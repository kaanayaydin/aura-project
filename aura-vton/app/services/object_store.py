"""VTON sonucunu S3/MinIO'ya yukleme (opsiyonel)."""

from __future__ import annotations

import io
import logging
from dataclasses import dataclass

from PIL import Image

from app.config import settings

logger = logging.getLogger("aura.vton.object_store")


@dataclass(frozen=True)
class UploadedObject:
    object_url: str
    bucket: str
    key: str


def upload_result_png(job_id: str, image: Image.Image) -> UploadedObject | None:
    """AURA_VTON_S3_ENABLED=true ise aura-vton bucket'ina PNG yazar."""
    if not getattr(settings, "s3_enabled", False):
        return None
    try:
        import boto3
        from botocore.client import Config
    except ImportError:
        logger.warning("boto3 yok — S3 upload atlandi")
        return None

    bucket = settings.s3_vton_bucket
    key = f"results/{job_id}.png"
    buffer = io.BytesIO()
    image.convert("RGB").save(buffer, format="PNG", optimize=True)
    buffer.seek(0)

    client = boto3.client(
        "s3",
        endpoint_url=settings.s3_endpoint,
        aws_access_key_id=settings.s3_access_key,
        aws_secret_access_key=settings.s3_secret_key,
        region_name=settings.s3_region,
        config=Config(s3={"addressing_style": "path" if settings.s3_path_style else "virtual"}),
    )
    client.put_object(Bucket=bucket, Key=key, Body=buffer.getvalue(), ContentType="image/png")
    base = settings.s3_public_base_url.rstrip("/")
    object_url = f"{base}/{bucket}/{key}" if settings.s3_path_style else f"{base}/{key}"
    logger.info("VTON sonuc S3'e yazildi: %s", object_url)
    return UploadedObject(object_url=object_url, bucket=bucket, key=key)
