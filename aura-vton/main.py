"""Aura VTON Worker giris noktasi.

Lokal (mock — test/CI):
  docker compose up -d redis
  cd aura-vton && uvicorn main:app --port 8001
  celery -A app.celery_app.celery worker --loglevel=INFO

Gercek CatVTON (Apple Silicon ornek):
  export AURA_VTON_MOCK_MODEL=false
  export PYTORCH_ENABLE_MPS_FALLBACK=1
  export PYTORCH_MPS_HIGH_WATERMARK_RATIO=0.0
  export HF_HUB_DISABLE_XET=1
  pip install -r requirements.txt -r requirements-ml.txt
  celery -A app.celery_app.celery worker --pool=solo --loglevel=INFO -Q aura-vton
  uvicorn main:app --port 8001
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.api.internal_vton import router as vton_router
from app.config import settings
from app.services.model_loader import catvton_loader

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("aura.vton")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(
        "Aura VTON basliyor (port=%s redis=%s mock=%s cache=%s)",
        settings.port,
        settings.redis_url,
        settings.mock_model,
        settings.cache_dir,
    )
    try:
        catvton_loader.ensure_ready()
    except Exception:
        logger.exception("Model yukleme basarisiz — enqueue sonrasi task da deneyecek")
    yield
    logger.info("Aura VTON kapanıyor")


app = FastAPI(
    title="Aura VTON Worker",
    version="0.17.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(vton_router)

_output_dir = Path(settings.output_dir).expanduser()
_output_dir.mkdir(parents=True, exist_ok=True)
app.mount("/outputs", StaticFiles(directory=str(_output_dir)), name="outputs")


@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "aura-vton",
        "version": "0.17.0",
        "mockModel": settings.mock_model,
        "mode": catvton_loader.mode if catvton_loader.is_ready() else "not-ready",
        "executionMode": settings.execution_mode,
        "schpEnabled": settings.schp_enabled,
        "poseEnabled": settings.pose_enabled,
        "poseOnnxEnabled": settings.pose_onnx_enabled,
        "numInferenceSteps": settings.num_inference_steps,
        "guidanceScale": settings.guidance_scale,
        "redisUrl": settings.redis_url,
        "modelReady": catvton_loader.is_ready(),
        "cacheDir": settings.cache_dir,
        "outputDir": settings.output_dir,
    }
