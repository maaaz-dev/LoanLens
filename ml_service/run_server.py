"""Standalone server runner for LoanLens ML Inference Service.

Starts Uvicorn serving the FastAPI application on http://127.0.0.1:8000.
Can be executed from repository root or from within ml_service:
    python ml_service/run_server.py
"""
import sys
from pathlib import Path
import uvicorn

CURRENT_DIR = Path(__file__).resolve().parent
REPO_ROOT = CURRENT_DIR.parent

if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

if __name__ == "__main__":
    host = "127.0.0.1"
    port = 8000
    print(f"Starting LoanLens ML Inference API on http://{host}:{port} ...")
    uvicorn.run("ml_service.app.main:app", host=host, port=port, log_level="info")
