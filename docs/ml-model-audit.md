# LoanLens Model Audit — Active v2.0 Snapshot

**Scope:** Review of the checked-in model metadata, inference contract and previously recorded evaluation results. This document is an engineering caution, not a credit-risk certification.

## Active artifact

The current repository snapshot identifies the active artifact as a LightGBM preprocessing/classification pipeline, version v2.0.

| Item | Current metadata |
|---|---|
| Model file | ml_service/models/best_model.joblib |
| Selected model | LightGBM |
| Model version | v2.0 |
| Positive class | B |
| Negative class | G |
| Decision threshold | Approximately 0.42 |
| Training rows recorded in metadata | 37,408 |
| Validation rows recorded in metadata | 7,482 |
| Raw prediction features | Seven non-demographic fields |

The active API accepts INVESTMENT_TOTAL, ACCCURRENTBALANCE, INSTALL_SIZE, DUE_PAYMENT, COMPENSATION_CHARGED, CLIENT_TYPE and REPAY_MODE. It rejects extra fields, including INF_GENDER and INF_MARITAL_STATUS. Numeric validation allows INSTALL_SIZE to be zero and allows a negative account balance; the investment total must be positive and due payment must be non-negative.

Verify the live /health and /model-info endpoints before a demonstration. Metadata and active runtime configuration, not older presentation notes, define the model snapshot currently being used.

## Validation results recorded in the active metadata

The checked-in metadata reports approximately:

| Measure | Recorded validation result |
|---|---:|
| ROC-AUC | 0.7807 |
| Average precision / PR-AUC | 0.3668 |
| Precision at threshold ~0.42 | 20.53% |
| Recall at threshold ~0.42 | 80.02% |
| F1 at threshold ~0.42 | 0.3268 |
| Accuracy at threshold ~0.42 | 63.38% |

The threshold was selected using validation data. Therefore, the threshold-specific figures are selection/validation results and should not be presented as an independent unbiased estimate of performance on a new population.

## Previously recorded independent holdout results

The current integration guide records the following evaluation on a separate held-out file. The raw spreadsheet is not included in this source repository, so the numbers below are documented results and were not independently reproduced from this public checkout.

| Measure | Recorded result |
|---|---:|
| Labelled rows | 4,305 |
| Class G / Class B | 4,286 / 19 |
| ROC-AUC | 0.5282 |
| Average precision / PR-AUC | 0.0166 |
| Accuracy at threshold ~0.42 | 74.08% |
| Precision at threshold ~0.42 | 0.72% |
| Recall at threshold ~0.42 | 42.11% |
| F1 at threshold ~0.42 | 0.0141 |
| Confusion matrix TN / FP / FN / TP | 3,181 / 1,105 / 11 / 8 |

Only 19 Class B cases were recorded in this holdout. Precision/recall estimates for such a small positive group are uncertain, and the ROC-AUC is close to 0.5 for that evaluated population. High accuracy alone is not evidence of usefulness under severe class imbalance.

## Dataset and target caveats

- The target is named QUALITY_OF_LOAN, with labels G and B.
- Current evidence does not verify that B is a legal or financial default event.
- The cause of the train/holdout distribution differences is not established by this repository snapshot.
- The raw holdout and source data dictionary are absent from the public source tree, so the reported holdout results cannot be rerun from this repository alone.
- Historical model artifacts and older docs mention other algorithms/thresholds. Do not combine those historical results with current v2.0 LightGBM metadata.

## Required before any real-world use

1. Verify source data provenance, the data dictionary, target meaning and time/account grouping.
2. Establish why training and holdout populations differ; do not reshuffle the external holdout merely to improve metrics.
3. Evaluate the exact released artifact on representative, independent data using class-imbalance-aware measures and uncertainty intervals.
4. Review probability calibration and threshold selection separately from the final holdout.
5. Assess privacy, fairness, security, compliance and operational controls with domain experts.
6. Run the model using the pinned runtime dependencies and verify the response through the live API.

**Conclusion:** The current evidence supports an academic model-serving/integration demonstration only. Do not use this model to approve, reject, price or make adverse decisions for actual loans.
