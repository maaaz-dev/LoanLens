<#
.SYNOPSIS
    Starts the LoanLens Python ML inference service on http://127.0.0.1:8000.
.DESCRIPTION
    Detects Python in the dedicated ml_service virtual environment or system PATH, then launches Uvicorn.
#>

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
Set-Location $RepoRoot

# Look for dedicated local virtual environment first, then repository root, then system PATH
$PythonPaths = @(
    (Join-Path $ScriptDir ".venv\Scripts\python.exe"),
    (Join-Path $RepoRoot ".venv\Scripts\python.exe"),
    (Join-Path $ScriptDir "venv\Scripts\python.exe"),
    "python"
)

$PythonExe = $null
foreach ($path in $PythonPaths) {
    if ($path -eq "python") {
        $PythonExe = "python"
        break
    } elseif (Test-Path $path) {
        $PythonExe = $path
        break
    }
}

if (-not $PythonExe) {
    $PythonExe = "python"
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Starting LoanLens Python Machine Learning Inference API" -ForegroundColor Green
Write-Host " Python: $PythonExe" -ForegroundColor Gray
Write-Host " Endpoint: http://127.0.0.1:8000" -ForegroundColor Gray
Write-Host " Press Ctrl+C to stop." -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Cyan

& $PythonExe (Join-Path $ScriptDir "run_server.py")
