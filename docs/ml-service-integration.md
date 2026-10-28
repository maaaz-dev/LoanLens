# LoanLens — Python Machine Learning Service Integration Guide (v2.0)

## 1. Executive Summary & Architecture Overview

The LoanLens academic prototype integrates a retrained machine-learning pipeline into its Java/Jakarta Servlet application.

As of **v2.0**, the active prediction pipeline and user interface use a seven-feature contract; `INF_GENDER` and `INF_MARITAL_STATUS` are excluded. The model artifact and metadata are checked into the repository, while local training/test data are not included in the public source bundle.

> [!WARNING]
> **Academic prototype only — do not use for real lending decisions.** `QUALITY_OF_LOAN` classes `G` and `B` are dataset labels. The available project documentation does not verify that class `B` means a legal or financial default event. Validation results are not a guarantee of performance on new applicants.

The complete end-to-end execution path operates as follows:

```
[Browser UI: workspace.jsp / app.js]
               │  HTTP POST (JSON, 7 features)
               ▼
[Java Web Controller: LoanApplicationServlet]
               │  DTO & Model Validation (7 active features)
               ▼
[Domain Service: LoanApplicationService]
               │  Inference Request
               ▼
[ML Provider Abstraction: PredictionProvider Interface]
               │  (Active when LOAN_ML_MODE=http: HttpPredictionProvider)
               ▼
[HTTP Client: java.net.http.HttpClient]
               │  HTTP POST /predict (JSON, 7 features)
               ▼
[Python FastAPI Inference Service: ml_service/app/main.py]
               │  Pydantic Validation (extra='forbid') & Categorical Normalization
               ▼
[Scikit-Learn Preprocessing Pipeline: LoanRiskFeatureEngineer + QuantileClipper]
               │  Transformed Numerical & Encoded Feature Vectors (25 columns)
               ▼
[Trained Champion Classifier (LightGBM): best_model.joblib]
               │  predict_proba -> P(Class = 'B')
               ▼
[Threshold Decision Engine: model_metadata.json (Threshold 0.4200)]
               │  P(B) >= 0.4200 ? 'B' : 'G' (dataset labels; not verified default outcomes)
               ▼
[JSON Response: {predicted_class, probability_b, threshold_used, model_version}]
               │  HTTP Response 200 OK
               ▼
[Java Response Parsing: JsonUtil]
               │  PredictionRecord Instantiation (simulated = false)
               ▼
[Dual Persistence Layer: MemoryRepository / OracleRepository]
               │  Associated Application & Prediction Record Stored
               ▼
[Java Controller Response: LoanApplicationServlet]
               │  HTTP 201 Created (JSON)
               ▼
[Browser UI: Safe DOM Result Card Rendering in workspace.jsp]
```

---

## 2. ML Service File Organization

The Python service assets are located in `ml_service/` at the repository root:

| Path | Description |
| :--- | :--- |
| `ml_service/__init__.py` | Package marker for clean module imports. |
| `ml_service/app/pipeline.py` | Custom transformers (`LoanRiskFeatureEngineer`, `QuantileClipper`), 7-feature raw feature list, and pipeline definition. |
| `ml_service/app/main.py` | FastAPI application serving `/health`, `/model-info`, and `/predict`. Enforces 7-feature contract with `extra="forbid"`, rejecting demographic fields with HTTP 422. |
| `ml_service/models/best_model.joblib` | Serialized champion model (LightGBM pipeline v2.0 without demographic inputs). |
| `ml_service/models/model_metadata.json` | Model metadata, threshold configuration (`0.4200`), validation metrics, and version tag (`v2.0`). |
| `ml_service/models/legacy_v1_best_model.joblib` | Archived v1.0 9-feature model retained for historical audit/rollback. |
| `ml_service/models/legacy_v1_model_metadata.json` | Archived v1.0 metadata. |
| `ml_service/reports/model_comparison_validation.csv` | Empirical validation benchmark metrics for evaluated model families on the 7-feature contract. |
| `ml_service/data/train-FIN_ANA_DATA .xls` | Original training dataset containing 37,408 records and raw features (kept read-only and unmodified). |
| `ml_service/data/test-FIN_ANA_DATA .xls` | Original test dataset containing 4,310 records. |
| `ml_service/tests/test_prediction_api.py` | Unit and regression test suite covering endpoints, schema validation, and boundary conditions. |
| `ml_service/tests/test_cross_language_contract.py` | Cross-language contract verification suite verifying UI compatibility, semantic mappings, demographic rejection, and error codes. |
| `ml_service/requirements.txt` | Python dependency specifications for full training and evaluation. |
| `ml_service/requirements-runtime.txt` | Minimal runtime dependency specifications for inference serving. |
| `ml_service/run_server.py` | Python entry point to run Uvicorn on `http://127.0.0.1:8000`. |
| `ml_service/start_service.ps1` | PowerShell runner script for starting the inference service. |
| `ml_service/start_service.bat` | Windows batch launcher for launching the service. |

---

## 3. Active Cross-Language API Contract (v2.0)

### Request: `POST /predict`

Header: `Content-Type: application/json`

```json
{
  "INVESTMENT_TOTAL": 50000.00,
  "ACCCURRENTBALANCE": 28500.00,
  "INSTALL_SIZE": 1200.00,
  "DUE_PAYMENT": 0.00,
  "COMPENSATION_CHARGED": "None",
  "CLIENT_TYPE": "Urban",
  "REPAY_MODE": "Normal"
}
```

#### Feature Validation Constraints
1. `INVESTMENT_TOTAL` (`float`): Must be strictly positive (`gt=0`), finite.
2. `ACCCURRENTBALANCE` (`float`): Finite number; may be negative (representing overdraft accounts).
3. `INSTALL_SIZE` (`float`): Non-negative finite number (`ge=0` in Python schema; Java requires `gt=0` for new loan applications).
4. `DUE_PAYMENT` (`float`): Non-negative finite number (`ge=0`).
5. `COMPENSATION_CHARGED` (`str`): Canonical codes (`N`, `Y`) or genuine semantic synonyms (`None`, `No`, `Waived`, `Charged`, `Penalty Charged`, `Yes`).
6. `CLIENT_TYPE` (`str`): Geographic branch area category: `Rural`, `Semi-urban`, `Urban` (case-insensitive).
7. `REPAY_MODE` (`str`): Repayment schedule classification: `N` / `Normal` (standard amortizing schedule), `I` / `Installment` (installment schedule).

*Note: Any unexpected or demographic keys (`INF_GENDER`, `INF_MARITAL_STATUS`) are strictly rejected with HTTP 422 Unprocessable Entity (`extra="forbid"`).*

### Response: HTTP 200 OK

Header: `Content-Type: application/json`

```json
{
  "predicted_class": "G",
  "probability_b": 0.01698478347345711,
  "threshold_used": 0.4200,
  "model_version": "v2.0"
}
```

- `predicted_class`: Strictly `'G'` (Good / Low Default Risk) or `'B'` (Bad / Elevated Default Risk).
- `probability_b`: Model estimated probability that applicant defaults ($P(\text{Class} = \text{'B'} \mid X)$).
- `threshold_used`: Tuned decision threshold applied ($0.4200$).
- `model_version`: Serialized model release identifier (`v2.0`).

---

## 4. Feature Audit & Demographic Removal Summary

| Feature | Category | Dataset Meaning | Action in v2.0 | Rationale & Architectural Handling |
| :--- | :--- | :--- | :--- | :--- |
| `INF_GENDER` | Demographic | Applicant Gender | **REMOVED** | Non-predictive personal attribute; creates fairness and compliance concerns. Removed from UI, Java validation, HTTP serialization, and retrained pipeline. Oracle column made nullable. |
| `INF_MARITAL_STATUS` | Demographic | Applicant Marital Status | **REMOVED** | Irrelevant personal demographic; creates unnecessary privacy concerns. Removed from active UI and prediction pipeline. Retrained model shows no degradation in ROC-AUC without it. |
| `INVESTMENT_TOTAL` | Financial | Requested loan amount | **RETAINED** | Core credit risk parameter. Validated positive numeric. |
| `ACCCURRENTBALANCE` | Financial | Current deposit balance | **RETAINED** | Financial liquidity indicator. Permitted negative (overdraft). |
| `INSTALL_SIZE` | Repayment | Installment installment size | **RETAINED** | Payment obligation sizing. Validated positive numeric. |
| `DUE_PAYMENT` | Delinquency | Past-due unpaid balance | **RETAINED** | Highest importance feature indicating delinquency. |
| `COMPENSATION_CHARGED`| Operational | Penalty charges assessed | **RETAINED** | Indicator of historical payment friction. Mapped to canonical `N` / `Y`. |
| `CLIENT_TYPE` | Geographic | Branch area category | **RETAINED & CORRECTED** | Corrected in UI as "Branch Area Category" with valid options `Rural`, `Semi-urban`, `Urban`. Misleading entity types (`Individual`, `Corporate`) removed. |
| `REPAY_MODE` | Operational | Repayment schedule mode | **RETAINED & CORRECTED** | Corrected in UI as "Repayment Schedule Mode" with valid options `Normal`, `Installment`. Misleading frequency terms (`Monthly`, `Bullet`) removed. |

---

## 5. Machine Learning Pipeline Mechanics & Feature Dimensions (v2.0)

### Dimension Progression Across Pipeline Stages
- **Stage 1 (Raw Application Input)**: **7 features** (`INVESTMENT_TOTAL`, `ACCCURRENTBALANCE`, `INSTALL_SIZE`, `DUE_PAYMENT`, `COMPENSATION_CHARGED`, `CLIENT_TYPE`, `REPAY_MODE`).
- **Stage 2 (Feature Engineering — `LoanRiskFeatureEngineer`)**: Generates **18 engineered numeric features**:
  - Repayment Progress: $1 - \frac{\text{ACCCURRENTBALANCE}}{\text{INVESTMENT\_TOTAL} + 1.0}$
  - Balance & Burden: `IS_ZERO_BALANCE`, `DUE_BURDEN` ($\frac{\text{DUE\_PAYMENT}}{\text{INVESTMENT\_TOTAL} + 1.0}$)
  - Status & Delinquency Flags: `HAS_DUE`, `COMPENSATION_FLAG`, `ZERO_INSTALL_WITH_DUE`
  - Location Flags: `IS_RURAL`, `IS_URBAN`
  - Interaction Terms: `DUE_X_COMPENSATION`, `RURAL_X_DUE`, `PROGRESS_X_DUE`
  - Ratios: `BALANCE_TO_DUE`, `INSTALL_TO_INVESTMENT`, `TOTAL_STRESS`
  - Log Transforms: `LOG_INVESTMENT_TOTAL`, `LOG_ACCCURRENTBALANCE`, `LOG_INSTALL_SIZE`, `LOG_DUE_PAYMENT`
- **Stage 3 (Columns entering `ColumnTransformer`)**: **25 columns** (4 raw numeric + 18 engineered numeric = 22 numeric, plus 3 categoricals).
- **Stage 4 (Preprocessor Encoding — `build_preprocessor`)**:
  - 22 numeric features processed via `QuantileClipper` (1st to 99th percentiles) and `PowerTransformer(method="yeo-johnson", standardize=True)` $\rightarrow$ 22 normalized columns.
  - 3 categorical features encoded via `OrdinalEncoder(handle_unknown="use_encoded_value", unknown_value=-1)` $\rightarrow$ 3 integer-encoded columns.
  - Total columns output by `ColumnTransformer`: **25 encoded columns**.
- **Stage 5 (Classifier Input)**: LightGBM estimator consumes precisely **25 features** (`n_features_in_ = 25`).

### Model Evaluation Benchmarks

#### 1. Stratified Validation Benchmark (`7,482` held-out rows from `train-FIN_ANA_DATA .xls`):
- Total Validation Rows: `7,482` (Class G: `6,651`, Class B: `831`)
- ROC-AUC: `0.7807`
- PR-AUC: `0.3668`
- Decision threshold selected on validation split: `0.4200` (chosen for a target recall on Class B validation examples)
- Metrics at Threshold `0.4200`:
  - Recall (Class B): `80.02%` (665 / 831 Class B validation examples identified)
  - Precision: `20.53%`
  - F1-Score: `0.3268`
  - Overall Accuracy: `63.38%`
  - Confusion Matrix: TN: `4,077`, FP: `2,574`, FN: `166`, TP: `665`

#### 2. Independent Held-Out Test Evaluation (`test-FIN_ANA_DATA .xls`):
- Total Binary Samples: `4,305` (Class G: `4,286`, Class B: `19`)
- Provenance Note: Never used during training, cross-validation, model selection, or threshold tuning.
- Extreme Imbalance: $99.56\%$ Class G vs $0.44\%$ Class B (only 19 Class B records).
- Metrics at Frozen Threshold `0.4200`:
  - ROC-AUC: `0.5282`
  - PR-AUC: `0.0166`
  - Accuracy: `74.08%`
  - Recall: `42.11%` (8 / 19 Class B records identified)
  - Precision: `0.72%`
  - F1-Score: `0.0141`
  - Confusion Matrix: TN: `3,181`, FP: `1,105`, FN: `11`, TP: `8`

---

## 6. Frontend Synthetic Demo Presets (Verified in Live ML Mode)

Both sample buttons in `workspace.jsp` / `app.js` have been calibrated against the live v2.0 model:

### Synthetic Example 1 (predicted Class G in the recorded demo)
- **Inputs**: `INVESTMENT_TOTAL`: 50,000.00, `ACCCURRENTBALANCE`: 28,500.00, `INSTALL_SIZE`: 1,200.00, `DUE_PAYMENT`: 0.00, `COMPENSATION_CHARGED`: `None`, `CLIENT_TYPE`: `Urban`, `REPAY_MODE`: `Normal`.
- **Live Model Response**:
  - Predicted Class: `G`
  - Probability B: `1.70%` (`0.016985`)
  - Threshold: `0.4200`
  - Model Version: `v2.0`

### Synthetic Example 2 (predicted Class B in the recorded demo)
- **Inputs**: `INVESTMENT_TOTAL`: 50,000.00, `ACCCURRENTBALANCE`: 0.00, `INSTALL_SIZE`: 1,500.00, `DUE_PAYMENT`: 25,000.00, `COMPENSATION_CHARGED`: `Penalty Charged`, `CLIENT_TYPE`: `Rural`, `REPAY_MODE`: `Installment`.
- **Live Model Response**:
  - Predicted Class: `B`
  - Probability B: `53.27%` (`0.532745`)
  - Threshold: `0.4200`
  - Model Version: `v2.0`

---

## 7. Windows Runbook & Service Control

### Step 1: Start the Python ML Service

```powershell
cd .
.\ml_service\start_service.ps1
```

Or run via dedicated venv:

```powershell
cd "E:\College Projects\Loan-Default-Risk-Prediction-copy"
& ".\ml_service\.venv\Scripts\python.exe" ml_service/run_server.py
```

The service will start on `http://127.0.0.1:8000`.

### Step 2: Configure LoanLens to Use Python ML Service

LoanLens defaults to `LOAN_ML_MODE=mock` for zero-dependency standalone operation. To connect to the live Python service, set the following environment variables:

```powershell
$env:LOAN_ML_MODE = "http"
$env:LOAN_ML_BASE_URL = "http://127.0.0.1:8000"
```

Or in Apache Tomcat `bin/setenv.bat`:

```cmd
set "LOAN_ML_MODE=http"
set "LOAN_ML_BASE_URL=http://127.0.0.1:8000"
```

### Step 3: Run Automated Test Suites

**Run Python Tests (22 tests):**

```powershell
cd "E:\College Projects\Loan-Default-Risk-Prediction-copy"
& ".\ml_service\.venv\Scripts\python.exe" -m unittest discover -s ml_service/tests -v
```

**Run Java Backend Tests & Build WAR:**

```powershell
& "C:\Tools\apache-ant-1.10.18\bin\ant.bat" -f build.xml `
  "-Dj2ee.server.home=C:\Tools" `
  "-Dj2ee.platform.classpath=C:\Program Files\Apache NetBeans\enterprise\modules\ext\servlet-jsp-api-6.1_3.0.jar" `
  "-Dlibs.CopyLibs.classpath=C:\Program Files\Apache NetBeans\java\ant\extra\org-netbeans-modules-java-j2seproject-copylibstask.jar" `
  test
```

---

## 8. Database Backward Compatibility

1. **Nullable Legacy Columns**: `INF_MARITAL_STATUS` and `INF_GENDER` in `database/schema.sql` are defined as `NULL` (nullable).
2. **Migration for Existing Oracle Databases**:
   ```sql
   ALTER TABLE LOAN_APPLICATION MODIFY (INF_MARITAL_STATUS NULL);
   ALTER TABLE LOAN_APPLICATION MODIFY (INF_GENDER NULL);
   ```
3. **Data Preservation**: Historical rows and columns remain intact; new submissions omit demographic inputs without integrity violations.
