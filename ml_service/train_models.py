from __future__ import annotations

import json
import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from sklearn.base import clone
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    brier_score_loss,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split

from xgboost import XGBClassifier
from lightgbm import LGBMClassifier

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent

if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

try:
    from ml_service.app.pipeline import RAW_FEATURES, build_model_pipeline
except ImportError:
    from app.pipeline import RAW_FEATURES, build_model_pipeline

DATA_PATH = SCRIPT_DIR / "data" / "train-FIN_ANA_DATA .xls"
MODEL_DIR = SCRIPT_DIR / "models"
REPORT_DIR = SCRIPT_DIR / "reports"

MODEL_DIR.mkdir(parents=True, exist_ok=True)
REPORT_DIR.mkdir(parents=True, exist_ok=True)


def make_models(scale_pos_weight):
    return {
        "Logistic Regression": LogisticRegression(
            C=0.1,
            class_weight="balanced",
            max_iter=1000,
            random_state=42,
        ),
        "Random Forest": RandomForestClassifier(
            n_estimators=300,
            max_depth=8,
            class_weight="balanced",
            min_samples_leaf=20,
            random_state=42,
            n_jobs=-1,
        ),
        "XGBoost": XGBClassifier(
            n_estimators=400,
            max_depth=5,
            learning_rate=0.05,
            subsample=0.8,
            colsample_bytree=0.8,
            scale_pos_weight=scale_pos_weight,
            eval_metric="auc",
            random_state=42,
            n_jobs=-1,
        ),
        "LightGBM": LGBMClassifier(
            n_estimators=400,
            max_depth=5,
            learning_rate=0.05,
            subsample=0.8,
            colsample_bytree=0.8,
            class_weight="balanced",
            random_state=42,
            n_jobs=-1,
            verbosity=-1,
        ),
    }


def evaluate(model, X, y):
    probabilities = model.predict_proba(X)[:, 1]
    predictions = (probabilities >= 0.5).astype(int)

    return {
        "ROC_AUC": float(roc_auc_score(y, probabilities)),
        "PR_AUC": float(average_precision_score(y, probabilities)),
        "Accuracy": float(accuracy_score(y, predictions)),
        "Precision": float(precision_score(y, predictions, zero_division=0)),
        "Recall": float(recall_score(y, predictions, zero_division=0)),
        "F1": float(f1_score(y, predictions, zero_division=0)),
        "Brier_Score": float(brier_score_loss(y, probabilities)),
    }, probabilities


def main():
    if not DATA_PATH.exists():
        raise FileNotFoundError(f"Training dataset not found: {DATA_PATH}")

    print("Loading raw training dataset from:", DATA_PATH)
    data = pd.read_excel(DATA_PATH, engine="xlrd")
    data = data[data["QUALITY_OF_LOAN"].isin(["G", "B"])].copy()

    print(f"Raw dataset shape: {data.shape}")
    print(f"Target distribution (QUALITY_OF_LOAN): {data['QUALITY_OF_LOAN'].value_counts().to_dict()}")

    # Active predictor features excluding demographic features (INF_GENDER, INF_MARITAL_STATUS)
    print(f"Active predictor features ({len(RAW_FEATURES)} features): {RAW_FEATURES}")
    X = data[RAW_FEATURES]
    y = (data["QUALITY_OF_LOAN"] == "B").astype(int)

    X_train, X_val, y_train, y_val = train_test_split(
        X,
        y,
        test_size=0.20,
        stratify=y,
        random_state=42,
    )

    train_weight = float((y_train == 0).sum() / (y_train == 1).sum())
    models = make_models(train_weight)

    print("Training rows:", len(X_train))
    print("Validation rows:", len(X_val))
    print("Training bad-loan class weight (scale_pos_weight):", round(train_weight, 3))
    print("\nTraining and evaluating candidate models...")

    validation_models = {}
    results = []
    probabilities_by_model = {}

    for name, estimator in models.items():
        print(f"\nTraining: {name} ...", flush=True)

        pipeline = build_model_pipeline(clone(estimator))
        pipeline.fit(X_train, y_train)

        metrics, probabilities = evaluate(pipeline, X_val, y_val)

        validation_models[name] = pipeline
        probabilities_by_model[name] = probabilities
        results.append({"Model": name, **metrics})

        print(
            f"  ROC-AUC={metrics['ROC_AUC']:.4f} | "
            f"PR-AUC={metrics['PR_AUC']:.4f} | "
            f"Accuracy={metrics['Accuracy']:.4f} | "
            f"Recall={metrics['Recall']:.4f} | "
            f"F1={metrics['F1']:.4f}"
        )

    report = pd.DataFrame(results).sort_values(
        "ROC_AUC", ascending=False
    ).reset_index(drop=True)

    report.insert(0, "Rank_by_ROC_AUC", range(1, len(report) + 1))
    report_path = REPORT_DIR / "model_comparison_validation.csv"
    report.to_csv(report_path, index=False)

    best_name = report.loc[0, "Model"]
    best_probabilities = probabilities_by_model[best_name]

    # Tune the chosen model's threshold using validation data only (80% target recall)
    thresholds = np.linspace(0.05, 0.95, 181)
    threshold_results = []

    for threshold in thresholds:
        predictions = (best_probabilities >= threshold).astype(int)
        recall = recall_score(y_val, predictions, zero_division=0)

        if recall >= 0.80:
            threshold_results.append({
                "threshold": float(threshold),
                "recall": float(recall),
                "precision": float(
                    precision_score(y_val, predictions, zero_division=0)
                ),
                "f1": float(f1_score(y_val, predictions, zero_division=0)),
            })

    if threshold_results:
        chosen_threshold = max(
            threshold_results, key=lambda item: item["f1"]
        )["threshold"]
    else:
        chosen_threshold = 0.30

    print("\n=== Validation Comparison Report (Threshold = 0.50) ===")
    print(report.to_string(index=False, float_format=lambda value: f"{value:.4f}"))
    print("\nChampion Model Selected by validation ROC-AUC:", best_name)
    print(f"Tuned Decision Threshold (for 80% default recall): {chosen_threshold:.4f}")

    tuned_predictions = (
        best_probabilities >= chosen_threshold
    ).astype(int)

    cm = confusion_matrix(y_val, tuned_predictions)
    tuned_metrics = {
        "threshold": float(chosen_threshold),
        "accuracy": float(accuracy_score(y_val, tuned_predictions)),
        "precision": float(
            precision_score(y_val, tuned_predictions, zero_division=0)
        ),
        "recall": float(
            recall_score(y_val, tuned_predictions, zero_division=0)
        ),
        "f1": float(f1_score(y_val, tuned_predictions, zero_division=0)),
        "roc_auc": float(roc_auc_score(y_val, best_probabilities)),
        "pr_auc": float(average_precision_score(y_val, best_probabilities)),
        "brier_score": float(brier_score_loss(y_val, best_probabilities)),
        "confusion_matrix": {
            "true_negatives_G": int(cm[0, 0]),
            "false_positives_B": int(cm[0, 1]),
            "false_negatives_G": int(cm[1, 0]),
            "true_positives_B": int(cm[1, 1]),
        },
    }

    print("\n=== Champion Model Performance at Tuned Threshold ===")
    for k, v in tuned_metrics.items():
        print(f"  {k}: {v}")

    # Refit every algorithm on all labelled training rows for deployment.
    full_weight = float((y == 0).sum() / (y == 1).sum())
    deployment_models = make_models(full_weight)

    for name, estimator in deployment_models.items():
        print(f"\nRefitting deployment model on all training rows: {name} ...", flush=True)

        full_pipeline = build_model_pipeline(clone(estimator))
        full_pipeline.fit(X, y)

        filename = name.lower().replace(" ", "_") + "_pipeline.joblib"
        joblib.dump(full_pipeline, MODEL_DIR / filename, compress=3)

        if name == best_name:
            joblib.dump(
                full_pipeline,
                MODEL_DIR / "best_model.joblib",
                compress=3,
            )

    metadata = {
        "model_version": "v2.0",
        "selected_model": best_name,
        "selection_metric": "validation_ROC_AUC",
        "positive_class": "B",
        "negative_class": "G",
        "threshold": float(chosen_threshold),
        "threshold_metrics_on_validation": tuned_metrics,
        "input_features": RAW_FEATURES,
        "numeric_features": [
            "INVESTMENT_TOTAL",
            "ACCCURRENTBALANCE",
            "INSTALL_SIZE",
            "DUE_PAYMENT",
        ],
        "categorical_features": [
            "COMPENSATION_CHARGED",
            "CLIENT_TYPE",
            "REPAY_MODE",
        ],
        "excluded_features": [
            "INF_GENDER",
            "INF_MARITAL_STATUS",
        ],
        "validation_metrics_at_0_5": results,
        "training_rows": int(len(X)),
        "validation_rows": int(len(X_val)),
        "report_file": "model_comparison_validation.csv",
    }

    with (MODEL_DIR / "model_metadata.json").open(
        "w", encoding="utf-8"
    ) as handle:
        json.dump(metadata, handle, indent=2)

    print("\nRetraining complete!")
    print(f"Saved active model: {MODEL_DIR / 'best_model.joblib'}")
    print(f"Saved metadata: {MODEL_DIR / 'model_metadata.json'}")


if __name__ == "__main__":
    main()
