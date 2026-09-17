from __future__ import annotations

import logging
import time

from app.celery_app import celery
from app.config import settings
from app.services.errors import VtonInferenceError
from app.services.job_store import (
    STATUS_COMPLETED,
    STATUS_FAILED,
    STATUS_PROCESSING,
    job_store,
)
from app.services.model_loader import catvton_loader

logger = logging.getLogger("aura.vton.tasks")


@celery.task(name="aura.vton.run_try_on", bind=True)
def run_try_on(
    self,
    job_id: str,
    user_id: int | None = None,
    wardrobe_item_id: int | None = None,
    person_image_base64: str | None = None,
    garment_image_base64: str | None = None,
    person_image_url: str | None = None,
    garment_image_url: str | None = None,
    cloth_type: str = "upper",
) -> dict:
    """CatVTON try-on task — Redis job durumunu gunceller."""
    logger.info(
        "VTON task basladi: jobId=%s celeryId=%s itemId=%s clothType=%s mock=%s urlPerson=%s",
        job_id,
        self.request.id,
        wardrobe_item_id,
        cloth_type,
        settings.mock_model,
        bool(person_image_url),
    )
    job_store.update(
        job_id,
        status=STATUS_PROCESSING,
        celeryTaskId=self.request.id,
        clothType=cloth_type,
    )

    try:
        if settings.mock_model and settings.mock_delay_seconds > 0:
            time.sleep(settings.mock_delay_seconds)

        result = catvton_loader.try_on(
            job_id=job_id,
            person_image_base64=person_image_base64,
            garment_image_base64=garment_image_base64,
            person_image_url=person_image_url,
            garment_image_url=garment_image_url,
            cloth_type=cloth_type,
        )
        payload = job_store.update(
            job_id,
            status=STATUS_COMPLETED,
            resultImageUri=result["resultImageUri"],
            resultImageBase64=result.get("resultImageBase64"),
            filePath=result.get("filePath"),
            model=result.get("model"),
            device=result.get("device"),
            maskSource=result.get("maskSource"),
            poseSource=result.get("poseSource"),
            clothType=result.get("clothType", cloth_type),
            inferenceSteps=result.get("inferenceSteps"),
            guidanceScale=result.get("guidanceScale"),
            errorMessage=None,
        )
        logger.info("VTON task tamamlandi: jobId=%s uri=%s", job_id, result["resultImageUri"])
        return payload
    except VtonInferenceError as exc:
        logger.error("VTON kontrollu hata jobId=%s code=%s: %s", job_id, exc.code, exc.message)
        return job_store.update(
            job_id,
            status=STATUS_FAILED,
            errorMessage=f"[{exc.code}] {exc.message}",
        )
    except Exception as exc:  # noqa: BLE001
        logger.exception("VTON task basarisiz: jobId=%s", job_id)
        return job_store.update(
            job_id,
            status=STATUS_FAILED,
            errorMessage=str(exc),
        )
