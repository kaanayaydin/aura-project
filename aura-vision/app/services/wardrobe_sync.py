"""Java backend'ine dolap kaydi gonderen opsiyonel istemci.

Varsayilan olarak KAPALIDIR (`AURA_BACKEND_SYNC_ENABLED=true` ile acilir).
Analiz akisi bu entegrasyona bagimli degildir: backend kapali, yavas veya hata
donuyor olsa bile analiz cevabi etkilenmez, yalnizca uyari loglanir.

v0.19.1+: Prefer edilen yol — mobil JWT ile cutout yazar.
Sync aciksa istemcinin `Authorization: Bearer` token'i iletilmelidir.
Demo `/auth/token` yolu yanlis kullaniciya yazdigi icin artik kullanilmaz.
"""

from __future__ import annotations

import base64
import logging
from dataclasses import dataclass
from io import BytesIO
from typing import List, Optional

from PIL import Image

from app.core.config import settings

logger = logging.getLogger(__name__)


@dataclass
class SyncOutcome:
    """Gonderim ozeti; analiz cevabinda raporlanir."""

    attempted: int = 0
    succeeded: int = 0
    failed: int = 0

    @property
    def performed(self) -> bool:
        return self.attempted > 0


class WardrobeSyncClient:
    """Kesim + kategori ciftlerini backend'in dolap endpoint'ine gonderir."""

    def __init__(self) -> None:
        base = settings.backend_base_url.rstrip("/")
        self._endpoint = f"{base}/api/v1/wardrobe/items"

    @property
    def enabled(self) -> bool:
        return settings.backend_sync_enabled

    @property
    def endpoint(self) -> str:
        return self._endpoint

    def sync(
        self,
        entries: List["SyncEntry"],
        bearer_token: Optional[str] = None,
    ) -> SyncOutcome:
        """Verilen kayitlari tek tek gonderir, hatalari yutar."""
        outcome = SyncOutcome()
        if not self.enabled or not entries:
            return outcome

        if not bearer_token or not bearer_token.strip():
            logger.warning(
                "Dolap sync basarisiz: Bearer JWT yok (attempted=%s). "
                "Mobil Authorization header gondermeli veya cutout'lari kendisi yazmali.",
                len(entries),
            )
            outcome.attempted = len(entries)
            outcome.failed = len(entries)
            return outcome

        try:
            import httpx
        except ImportError:
            logger.warning("httpx kurulu degil, dolap senkronizasyonu atlandi.")
            return outcome

        token = bearer_token.strip()
        if token.lower().startswith("bearer "):
            token = token[7:].strip()
        headers = {"Authorization": f"Bearer {token}"}

        try:
            with httpx.Client(timeout=settings.backend_timeout_seconds) as client:
                for entry in entries:
                    outcome.attempted += 1
                    if self._post(client, entry, headers):
                        outcome.succeeded += 1
                    else:
                        outcome.failed += 1
        except Exception as exc:
            logger.warning("Dolap senkronizasyonu basarisiz: %s", exc)
            outcome.failed = outcome.attempted - outcome.succeeded

        if outcome.failed:
            logger.warning(
                "Dolap sync ozeti: succeeded=%s failed=%s (JWT ile)",
                outcome.succeeded,
                outcome.failed,
            )
        return outcome

    def _post(self, client, entry: "SyncEntry", headers: dict) -> bool:
        payload = {
            "category": entry.category,
            "categoryConfidence": entry.category_confidence,
            "imageBase64": entry.image_base64,
        }
        if entry.color:
            payload["color"] = entry.color

        try:
            response = client.post(self._endpoint, json=payload, headers=headers)
        except Exception as exc:
            logger.warning("Dolap kaydi gonderilemedi (%s): %s", entry.category, exc)
            return False

        if response.status_code >= 400:
            logger.warning(
                "Backend dolap kaydini reddetti (%s): HTTP %s %s",
                entry.category,
                response.status_code,
                response.text[:200],
            )
            return False

        logger.info("Dolap kaydi olusturuldu: %s", entry.category)
        return True


@dataclass
class SyncEntry:
    """Backend'e gonderilecek tek kayit."""

    category: str
    category_confidence: Optional[float]
    image_base64: str
    color: Optional[str] = None


def encode_cutout(image: Image.Image) -> str:
    """Saydam kesimi PNG olarak base64'e cevirir (alfa kanali korunur)."""
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("ascii")


wardrobe_sync_client = WardrobeSyncClient()
