"""HTTP / object URL uzerinden gorsel indirme."""

from __future__ import annotations

import io
import logging

import httpx
from PIL import Image

from app.services.errors import VtonInferenceError
from app.services.image_codec import decode_base64_image

logger = logging.getLogger("aura.vton.fetch")


def load_image_from_url(url: str, *, label: str = "image", timeout: float = 60.0) -> Image.Image:
    """Presigned / public URL → RGB PIL Image."""
    if not url or not str(url).strip():
        raise VtonInferenceError(f"{label} URL bos", code="BAD_IMAGE")
    try:
        with httpx.Client(timeout=timeout, follow_redirects=True) as client:
            response = client.get(url.strip())
            response.raise_for_status()
            data = response.content
        if not data:
            raise ValueError("bos govde")
        with Image.open(io.BytesIO(data)) as img:
            img.load()
            return img.convert("RGB")
    except VtonInferenceError:
        raise
    except Exception as exc:  # noqa: BLE001
        raise VtonInferenceError(
            f"{label} URL indirilemedi: {exc}",
            code="BAD_IMAGE",
        ) from exc


def resolve_image(
    *,
    image_url: str | None,
    image_base64: str | None,
    label: str,
) -> Image.Image | None:
    """URL oncelikli; yoksa base64. Ikisi de yoksa None."""
    if image_url and str(image_url).strip():
        return load_image_from_url(image_url, label=label)
    if image_base64 and str(image_base64).strip():
        return decode_base64_image(image_base64, label=label)
    return None
