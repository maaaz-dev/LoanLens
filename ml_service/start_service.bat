@echo off
setlocal
cd /d "%~dp0\.."

set "PYTHON_EXE="
if exist "%~dp0\.venv\Scripts\python.exe" set "PYTHON_EXE=%~dp0\.venv\Scripts\python.exe"
if not defined PYTHON_EXE if exist "%cd%\.venv\Scripts\python.exe" set "PYTHON_EXE=%cd%\.venv\Scripts\python.exe"
if not defined PYTHON_EXE if exist "%~dp0\venv\Scripts\python.exe" set "PYTHON_EXE=%~dp0\venv\Scripts\python.exe"
if not defined PYTHON_EXE set "PYTHON_EXE=python"

echo ============================================================
echo  Starting LoanLens Python Machine Learning Inference API
echo  Python: %PYTHON_EXE%
echo  Endpoint: http://127.0.0.1:8000
echo ============================================================

"%PYTHON_EXE%" "%~dp0run_server.py"
