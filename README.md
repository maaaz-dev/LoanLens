<div align="center">

# LoanLens
### Loan Default Risk Prediction — Academic Project

**A Java web application with a separate Python machine-learning inference service.**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://www.oracle.com/java/)
[![Python](https://img.shields.io/badge/Python-3.x-blue?logo=python)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/API-FastAPI-009688?logo=fastapi)](https://fastapi.tiangolo.com/)
[![Model](https://img.shields.io/badge/Model-LightGBM-v2.0-2b7a3d)](https://lightgbm.readthedocs.io/)
[![Status](https://img.shields.io/badge/Status-Academic%20Prototype-6f42c1)](#limitations-and-responsible-use)

</div>

> [!WARNING]
> **For learning and demonstration only — not for real lending decisions.** The active model predicts dataset labels **G** and **B** for the target **QUALITY_OF_LOAN**; label B has not been independently verified as a legal or financial default event. Published validation figures are not a guarantee of performance on new applicants. Do not use this prototype to approve or reject real loans.

## Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Active model input contract](#active-model-input-contract)
- [Project structure](#project-structure)
- [Run locally on Windows](#run-locally-on-windows)
- [Configuration](#configuration)
- [API endpoints](#api-endpoints)
- [Build and tests](#build-and-tests)
- [Model notes](#model-notes)
- [Limitations and responsible use](#limitations-and-responsible-use)
- [Documentation](#documentation)
- [Future work](#future-work)
- [License](#license)

## Overview

**LoanLens** is an academic full-stack project demonstrating how a web application can validate loan-related inputs, record an application, call a separate model-serving API, and present a structured prediction result.

The Java application handles the web workflow, validation, storage abstraction and prediction-provider selection. When configured for HTTP mode, it sends the active feature contract to a Python FastAPI service, which loads a saved preprocessing/model pipeline and returns the predicted class, Class B probability, threshold and model version.

The project demonstrates **Java + Python integration, API design, input validation, storage abstraction, automated tests and model-serving concepts**. It is not a production underwriting system.

## Features

- Responsive browser interface built with JSP, HTML, CSS and vanilla JavaScript.
- Java 21 backend using Jakarta Servlet APIs and Apache Tomcat 10.1.
- Input validation and structured API errors.
- Separate prediction providers:
  - **Mock mode:** simulated output for development; not an actual model inference.
  - **HTTP mode:** calls the Python model API.
- Python FastAPI endpoints for health, model information and prediction.
- Saved preprocessing and LightGBM pipeline with engineered features.
- In-memory and Oracle repository implementations.
- Apache Ant build/test workflow and Python unit tests.
- Architecture, integration, model-training and project walkthrough documentation.

## Architecture

Browser (JSP/JavaScript) → Java Servlet → Application Service and Validation → Repository and Prediction Provider → Python FastAPI /predict (HTTP mode) → Saved preprocessing and LightGBM pipeline → result returned to the UI.

~~~mermaid
flowchart TD
    A[User / Browser] --> B[JSP + CSS + JavaScript]
    B --> C[Java Servlet API]
    C --> D[Validation and Application Service]
    D --> E[Application / Prediction Repositories]
    E --> F[In-memory storage]
    E --> G[Oracle storage option]
    D --> H{Prediction mode}
    H -->|mock| I[Simulated result]
    H -->|http| J[Python FastAPI /predict]
    J --> K[Saved preprocessing + LightGBM model]
    K --> J
    I --> D
    J --> D
    D --> L[Prediction result and status]
    L --> B
~~~

The Java web application and Python service are separate processes. In HTTP mode, the Python API must be running and reachable. In mock mode, predictions are simulated and must be described as such.

## Technology stack

| Component | Technology | Purpose |
|---|---|---|
| Frontend | JSP, HTML, CSS, vanilla JavaScript | Input form and result display |
| Web backend | Java 21, Jakarta Servlets | Request handling, validation and workflow |
| Web server | Apache Tomcat 10.1.x | Runs the Java web application |
| Build | Apache Ant / NetBeans project build | Compilation, checks and WAR packaging |
| ML API | Python, FastAPI, Uvicorn | Serves model inference over HTTP |
| Active model artifact | LightGBM pipeline, metadata version v2.0 | Model prediction |
| ML preprocessing | scikit-learn, pandas, NumPy, joblib | Transformations and model loading |
| Storage | In-memory; Oracle repository/schema option | Application and prediction records |
| Tests | Java test runner, Python unittest | Configuration, API contract and workflow checks |

## Active model input contract

The **v2.0 Python inference API accepts seven non-demographic features**. The API rejects extra fields, including INF_GENDER and INF_MARITAL_STATUS.

| Feature | Description |
|---|---|
| INVESTMENT_TOTAL | Requested investment/loan amount; must be greater than zero. |
| ACCCURRENTBALANCE | Current account balance; a negative value can represent an overdraft and is allowed by the current contract. |
| INSTALL_SIZE | Installment size; zero is allowed. |
| DUE_PAYMENT | Amount currently due; must be non-negative. |
| COMPENSATION_CHARGED | Categorical compensation/penalty indicator. |
| CLIENT_TYPE | Client/area category represented in the training data. |
| REPAY_MODE | Repayment mode/category represented in the training data. |

The current metadata identifies B as the positive class and G as the negative class. The domain meaning of those labels must be confirmed from a reliable data dictionary before interpreting B as real-world default.

## Project structure

~~~text
.
├── database/
│   └── schema.sql
├── docs/
│   ├── LOANLENS_COMPLETE_ARCHITECTURE_AND_PIPELINE_GUIDE.md
│   ├── ml-integration-handoff.md
│   ├── ml-service-integration.md
│   └── project-walkthrough.md
├── ml_service/
│   ├── app/
│   │   ├── main.py
│   │   └── pipeline.py
│   ├── models/
│   │   ├── best_model.joblib
│   │   ├── model_metadata.json
│   │   └── legacy / comparison model artifacts
│   ├── reports/
│   ├── tests/
│   ├── requirements-runtime.txt
│   ├── requirements.txt
│   ├── run_server.py
│   ├── start_service.ps1
│   └── start_service.bat
├── nbproject/
├── src/                 # Java application source
├── test/                # Java tests
├── web/                 # JSP pages, styles, scripts and descriptors
├── .gitignore
├── build.xml
└── README.md
~~~

Generated build output, local virtual environments and local-only configuration are not required in source control unless a release process deliberately manages a specific artifact separately.

## Run locally on Windows

Use PowerShell. Open the repository root (the folder containing build.xml).

### 1. Create the Python environment

Use a Python version compatible with the pinned dependencies in ml_service/requirements-runtime.txt. Example with Python 3.14, if available:

~~~powershell
py -3.14 -m venv .\ml_service\.venv
& ".\ml_service\.venv\Scripts\python.exe" -m pip install --upgrade pip
& ".\ml_service\.venv\Scripts\python.exe" -m pip install -r ".\ml_service\requirements-runtime.txt"
~~~

The runtime requirements are the minimal service environment. ml_service/requirements.txt also contains packages used by training and experimentation.

### 2. Run the Python tests

~~~powershell
& ".\ml_service\.venv\Scripts\python.exe" -m unittest discover -s ".\ml_service\tests" -v
~~~

### 3. Start the Python API

~~~powershell
.\ml_service\start_service.ps1
~~~

Leave that terminal running. In another PowerShell window, check health and active model metadata:

~~~powershell
Invoke-RestMethod "http://127.0.0.1:8000/health" |
  ConvertTo-Json -Depth 5

Invoke-RestMethod "http://127.0.0.1:8000/model-info" |
  ConvertTo-Json -Depth 8
~~~

The committed metadata for the current v2.0 snapshot names LightGBM as the selected model and sets the threshold to approximately 0.42. The live /model-info response is the authority for the runtime currently running on your machine.

### 4. Build and verify the Java application

Install JDK 21, Apache Tomcat 10.1.x and Apache Ant / NetBeans build support. From the project root, run:

~~~powershell
ant test
ant verify
~~~

If ant is not recognised, run the Ant executable bundled with your NetBeans installation, or configure the Ant path and CopyLibs classpath required by build.xml. Follow the first build error rather than deleting generated directories blindly.

### 5. Open the application

Deploy the generated WAR using your configured Tomcat setup. The URL context path depends on the deployed WAR filename. For example, a WAR named LoanLens.war normally uses:

~~~text
http://localhost:8080/LoanLens/
~~~

Avoid deploying multiple differently named copies of the same app while testing, because it is easy to inspect the wrong context.

## Configuration

The Java application supports these configuration choices; exact precedence and required settings are implemented in src/java/com/loanrisk/config/.

| Setting | Supported values | Purpose |
|---|---|---|
| LOAN_STORAGE_MODE | memory, oracle | Selects storage implementation. |
| LOAN_ML_MODE | mock, http | Selects simulated output or the Python API provider. |
| LOAN_ML_BASE_URL | URL, e.g. http://127.0.0.1:8000 | Python service base address when HTTP mode is enabled. |
| LOAN_ML_CONNECT_TIMEOUT_MS | Positive integer | HTTP connection timeout. |
| LOAN_ML_REQUEST_TIMEOUT_MS | Positive integer | HTTP response timeout. |
| LOAN_DB_URL | JDBC URL | Oracle connection URL in Oracle mode. |
| LOAN_DB_USER / LOAN_DB_PASSWORD | Local credentials | Oracle connection identity. Never commit real credentials. |
| LOAN_ML_THRESHOLD | Number from 0 to 1 | Optional threshold override supported by the Python service. |

**Mock vs HTTP**
- mock mode returns simulated output. The result must not be called a live ML prediction.
- http mode calls POST /predict on the running Python service. Verify the active provider and health response before describing a result as model-backed.

**Storage**
- memory is useful for development, but records are lost when the application process restarts.
- The Oracle repository/schema option requires a configured Oracle database and compatible JDBC driver. Presence of schema/code is not proof that a live persistent Oracle deployment has been verified.

## API endpoints

### Java application

| Method | Path | Purpose |
|---|---|---|
| GET | /api/health | Reports Java application/runtime and configured provider/storage status. |
| POST | /api/applications | Validates the application, stores the application record, calls the selected provider and records the prediction when successful. |

Expected status-code semantics documented by the Java application:

| Status | Meaning |
|---|---|
| 201 Created | Application and prediction workflow completed successfully. |
| 400 Bad Request | Invalid application input. |
| 500 Internal Server Error | Internal or persistence failure. |
| 502 Bad Gateway | Upstream prediction provider failed after application processing began; the application identifier may be retained in the response. |

### Python inference service

| Method | Path | Purpose |
|---|---|---|
| GET | /health | Checks the service and loaded model. |
| GET | /model-info | Returns model version, threshold and metadata metrics. |
| POST | /predict | Accepts the seven active features and returns the class/probability. |

Example request to the Python service:

~~~json
{
  "INVESTMENT_TOTAL": 2000000,
  "ACCCURRENTBALANCE": 100000,
  "INSTALL_SIZE": 10000,
  "DUE_PAYMENT": 0,
  "COMPENSATION_CHARGED": "N",
  "CLIENT_TYPE": "Rural",
  "REPAY_MODE": "N"
}
~~~

Response fields include predicted_class, probability_b, threshold_used, and model_version. Returned values depend on the active model and supplied input; example inputs are for demonstration only.

## Build and tests

Run the Python tests and Java verification against the exact checkout you intend to publish:

~~~powershell
& ".\ml_service\.venv\Scripts\python.exe" -m unittest discover -s ".\ml_service\tests" -v
ant test
ant verify
~~~

Historical project notes include successful Java and Python test runs. Test counts can vary by checkout; use the actual command output for a release claim. Model-loading libraries should match the pinned package versions used for the saved artifact.

## Model notes

The current committed metadata (ml_service/models/model_metadata.json) identifies:

- Model version: v2.0
- Selected model: **LightGBM**
- Positive/negative classes: B / G
- Decision threshold: approximately 0.42
- Selection and threshold metrics: calculated on the project's validation split.

The metadata reports validation ROC-AUC around **0.781** and threshold-specific validation precision/recall around **0.205 / 0.800**. These are validation results, not independent external-test performance or a guarantee about future applicants. The threshold was selected using validation data; do not present those figures as unbiased performance on unseen populations.

Older repository documentation describes an earlier model snapshot (including Random Forest, a 0.30 threshold and historical metrics). Those figures should not be mixed with the active v2.0 LightGBM metadata. A separate prior holdout audit also reported near-chance ROC-AUC for an earlier snapshot; it is not directly comparable unless the exact model version and evaluation data are matched.

## Limitations and responsible use

- QUALITY_OF_LOAN classes G and B remain dataset labels until their domain meaning is verified.
- Validation metrics do not establish generalisation to a new bank, borrower population, time period or economic environment.
- Class imbalance and distribution shift can make accuracy misleading; review precision, recall, PR-AUC, calibration and confusion matrices.
- Threshold choice affects false positives and false negatives and should be reviewed with domain experts.
- Do not use the prototype for actual credit approval, rejection, pricing or adverse action.
- In-memory storage is temporary; live Oracle persistence needs an independent end-to-end test.
- An academic demo is not a security, fairness, compliance or model-risk certification.

## Documentation

- [Complete architecture and pipeline guide](docs/LOANLENS_COMPLETE_ARCHITECTURE_AND_PIPELINE_GUIDE.md)
- [ML integration handoff](docs/ml-integration-handoff.md)
- [ML service integration and v2.0 feature contract](docs/ml-service-integration.md)
- [Student/developer walkthrough](docs/project-walkthrough.md)
- [Model training and comparison code](ml_service/train_models.py)
- [Python inference service](ml_service/app/main.py)
- [Custom preprocessing pipeline](ml_service/app/pipeline.py)

## Future work

1. Verify target-label meaning and document dataset provenance/sampling.
2. Evaluate the exact released model on a representative independent holdout.
3. Review threshold choice, probability calibration and class-imbalance-aware metrics.
4. Add automated CI for Java and Python checks.
5. Test Oracle connectivity and persistent workflow end to end.
6. Document the exact working demo environment and include current screenshots.
7. Perform security, fairness, privacy and operational reviews before any non-academic use.

## License

No open-source license has been confirmed for this repository. Unless a license is added by the owners, do not assume permission to reuse, modify or redistribute the project.