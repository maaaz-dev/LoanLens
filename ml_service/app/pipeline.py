from __future__ import annotations

import numpy as np
import pandas as pd

from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OrdinalEncoder, PowerTransformer


RAW_FEATURES = [
    "INVESTMENT_TOTAL",
    "ACCCURRENTBALANCE",
    "INSTALL_SIZE",
    "DUE_PAYMENT",
    "COMPENSATION_CHARGED",
    "CLIENT_TYPE",
    "REPAY_MODE",
]

NUMERIC_RAW = [
    "INVESTMENT_TOTAL",
    "ACCCURRENTBALANCE",
    "INSTALL_SIZE",
    "DUE_PAYMENT",
]

CATEGORICAL_FEATURES = [
    "COMPENSATION_CHARGED",
    "CLIENT_TYPE",
    "REPAY_MODE",
]

ENGINEERED_NUMERIC = [
    "REPAYMENT_PROGRESS",
    "IS_ZERO_BALANCE",
    "DUE_BURDEN",
    "HAS_DUE",
    "COMPENSATION_FLAG",
    "ZERO_INSTALL_WITH_DUE",
    "IS_RURAL",
    "IS_URBAN",
    "DUE_X_COMPENSATION",
    "RURAL_X_DUE",
    "PROGRESS_X_DUE",
    "BALANCE_TO_DUE",
    "INSTALL_TO_INVESTMENT",
    "TOTAL_STRESS",
    "LOG_INVESTMENT_TOTAL",
    "LOG_ACCCURRENTBALANCE",
    "LOG_INSTALL_SIZE",
    "LOG_DUE_PAYMENT",
]

NUMERIC_FEATURES = NUMERIC_RAW + ENGINEERED_NUMERIC


class LoanRiskFeatureEngineer(TransformerMixin, BaseEstimator):
    """Clean raw input and create the engineered domain features without demographic attributes."""

    def _select_raw(self, X):
        if not isinstance(X, pd.DataFrame):
            raise TypeError("Input must be a pandas DataFrame.")

        missing = [c for c in RAW_FEATURES if c not in X.columns]
        if missing:
            raise ValueError(f"Missing input features: {missing}")

        data = X.loc[:, RAW_FEATURES].copy()
        data["CLIENT_TYPE"] = data["CLIENT_TYPE"].replace(
            {0: np.nan, "0": np.nan}
        )

        for col in CATEGORICAL_FEATURES:
            data[col] = (
                data[col].astype("string").str.strip().replace("", pd.NA)
            )

        return data

    def fit(self, X, y=None):
        # Learn missing-value replacements from training data only.
        data = self._select_raw(X)

        self.numeric_medians_ = {}
        for col in NUMERIC_RAW:
            values = pd.to_numeric(data[col], errors="coerce")
            median = values.median()
            self.numeric_medians_[col] = (
                float(median) if pd.notna(median) else 0.0
            )

        self.category_modes_ = {}
        for col in CATEGORICAL_FEATURES:
            values = data[col].dropna()
            modes = values.mode()
            self.category_modes_[col] = (
                str(modes.iloc[0]) if not modes.empty else "UNKNOWN"
            )

        return self

    def transform(self, X):
        if not hasattr(self, "numeric_medians_"):
            raise RuntimeError("Fit the feature engineer before transform.")

        data = self._select_raw(X)

        # Fill missing numeric and categorical values.
        for col in NUMERIC_RAW:
            data[col] = pd.to_numeric(data[col], errors="coerce")
            data[col] = data[col].fillna(self.numeric_medians_[col])

        for col in CATEGORICAL_FEATURES:
            data[col] = data[col].fillna(self.category_modes_[col]).astype(str)

        # Repayment health features.
        data["REPAYMENT_PROGRESS"] = 1 - (
            data["ACCCURRENTBALANCE"] / (data["INVESTMENT_TOTAL"] + 1)
        )
        data["IS_ZERO_BALANCE"] = (
            data["ACCCURRENTBALANCE"] == 0
        ).astype(int)
        data["DUE_BURDEN"] = (
            data["DUE_PAYMENT"] / (data["INVESTMENT_TOTAL"] + 1)
        )

        # Risk flags.
        data["HAS_DUE"] = (data["DUE_PAYMENT"] > 0).astype(int)
        data["COMPENSATION_FLAG"] = (
            data["COMPENSATION_CHARGED"] == "Y"
        ).astype(int)
        data["ZERO_INSTALL_WITH_DUE"] = (
            (data["INSTALL_SIZE"] == 0) & (data["DUE_PAYMENT"] > 0)
        ).astype(int)

        # Client profile (geographic branch location).
        data["IS_RURAL"] = (data["CLIENT_TYPE"] == "Rural").astype(int)
        data["IS_URBAN"] = (data["CLIENT_TYPE"] == "Urban").astype(int)

        # Interaction features.
        data["DUE_X_COMPENSATION"] = (
            data["HAS_DUE"] * data["COMPENSATION_FLAG"]
        )
        data["RURAL_X_DUE"] = data["IS_RURAL"] * data["HAS_DUE"]
        data["PROGRESS_X_DUE"] = (
            data["REPAYMENT_PROGRESS"] * data["DUE_BURDEN"]
        )

        # Ratios.
        data["BALANCE_TO_DUE"] = (
            data["ACCCURRENTBALANCE"] / (data["DUE_PAYMENT"] + 1)
        )
        data["INSTALL_TO_INVESTMENT"] = (
            data["INSTALL_SIZE"] / (data["INVESTMENT_TOTAL"] + 1)
        )
        data["TOTAL_STRESS"] = (
            data["DUE_BURDEN"]
            + data["COMPENSATION_FLAG"]
            + data["HAS_DUE"]
        )

        # Log transforms.
        for col in NUMERIC_RAW:
            data[f"LOG_{col}"] = np.log1p(data[col].clip(lower=0))

        result = data[NUMERIC_FEATURES + CATEGORICAL_FEATURES].copy()

        if not np.isfinite(result[NUMERIC_FEATURES].to_numpy(dtype=float)).all():
            raise ValueError("Engineered features contain invalid numbers.")

        return result


class QuantileClipper(TransformerMixin, BaseEstimator):
    """Learn numeric clipping limits from training data."""

    def fit(self, X, y=None):
        self.lower_bounds_ = {
            c: float(X[c].quantile(0.01)) for c in NUMERIC_FEATURES
        }
        self.upper_bounds_ = {
            c: float(X[c].quantile(0.99)) for c in NUMERIC_FEATURES
        }
        return self

    def transform(self, X):
        if not hasattr(self, "lower_bounds_"):
            raise RuntimeError("Fit the clipper before transform.")

        result = X.copy()
        for col in NUMERIC_FEATURES:
            result[col] = result[col].clip(
                self.lower_bounds_[col], self.upper_bounds_[col]
            )
        return result


def build_preprocessor():
    return ColumnTransformer([
        (
            "num",
            PowerTransformer(method="yeo-johnson", standardize=True),
            NUMERIC_FEATURES,
        ),
        (
            "cat",
            OrdinalEncoder(
                handle_unknown="use_encoded_value",
                unknown_value=-1,
            ),
            CATEGORICAL_FEATURES,
        ),
    ])


def build_preprocessing_pipeline():
    return Pipeline([
        ("features", LoanRiskFeatureEngineer()),
        ("clip", QuantileClipper()),
        ("preprocess", build_preprocessor()),
    ])


def build_model_pipeline(model):
    return Pipeline([
        ("preprocessing", build_preprocessing_pipeline()),
        ("model", model),
    ])
