"""Base64 / data-URI gorsel cozumleme — VTON person + garment."""

from __future__ import annotations

import base64
import io
import re

from PIL import Image

from app.services.errors import VtonInferenceError

_DATA_URI_RE = re.compile(r"^data:image/[a-zA-Z0-9.+-]+;?[^,]*,", re.IGNORECASE)


def normalize_base64_payload(raw: str) -> str:
    """Data-URI oneki + whitespace temizler; padding tamamlar."""
    value = raw.strip().strip('"').strip("'")
    if not value:
        return ""

    # data:image/...;base64,XXXX  veya genel data:,XXXX
    if value.lower().startswith("data:") and "," in value:
        value = value.split(",", 1)[-1]
    elif _DATA_URI_RE.match(value):
        value = value.split(",", 1)[-1]

    value = "".join(value.split())
    if not value:
        return ""

    pad = (-len(value)) % 4
    if pad:
        value += "=" * pad
    return value


def decode_base64_image(raw: str | None, *, label: str = "image") -> Image.Image | None:
    """Base64 (veya data-URI) → RGB PIL Image.

    Basarisizda ``BAD_IMAGE`` ile ``VtonInferenceError`` firlatir.
    """
    if raw is None:
        return None
    if not str(raw).strip():
        return None

    value = normalize_base64_payload(str(raw))
    if not value:
        return None

    try:
        try:
            data = base64.b64decode(value, validate=False)
        except Exception:
            data = base64.urlsafe_b64decode(value)

        if not data:
            raise ValueError("bos byte dizisi")

        with Image.open(io.BytesIO(data)) as img:
            img.load()
            return img.convert("RGB")
    except VtonInferenceError:
        raise
    except Exception as exc:  # noqa: BLE001
        preview = value[:48] + ("…" if len(value) > 48 else "")
        raise VtonInferenceError(
            f"{label} base64 cozulemedi: {exc} (prefix={preview!r})",
            code="BAD_IMAGE",
        ) from exc
