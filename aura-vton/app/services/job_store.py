from __future__ import annotations

import json
import logging
from typing import Any

import redis

from app.config import settings

logger = logging.getLogger("aura.vton.job_store")

STATUS_QUEUED = "QUEUED"
STATUS_PROCESSING = "PROCESSING"
STATUS_COMPLETED = "COMPLETED"
STATUS_FAILED = "FAILED"

_KEY_PREFIX = "aura:vton:job:"


class JobStore:
    """VTON is durumunu Redis'te tutar (Java status poll icin kaynak)."""

    def __init__(self, redis_url: str | None = None) -> None:
        self._redis = redis.Redis.from_url(
            redis_url or settings.redis_url,
            decode_responses=True,
        )

    def _key(self, job_id: str) -> str:
        return f"{_KEY_PREFIX}{job_id}"

    def put(self, job_id: str, payload: dict[str, Any]) -> None:
        self._redis.set(self._key(job_id), json.dumps(payload))

    def get(self, job_id: str) -> dict[str, Any] | None:
        raw = self._redis.get(self._key(job_id))
        if raw is None:
            return None
        return json.loads(raw)

    def update(self, job_id: str, **fields: Any) -> dict[str, Any]:
        current = self.get(job_id) or {"jobId": job_id}
        current.update(fields)
        self.put(job_id, current)
        return current


job_store = JobStore()
