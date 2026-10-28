# LoanLens — Repository and Release Readiness Audit

**Review date:** 28 October 2026  
**Scope:** Public GitHub repository, documentation consistency and available model metadata.  
**Not a substitute for:** a Windows build run, security certification, model validation study or production approval.

## Repository checks performed

- Confirmed the public repository and default branch: maaaz-dev/loan, main.
- Confirmed that the repository contains Java source, JSP/frontend assets, Java tests, Python inference source/tests, model artifacts/metadata, Oracle schema and project documentation.
- Rewrote the root README around the current Java + Python architecture and active v2.0 metadata.
- Replaced machine-local file links with repository-relative links in the long architecture guide.
- Refreshed the ML integration handoff and student walkthrough to describe the seven-feature v2.0 model contract.
- Updated the service integration guide to remove stale local machine paths and clarify that class B is a dataset label rather than a verified default outcome.
- Checked README relative links against the current GitHub file tree; the paths checked were present.
- Added `.github/workflows/ci.yml` for Python regression/contract tests on Python 3.13 and 3.14, JavaScript syntax checks, XML configuration parsing, and Java 21 tests using Tomcat 10.1 API jars.
- Verified an initial CI run passed the frontend/configuration job and Python tests on Python 3.13 and 3.14 (23 tests on Python 3.14).
- The first Java CI attempt exposed a NetBeans CopyLibs dependency in the generated Ant build; the workflow was changed to compile the Java source and test tree directly with `javac` and run `TestRunner`, avoiding machine-specific NetBeans tasks. The latest workflow run must complete before that Java check can be marked passing.
- Kept the existing repository history. No delete/recreate or force-push was used.

## Current implementation summary

The Java application accepts a loan-related application, validates it, records it through a storage abstraction and uses a configurable prediction provider. Mock mode is simulated. HTTP mode calls the Python FastAPI service. The active checked-in model metadata describes a LightGBM v2.0 pipeline with a seven-field raw input contract and a threshold around 0.42.

## Model and product limitations

- The metadata's validation ROC-AUC is approximately 0.7807; threshold-specific validation precision/recall is about 20.53% / 80.02%. These are validation/selection figures, not a guarantee for unseen populations.
- The current integration guide records an external holdout of 4,305 labelled cases with only 19 Class B rows. Reported ROC-AUC is 0.5282, precision 0.72% and recall 42.11% at threshold ~0.42. The raw holdout file is not included, so this report did not reproduce those metrics from raw data.
- The data dictionary has not established that QUALITY_OF_LOAN label B means a legal/financial loan default.
- Oracle repository/schema files exist, but this review did not establish a live Oracle deployment or durable database test.
- Mock results must not be described as model output.

## Verification status

This publishing pass inspected remote file contents and documentation links and added a GitHub Actions workflow. Python regression/contract tests passed on Python 3.13 and 3.14, and JavaScript syntax/XML checks passed. A Java 21 test job is now included using direct compilation against Tomcat 10.1 API jars; its latest run is not yet confirmed successful. The workflow does not build/deploy a WAR. This pass did not run the project on the user's Windows machine or perform live Tomcat deployment.

Run from the project root in Windows PowerShell:

~~~powershell
# Use the pinned ML runtime environment
& ".\ml_service\.venv\Scripts\python.exe" -m unittest discover -s ".\ml_service\tests" -v

# Check frontend syntax if Node.js is installed
node --check ".\web\js\app.js"

# Check the Java project with configured JDK 21, Ant, Tomcat and NetBeans CopyLibs paths
ant test
ant verify
~~~

## Release checklist

- [x] Current GitHub `main` Python tests pass on Python 3.13 and 3.14 in GitHub Actions.
- [ ] Current Java test job passes in the latest CI run; live Windows build/WAR deployment remains unverified.
- [ ] /health and /model-info report the expected active model/threshold.
- [ ] An end-to-end synthetic submission works with the configured provider.
- [ ] Only the intended Tomcat context is deployed.
- [ ] No credentials, .env files, virtual environments, private data or deployment backups are committed.
- [ ] README and model limitations are retained in the repository and demo.

## Overall status

The repository now has clearer documentation and a more accurate account of the current architecture and model limitations. This review does not certify the software or model as production-ready. The project should remain an academic demonstration until the data, model generalisation, persistent storage, security and operations are independently verified.
