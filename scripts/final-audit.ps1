param(
    [switch]$RunPythonTests,
    [switch]$CheckLiveApi
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

$script:Passed = 0
$script:Warnings = 0

function Pass([string]$Message) {
    $script:Passed++
    Write-Host "[PASS] $Message" -ForegroundColor Green
}
function Warn([string]$Message) {
    $script:Warnings++
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}
function Section([string]$Message) {
    Write-Host ""
    Write-Host "=== $Message ===" -ForegroundColor Cyan
}

Write-Host "LoanLens read-only audit"
Write-Host "Project root: $ProjectRoot"
Write-Host "This script does not delete files, alter deployments, or build/overwrite a WAR."

Section "Expected source and documentation"
$required = @(
    "README.md",
    "HANDOVER.md",
    "build.xml",
    "database/schema.sql",
    "docs/FINAL_AUDIT_REPORT.md",
    "docs/ml-model-audit.md",
    "docs/ml-service-integration.md",
    "docs/ml-integration-handoff.md",
    "docs/project-walkthrough.md",
    "ml_service/app/main.py",
    "ml_service/app/pipeline.py",
    "ml_service/models/best_model.joblib",
    "ml_service/models/model_metadata.json",
    "ml_service/requirements-runtime.txt",
    "ml_service/tests/test_prediction_api.py",
    "src/java/com/loanrisk/web/LoanApplicationServlet.java",
    "test/com/loanrisk/test/TestRunner.java",
    "web/index.jsp",
    "web/workspace.jsp",
    "web/js/app.js"
)
foreach ($relative in $required) {
    if (Test-Path (Join-Path $ProjectRoot $relative)) {
        Pass "Found $relative"
    } else {
        Warn "Missing $relative"
    }
}

Section "Current model metadata"
$metadataPath = Join-Path $ProjectRoot "ml_service/models/model_metadata.json"
if (Test-Path $metadataPath) {
    $metadata = Get-Content $metadataPath -Raw | ConvertFrom-Json
    if ($metadata.model_version -and $metadata.selected_model -and
        $metadata.threshold -ge 0 -and $metadata.threshold -le 1) {
        Pass ("Model metadata: version={0}; model={1}; threshold={2}" -f
            $metadata.model_version, $metadata.selected_model, $metadata.threshold)
    } else {
        Warn "Model metadata is missing a version/model or has an invalid threshold."
    }

    $expectedFeatures = @(
        "INVESTMENT_TOTAL", "ACCCURRENTBALANCE", "INSTALL_SIZE", "DUE_PAYMENT",
        "COMPENSATION_CHARGED", "CLIENT_TYPE", "REPAY_MODE"
    )
    $actualFeatures = @($metadata.input_features)
    if ($actualFeatures.Count -eq 7 -and
        (@(Compare-Object $expectedFeatures $actualFeatures).Count -eq 0)) {
        Pass "Metadata lists the seven active non-demographic input features"
    } else {
        Warn "Metadata input feature list differs from the expected v2.0 seven-field contract."
    }
} else {
    Warn "Model metadata file not found."
}

Section "README link hygiene"
$readmePath = Join-Path $ProjectRoot "README.md"
if (Test-Path $readmePath) {
    $readme = Get-Content $readmePath -Raw
    if ($readme -match "file:///|D:/Projects/Loan Default Risk Prediction|E:\\College Projects\\Loan-Default-Risk-Prediction-copy") {
        Warn "README still contains a machine-local file path."
    } else {
        Pass "README has no known machine-local file links."
    }
    if ($readme -match "academic prototype" -and $readme -match "not for real lending decisions") {
        Pass "README includes an academic-use/model limitation notice."
    } else {
        Warn "README's academic-use/model limitation notice may be missing."
    }
}

Section "Frontend syntax"
$node = Get-Command node -ErrorAction SilentlyContinue
if ($node) {
    & $node.Source --check (Join-Path $ProjectRoot "web/js/app.js")
    if ($LASTEXITCODE -eq 0) {
        Pass "JavaScript syntax check passed."
    } else {
        Warn "JavaScript syntax check exited with code $LASTEXITCODE."
    }
} else {
    Warn "Node.js is unavailable; skipped JavaScript syntax check."
}

Section "Python tests"
$python = Join-Path $ProjectRoot "ml_service/.venv/Scripts/python.exe"
if ($RunPythonTests) {
    if (Test-Path $python) {
        & $python -m unittest discover -s (Join-Path $ProjectRoot "ml_service/tests") -v
        if ($LASTEXITCODE -eq 0) {
            Pass "Python regression tests passed."
        } else {
            Warn "Python tests exited with code $LASTEXITCODE."
        }
    } else {
        Warn "Python virtual environment not found at ml_service/.venv. Install requirements-runtime.txt first."
    }
} else {
    Write-Host "Skipped by default. To run: .\scripts\final-audit.ps1 -RunPythonTests"
}

Section "Optional live API check"
if ($CheckLiveApi) {
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:8000/health" -TimeoutSec 3
        if ($health.status -eq "ok" -and $health.model_loaded -eq $true) {
            Pass "Python API health reports a loaded model."
        } else {
            Warn "Python API responded but did not report status=ok and model_loaded=true."
        }
        $info = Invoke-RestMethod -Uri "http://127.0.0.1:8000/model-info" -TimeoutSec 3
        if ($info.model_version -and $info.selected_model -and $info.threshold -ge 0 -and $info.threshold -le 1) {
            Pass ("Live model: version={0}; model={1}; threshold={2}" -f $info.model_version, $info.selected_model, $info.threshold)
        } else {
            Warn "Live model-info response is incomplete."
        }
    } catch {
        Warn "Python API is not reachable. Start it in another terminal and retry."
    }
} else {
    Write-Host "Skipped by default. To check: .\scripts\final-audit.ps1 -CheckLiveApi"
}

Section "Summary"
Write-Host ("Passed checks: {0}" -f $script:Passed) -ForegroundColor Green
Write-Host ("Warnings / checks needing attention: {0}" -f $script:Warnings) -ForegroundColor Yellow
Write-Host "This script is a convenience checklist, not a model-quality, security, Oracle, or release certification."
