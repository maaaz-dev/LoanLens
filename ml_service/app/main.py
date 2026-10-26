import json
import os
import sys
import types
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

# Ensure repo root and ml_service are in sys.path
BASE_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = BASE_DIR.parent

if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))
if str(BASE_DIR) not in sys.path:
    sys.path.insert(0, str(BASE_DIR))

# Ensure sys.modules['ml_service.app.pipeline'] resolves properly for joblib unpickling
try:
    import ml_service.app.pipeline as pipeline_module
except ImportError:
    import app.pipeline as pipeline_module

# Guarantee ml_service package hierarchy is registered in sys.modules
if "ml_service" not in sys.modules:
    ml_service_pkg = types.ModuleType("ml_service")
    ml_service_pkg.__path__ = [str(BASE_DIR)]
    sys.modules["ml_service"] = ml_service_pkg

if "ml_service.app" not in sys.modules:
    ml_service_app_pkg = types.ModuleType("ml_service.app")
    ml_service_app_pkg.__path__ = [str(BASE_DIR / "app")]
    sys.modules["ml_service.app"] = ml_service_app_pkg

sys.modules["ml_service.app.pipeline"] = pipeline_module

RAW_FEATURES = pipeline_module.RAW_FEATURES
CATEGORICAL_FEATURES = pipeline_module.CATEGORICAL_FEATURES

MODEL_PATH = BASE_DIR / "models" / "best_model.joblib"
METADATA_PATH = BASE_DIR / "models" / "model_metadata.json"

if not MODEL_PATH.exists():
    raise FileNotFoundError(f"Trained model not found: {MODEL_PATH}")

if not METADATA_PATH.exists():
    raise FileNotFoundError(f"Model metadata not found: {METADATA_PATH}")

# Load the trained model and its saved configuration.
model = joblib.load(MODEL_PATH)

with METADATA_PATH.open("r", encoding="utf-8") as file:
    metadata = json.load(file)

# Support optional environment variable override for threshold
env_threshold = os.environ.get("LOAN_ML_THRESHOLD")
if env_threshold is not None and env_threshold.strip():
    threshold = float(env_threshold.strip())
else:
    threshold = float(metadata["threshold"])

if not np.isfinite(threshold) or not 0.0 <= threshold <= 1.0:
    raise RuntimeError("Model decision threshold must be a finite float between 0.0 and 1.0.")

# The trainer encodes B as 1 and G as 0. Validate that contract before serving.
if metadata.get("positive_class") != "B" or metadata.get("negative_class") != "G":
    raise RuntimeError("Model metadata must map positive_class='B' and negative_class='G'.")

classifier = model.named_steps.get("model")
model_classes = list(getattr(classifier, "classes_", []))
positive_indices = [index for index, value in enumerate(model_classes) if value == 1]
if len(positive_indices) != 1:
    raise RuntimeError(
        "The loaded classifier must expose exactly one encoded positive class (1 for B)."
    )
positive_class_index = positive_indices[0]

# Read the category values the model actually learned.
preprocessor = (
    model.named_steps["preprocessing"]
    .named_steps["preprocess"]
)
category_encoder = preprocessor.named_transformers_["cat"]

allowed_categories = {
    column: tuple(sorted({str(value).strip() for value in values}))
    for column, values in zip(
        CATEGORICAL_FEATURES,
        category_encoder.categories_,
    )
}

# Accept harmless differences in whitespace/capitalization while still passing
# the exact category spelling learned by the model into the fitted pipeline.
category_lookup = {
    column: {value.casefold(): value for value in values}
    for column, values in allowed_categories.items()
}

# Canonical alias mapping for non-demographic categorical fields
CATEGORY_ALIASES = {
    "COMPENSATION_CHARGED": {
        "n": "N",
        "no": "N",
        "none": "N",
        "waived": "N",
        "0": "N",
        "false": "N",
        "y": "Y",
        "yes": "Y",
        "charged": "Y",
        "penalty charged": "Y",
        "1": "Y",
        "true": "Y",
    },
    "CLIENT_TYPE": {
        # Strictly geographic/branch classification from original RBI microfinance training data:
        "rural": "Rural",
        "semi-urban": "Semi-urban",
        "semiurban": "Semi-urban",
        "semi urban": "Semi-urban",
        "urban": "Urban",
    },
    "REPAY_MODE": {
        # Strictly repayment schedule mode from original training data:
        "n": "N",
        "normal": "N",
        "regular": "N",
        "i": "I",
        "installment": "I",
    },
}

app = FastAPI(title="LoanLens Class B Classification API (v2.0 - Non-Demographic)")


def _json_safe_validation_value(value):
    """Make validation details safe for strict JSON serialization."""
    if isinstance(value, (float, np.floating)) and not np.isfinite(value):
        return repr(float(value))
    if isinstance(value, dict):
        return {str(key): _json_safe_validation_value(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe_validation_value(item) for item in value]
    return value


@app.exception_handler(RequestValidationError)
async def request_validation_exception_handler(
    _request: Request,
    exc: RequestValidationError,
):
    errors = _json_safe_validation_value(exc.errors())
    return JSONResponse(
        status_code=422,
        content={"detail": jsonable_encoder(errors)},
    )


class LoanFeatures(BaseModel):
    # Seven active financial, delinquency, and geographic/schedule features.
    # Excluded demographic fields (INF_GENDER, INF_MARITAL_STATUS) are strictly forbidden.
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")

    INVESTMENT_TOTAL: float = Field(gt=0, allow_inf_nan=False)
    ACCCURRENTBALANCE: float = Field(allow_inf_nan=False)
    INSTALL_SIZE: float = Field(ge=0, allow_inf_nan=False)
    DUE_PAYMENT: float = Field(ge=0, allow_inf_nan=False)
    COMPENSATION_CHARGED: str = Field(min_length=1, max_length=30)
    CLIENT_TYPE: str = Field(min_length=1, max_length=30)
    REPAY_MODE: str = Field(min_length=1, max_length=30)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "loan-ml-api",
        "stage": "model-backed",
        "model_loaded": True,
        "model_version": metadata["model_version"],
        "selected_model": metadata["selected_model"],
        "input_features": RAW_FEATURES,
    }


@app.get("/model-info")
def model_info():
    return {
        "model_version": metadata["model_version"],
        "selected_model": metadata["selected_model"],
        "selection_metric": metadata["selection_metric"],
        "threshold": threshold,
        "threshold_metrics_on_validation": (
            metadata["threshold_metrics_on_validation"]
        ),
        "input_features": RAW_FEATURES,
        "training_rows": metadata["training_rows"],
        "validation_rows": metadata["validation_rows"],
    }


@app.post("/predict")
def predict(features: LoanFeatures):
    input_data = features.model_dump()

    # Prevent unknown categories from silently receiving an unintended encoded value
    for column in CATEGORICAL_FEATURES:
        received = input_data[column].strip()
        cf = received.casefold()

        # Check direct canonical lookup first
        canonical_value = category_lookup[column].get(cf)

        # Check alias dictionary if not direct match
        if canonical_value is None and column in CATEGORY_ALIASES:
            canonical_value = CATEGORY_ALIASES[column].get(cf)

        if canonical_value is None:
            raise HTTPException(
                status_code=422,
                detail={
                    "message": f"Unknown category for {column}.",
                    "received": received,
                    "allowed_values": list(allowed_categories[column]),
                },
            )

        # Use the exact training-time category representation
        input_data[column] = canonical_value

    # Keep the input columns in exactly the training order
    input_frame = pd.DataFrame(
        [[input_data[column] for column in RAW_FEATURES]],
        columns=RAW_FEATURES,
    )

    probability_b = float(
        model.predict_proba(input_frame)[0, positive_class_index]
    )

    if not np.isfinite(probability_b):
        raise HTTPException(
            status_code=500,
            detail="The model returned an invalid probability.",
        )

    predicted_class = "B" if probability_b >= threshold else "G"

    # Preserve the response fields used by the existing Java backend
    return {
        "predicted_class": predicted_class,
        "probability_b": probability_b,
        "threshold_used": threshold,
        "model_version": metadata["model_version"],
    }
