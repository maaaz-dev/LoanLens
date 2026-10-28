# LoanLens — Student & Developer Walkthrough

**Project:** Loan Default Risk Prediction (LoanLens)  
**Audience:** Students, project evaluators and developers  
**Purpose:** Academic web + ML integration demonstration

> **Current version note:** This guide describes the current repository snapshot: Java web workflow + Python FastAPI inference API. The active v2.0 Python contract contains seven non-demographic input features and uses the model specified in ml_service/models/model_metadata.json. Older project notes describing nine API inputs, Random Forest, or a 0.30 threshold belong to an earlier snapshot and must not be used as the current integration contract.

## 1. Project summary

LoanLens demonstrates an end-to-end flow for entering loan-related features in a browser, validating and recording an application with a Java backend, requesting a classification from a Python model service, and displaying the result.

It is made of separate components so that the browser interface, Java application workflow, persistence and Python inference are not one tightly coupled block of code.

**Academic scope:** This is a learning prototype, not a production lending or underwriting system. The dataset labels G and B must not be treated as verified real-world good-loan/default outcomes without a reliable data dictionary.

## 2. End-to-end request lifecycle

~~~mermaid
sequenceDiagram
    actor User
    participant UI as JSP / Browser
    participant Servlet as Java Servlet
    participant Service as Application Service
    participant Repo as Repository
    participant Provider as Prediction Provider
    participant API as Python FastAPI
    participant Model as Saved Model Pipeline

    User->>UI: Enter synthetic values and submit
    UI->>Servlet: POST /api/applications
    Servlet->>Service: Parse and validate request
    Service->>Repo: Save application
    Service->>Provider: Request prediction
    alt Mock mode
        Provider-->>Service: Simulated result
    else HTTP mode
        Provider->>API: POST /predict
        API->>Model: Preprocess and predict
        Model-->>API: Probability / class
        API-->>Provider: JSON prediction
        Provider-->>Service: Validated prediction
    end
    Service->>Repo: Save prediction record
    Service-->>Servlet: Workflow result
    Servlet-->>UI: JSON status and result
~~~

### What each part does

- **Browser UI:** collects the example inputs and displays validation feedback/results.
- **Servlet:** receives the HTTP request and returns the application's response.
- **Application service:** coordinates validation, persistence and prediction.
- **Repository:** hides the details of the selected storage implementation.
- **Prediction provider:** selects mock output or the Java HTTP client, based on configuration.
- **Python FastAPI:** validates the active ML input contract, applies the saved model pipeline and returns the result.
- **Model metadata:** identifies the model version, class mapping and threshold used by the running Python service.

## 3. Active seven-feature model contract

The current Python API accepts these fields:

| Field | Plain-language meaning |
|---|---|
| INVESTMENT_TOTAL | Requested amount; must be greater than zero. |
| ACCCURRENTBALANCE | Current account balance; negative values are allowed by the active contract. |
| INSTALL_SIZE | Installment size; zero is allowed. |
| DUE_PAYMENT | Current amount due; must be zero or positive. |
| COMPENSATION_CHARGED | Categorical compensation/penalty indicator. |
| CLIENT_TYPE | Client/area category represented in the dataset. |
| REPAY_MODE | Repayment mode/category represented in the dataset. |

The active service rejects extra input fields, including INF_GENDER and INF_MARITAL_STATUS. For valid categories, values must match the model's supported training categories or documented aliases. See [ML service integration](ml-service-integration.md).

## 4. Java and Python: why two languages?

- **Java** runs the web application and orchestrates request validation, storage and provider selection.
- **Python** serves the ML preprocessing/model pipeline using FastAPI.
- **HTTP + JSON** is the boundary: Java sends the feature values; Python returns a structured result.
- **Separation of concerns** makes it possible to test the Java request workflow and Python inference layer independently, then test their interface.

A successful Python model response includes:
- predicted_class — G or B
- probability_b — model probability associated with class B
- threshold_used — threshold in effect for this call
- model_version — active artifact version

A sample response shape is shown below. The numeric value is an illustration, not a prediction for an actual person.

~~~json
{
  "predicted_class": "G",
  "probability_b": 0.21,
  "threshold_used": 0.42,
  "model_version": "v2.0"
}
~~~

## 5. Mock mode vs HTTP mode

| Mode | Behaviour | How to describe the result |
|---|---|---|
| mock | Java returns a simulated development result without relying on the Python inference API. | Simulated / mock output. |
| http | Java sends the seven-feature request to the running Python FastAPI service. | Model-backed result only after confirming the active provider and API response. |

Do not quietly switch to mock mode after an HTTP failure and present the output as a real model result. Check the Java health/configuration response and Python /health and /model-info endpoints before a demo.

## 6. Storage modes

- **memory:** useful for a database-free local demo; records are held in process memory and are lost when the app restarts.
- **oracle:** Oracle repository/schema code is present. A working Oracle database, compatible JDBC driver, correct credentials/schema and a successful end-to-end persistence test are still required before claiming live Oracle support is verified.

## 7. Run and test

### Python service

From the repository root in Windows PowerShell:

~~~powershell
# Create this environment only if one does not already exist
py -3.14 -m venv .\ml_service\.venv

& ".\ml_service\.venv\Scripts\python.exe" -m pip install -r ".\ml_service\requirements-runtime.txt"
& ".\ml_service\.venv\Scripts\python.exe" -m unittest discover -s ".\ml_service\tests" -v

# Start the inference service
.\ml_service\start_service.ps1
~~~

Keep the service running and, in another terminal, verify:

~~~powershell
Invoke-RestMethod "http://127.0.0.1:8000/health" | ConvertTo-Json -Depth 5
Invoke-RestMethod "http://127.0.0.1:8000/model-info" | ConvertTo-Json -Depth 8
~~~

Check that the expected artifact/version, active model, input features and threshold are present. If Python reports a model-library version warning, reconcile the environment with the pinned requirements instead of ignoring the warning.

### Java web application

Prerequisites: JDK 21, Apache Tomcat 10.1.x, Apache Ant and the NetBeans CopyLibs build task referenced by build.xml.

~~~powershell
ant test
ant verify
~~~

If the ant command is unavailable, use the installed Ant executable and set the required Tomcat/CopyLibs properties as documented in the project build configuration. Read the first failing message and fix the stated prerequisite or test failure. Do not run destructive clean commands as a shortcut.

After deployment, the context path is based on the deployed WAR filename. For example, LoanLens.war normally serves at http://localhost:8080/LoanLens/. Verify that only the intended copy of the application is deployed during a demo.

## 8. API status codes

The Java application documents these status meanings:

| Status | Meaning |
|---|---|
| 201 Created | Application processing and prediction recording completed successfully. |
| 400 Bad Request | The Java application rejected invalid application input. |
| 500 Internal Server Error | Internal or persistence failure. |
| 502 Bad Gateway | The upstream prediction provider failed after application processing began; the application ID may be retained. |

The Python API uses 422 for invalid input or unsupported categorical values. See the active implementation in [main.py](../ml_service/app/main.py).

## 9. Model evaluation — what can and cannot be claimed

The checked-in metadata identifies the active snapshot as **LightGBM v2.0**, with the positive class B, negative class G, and a threshold near 0.42. Validation metrics in model_metadata.json describe the project's validation split. They are not independent external-test results and do not guarantee generalisation to new applicants.

Older repository text contains earlier experiments with different models, thresholds and metrics. Keep those historical results separate from the active v2.0 metadata. Before making any real-world claim, the team would need to verify the dataset source and target meaning, document the split methodology, and evaluate the exact release on representative independent data.

**Never say that the model is 100% accurate or that class B is a confirmed legal default. Do not use this academic prototype to approve, reject or price real loans.**

## 10. Viva questions — concise answers

**What is LoanLens?**  
An academic web application that integrates a Java application workflow with a separate Python machine-learning API.

**Why use Java and Python together?**  
Java handles web requests, validation and application workflow; Python hosts the ML pipeline. They communicate through HTTP and JSON.

**What is FastAPI doing?**  
It exposes endpoints to check the service/model and to receive a prediction request.

**What is a REST API?**  
A way for separate software components to exchange requests and responses over HTTP.

**What is a model threshold?**  
A configurable cutoff used to convert the model's class-B probability into a G/B label. Changing it can change false positives and false negatives.

**Mock vs HTTP mode?**  
Mock generates simulated output. HTTP calls the Python inference API; the active mode must be checked before a demo.

**Why is in-memory storage limited?**  
Its records are not durable and disappear when the application process restarts.

**Is Oracle deployment verified?**  
Repository/schema code exists, but it should only be described as verified after a successful live database integration test.

**Is this suitable for real loan decisions?**  
No. It is an academic prototype and needs verified target semantics, independent evaluation, calibration, fairness/privacy review and operational validation.

## Related documentation

- [Main README](../README.md)
- [Active ML service integration guide](ml-service-integration.md)
- [ML handoff/API contract](ml-integration-handoff.md)
- [Complete architecture reference (historical details may differ)](LOANLENS_COMPLETE_ARCHITECTURE_AND_PIPELINE_GUIDE.md)
