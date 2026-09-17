"""Aura Vision Service giris noktasi.

Lokal calistirma:
    uvicorn main:app --reload --port 8000
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1.vision import router as vision_router
from app.core.config import settings
from app.core.exceptions import ModelUnavailableError
from app.services.object_detector import object_detector
from app.services.segmenter import segmenter
from app.services.style_classifier import style_classifier
from app.services.wardrobe_sync import wardrobe_sync_client

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("aura.vision")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Modelleri aciliste hazirlar; ilk istek indirmeyi beklemez.

    Bir model yuklenemezse servis yine de ayaga kalkar: /health durumu
    bildirir, ilgili asama atlanir veya /analyze 503 doner.
    """
    if settings.preload_model:
        loaders = [("YOLO", object_detector.load)]
        if settings.segmentation_enabled:
            loaders.append(("SAM", segmenter.load))
        if settings.classification_enabled:
            loaders.append(("CLIP", style_classifier.load))

        for name, load in loaders:
            try:
                load()
            except ModelUnavailableError as exc:
                logger.warning(
                    "%s aciliste yuklenemedi, ilk istekte tekrar denenecek: %s", name, exc
                )
    yield


def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.project_name,
        version=settings.version,
        description="Aura'nin gorsel analiz motoru: metadata cikarimi ve YOLOv8 nesne tespiti.",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=list(settings.cors_origins),
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(vision_router, prefix=settings.api_v1_prefix)

    @app.get("/health", tags=["System"], summary="Servis sagligi")
    async def health_check() -> dict:
        return {
            "status": "up",
            "service": settings.project_name,
            "version": settings.version,
            "pipeline": {
                "detection": {
                    "model": settings.yolo_model_name,
                    "loaded": object_detector.is_loaded,
                    "device": object_detector.device,
                    "device_preference": settings.yolo_device,
                },
                "segmentation": {
                    "model": settings.sam_model_id,
                    "loaded": segmenter.is_loaded,
                    "enabled": settings.segmentation_enabled,
                    "device": segmenter.device,
                    "device_preference": settings.sam_device,
                },
                "classification": {
                    "model": settings.clip_model_id,
                    "loaded": style_classifier.is_loaded,
                    "enabled": settings.classification_enabled,
                    "device": style_classifier.device,
                    "device_preference": settings.clip_device,
                    "candidate_labels": list(settings.candidate_labels),
                },
            },
            "backend_sync": {
                "enabled": wardrobe_sync_client.enabled,
                "endpoint": wardrobe_sync_client.endpoint,
                "min_confidence": settings.backend_min_confidence,
                "user_id": settings.backend_user_id,
            },
            "studio": {
                "aspect": settings.studio_aspect,
                "long_side": settings.studio_long_side,
                "background": settings.studio_background_hex,
                "rembg": settings.studio_rembg_enabled,
            },
        }

    return app


app = create_app()
