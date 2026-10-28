# LoanLens — Current Project Handover

**Purpose:** College demonstration and engineering handover  
**Repository snapshot reviewed:** 28 October 2026  
**Scope:** Java web application + Python FastAPI inference service; not a production lending system.

## Current implementation

LoanLens is a Java 21 / Jakarta Servlet web application with a separate Python FastAPI model-serving service. The Java layer handles the web request, validation, application workflow, storage/provider selection and result response. With HTTP prediction mode enabled, it posts the active seven-feature input contract to FastAPI.

**Current model metadata in the repository:** LightGBM, version v2.0, positive class B, negative class G, threshold approximately 0.42. Read the live /model-info endpoint and checked-in metadata to verify the runtime before every demo.

## Important ML limitations

- QUALITY_OF_LOAN classes G and B are dataset labels. The available project evidence does not establish B as a verified legal/financial default outcome.
- Validation ROC-AUC is about 0.7807, with threshold-specific validation precision about 20.53% and recall about 80.02%. The model/threshold selection used validation data, so these are not independent test guarantees.
- The service integration guide records a separate holdout of 4,305 labelled rows, of which only 19 were Class B, with ROC-AUC 0.5282 and precision 0.72% at the recorded threshold. The raw holdout data is not included in this repository, so these figures cannot be reproduced from the public source tree alone.
- Do not use this prototype to approve, reject or price real loans.

## Prerequisites

- JDK 21
- Apache Tomcat 10.1.x
- Apache Ant and the NetBeans CopyLibs task referenced by the build
- A Python environment installed from ml_service/requirements-runtime.txt
- The bundled model and metadata under ml_service/models

## Run the Python service

From the project root in Windows PowerShell:

~~~powershell
# Create the environment only if it does not already exist
py -3.14 -m venv .\ml_service\.venv
& ".\ml_service\.venv\Scripts\python.exe" -m pip install -r ".\ml_service\requirements-runtime.txt"

# Run the service tests
& ".\ml_service\.venv\Scripts\python.exe" -m unittest discover -s ".\ml_service\tests" -v

# Start the API
.\ml_service\start_service.ps1
~~~

Keep the API terminal open. In another terminal, check:

~~~powershell
Invoke-RestMethod "http://127.0.0.1:8000/health" | ConvertTo-Json -Depth 5
Invoke-RestMethod "http://127.0.0.1:8000/model-info" | ConvertTo-Json -Depth 8
~~~

The active response should show the current model version, selected model, threshold, and input features. Do not assume a response is model-backed solely because the UI displays a result.

## Run Java checks

From the project root, after configuring the installed Ant/Tomcat/CopyLibs paths:

~~~powershell
ant test
ant verify
~~~

If Ant or a required NetBeans build property is unavailable, configure the actual installed path before running. Read the first build error and fix the stated issue. Avoid destructive clean/reset operations as a shortcut.

## Modes and storage

- Mock prediction mode returns simulated output. Label it as simulated.
- HTTP prediction mode calls the Python service. Confirm its health and active provider before a demo.
- In-memory storage is temporary; application records disappear when the process restarts.
- Oracle schema/repository code exists, but live Oracle deployment and persistent end-to-end behavior must be tested separately before claiming it works.

## Safe deployment checklist

1. Run Python regression tests and Java verification on the exact checkout being released.
2. Start the Python API and verify /health and /model-info.
3. Deploy only one copy/context of the application in Tomcat.
4. Use synthetic example data and confirm the active prediction provider.
5. Submit a sample, inspect the returned class/probability/threshold/version and application/prediction IDs.
6. Inspect the application and Tomcat logs for failures.
7. Keep the model caveats above visible in the report/demo.

## Repository hygiene

Do not commit virtual environments, credentials, .env files, personal/financial records, private datasets, local deployment backups or generated WARs unless a deliberate release workflow explicitly calls for them. Review git status and the staged diff before committing.

## Related documents

- [Main README](README.md)
- [Current ML service integration guide](docs/ml-service-integration.md)
- [Model audit](docs/ml-model-audit.md)
- [Repository audit report](docs/FINAL_AUDIT_REPORT.md)
- [Student walkthrough](docs/project-walkthrough.md)
