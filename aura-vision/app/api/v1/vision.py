"""Vision endpoint'leri."""

from typing import Optional

from fastapi import APIRouter, BackgroundTasks, File, Form, Header, HTTPException, UploadFile, status

from app.core.config import settings
from app.core.exceptions import ModelUnavailableError
from app.schemas.vision import AnalyzeResponse, NormalizeGarmentResponse
from app.services.garment_normalizer import garment_normalizer
from app.services.image_analyzer import (
    InvalidImageError,
    UnsupportedImageFormatError,
    image_analyzer,
)
import logging

logger = logging.getLogger("aura.vision.api")

router = APIRouter(prefix="/vision", tags=["Vision"])


def _extract_bearer(authorization: Optional[str]) -> Optional[str]:
    if not authorization:
        return None
    raw = authorization.strip()
    if raw.lower().startswith("bearer "):
        token = raw[7:].strip()
        return token or None
    return raw or None


@router.post(
    "/analyze",
    response_model=AnalyzeResponse,
    status_code=status.HTTP_200_OK,
    summary="Gorsel analizi (metadata + YOLO tespiti + SAM kesimi + CLIP etiketlemesi)",
)
async def analyze_image(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(..., description="Analiz edilecek gorsel (JPEG, PNG, WEBP...)"),
    authorization: Optional[str] = Header(
        None,
        alias="Authorization",
        description="Opsiyonel: kullanici JWT — backend sync icin iletilir",
    ),
) -> AnalyzeResponse:
    raw_bytes = await file.read()

    if not raw_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Bos dosya gonderildi.",
        )

    if len(raw_bytes) > settings.max_upload_size_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Gorsel {0} MB sinirini asiyor.".format(settings.max_upload_size_mb),
        )

    bearer = _extract_bearer(authorization)

    try:
        result = image_analyzer.analyze(
            raw_bytes=raw_bytes,
            file_name=file.filename or "unknown",
            content_type=file.content_type,
            schedule=background_tasks.add_task,
            bearer_token=bearer,
        )
    except UnsupportedImageFormatError as exc:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=str(exc),
        ) from exc
    except InvalidImageError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except ModelUnavailableError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc

    categorized = sum(1 for item in result.detected_items if item.category)
    cutouts = sum(1 for item in result.detected_items if item.cutout_image_base64)
    return AnalyzeResponse(
        message="{0} nesne tespit edildi, {1} tanesi kategorilendirildi, {2} kesim hazir.".format(
            len(result.detected_items), categorized, cutouts
        ),
        pipeline_stage=result.pipeline_stage,
        stages_completed=result.stages_completed,
        detection_model=result.detection_model,
        segmentation_model=result.segmentation_model,
        classification_model=result.classification_model,
        wardrobe_synced=result.wardrobe_synced,
        wardrobe_sync_queued=result.wardrobe_sync_queued,
        metadata=result.metadata,
        detected_items=result.detected_items,
    )


@router.post(
    "/normalize-garment",
    response_model=NormalizeGarmentResponse,
    status_code=status.HTTP_200_OK,
    summary="Kiyafet stüdyo normalizasyonu (dekupaj + 3:4 framing)",
)
async def normalize_garment(
    file: UploadFile = File(..., description="Ham veya cutout kiyafet gorseli"),
    aspect: Optional[str] = Form(None, description="3:4 veya 1:1"),
    background: Optional[str] = Form(None, description="Hex arka plan (#F8F9FA)"),
    drop_shadow: Optional[str] = Form(None, description="true/false/1/0"),
) -> NormalizeGarmentResponse:
    raw_bytes = await file.read()
    if not raw_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Bos dosya gonderildi.",
        )
    if len(raw_bytes) > settings.max_upload_size_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Gorsel {0} MB sinirini asiyor.".format(settings.max_upload_size_mb),
        )

    shadow: Optional[bool] = None
    if drop_shadow is not None and str(drop_shadow).strip() != "":
        shadow = str(drop_shadow).strip().lower() in {"1", "true", "yes", "on"}

    try:
        b64, result = garment_normalizer.normalize_to_base64(
            raw_bytes,
            aspect=aspect,
            background_hex=background,
            drop_shadow=shadow,
        )
    except ValueError as exc:
        logger.warning("Normalize garment validation: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except Exception as exc:
        logger.exception("Normalize garment failed")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Normalize garment failed: {0}".format(exc),
        ) from exc

    return NormalizeGarmentResponse(
        message="Garment studio normalize tamam ({0}).".format(result.cutout_source),
        width=result.width,
        height=result.height,
        aspect=result.aspect,
        cutout_source=result.cutout_source,
        image_base64=b64,
        image_bytes=len(result.png_bytes),
    )
