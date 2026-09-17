from __future__ import annotations

import base64
import io
import logging
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

from app.config import settings

logger = logging.getLogger("aura.vton.output")


@dataclass(frozen=True)
class StoredResult:
    result_image_uri: str
    result_image_base64: str
    file_path: str


class OutputStore:
    """Sonuc PNG'lerini diskte saklar; istemci icin Base64 + URI uretir."""

    def __init__(self, output_dir: str | None = None, public_base_url: str | None = None) -> None:
        self.output_dir = Path(output_dir or settings.output_dir).expanduser()
        self.public_base_url = (public_base_url or settings.public_base_url).rstrip("/")
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def save(self, job_id: str, image: Image.Image, *, optimize_quality: int = 88) -> StoredResult:
        safe_id = "".join(ch for ch in str(job_id) if ch.isalnum() or ch in "-_")
        file_path = self.output_dir / f"{safe_id}.png"
        rgb = image.convert("RGB")
        rgb.save(file_path, format="PNG", optimize=True)

        # Mobil poll icin daha kucuk JPEG/PNG base64 (max kenar 768)
        preview = rgb.copy()
        preview.thumbnail((768, 1024), Image.LANCZOS)
        buffer = io.BytesIO()
        preview.save(buffer, format="JPEG", quality=optimize_quality, optimize=True)
        b64 = base64.b64encode(buffer.getvalue()).decode("ascii")

        http_uri = f"{self.public_base_url}/outputs/{file_path.name}"
        logger.info("VTON sonuc yazildi: %s (%s)", file_path, http_uri)
        return StoredResult(
            result_image_uri=http_uri,
            result_image_base64=b64,
            file_path=str(file_path.resolve()),
        )


output_store = OutputStore()
