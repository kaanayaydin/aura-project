"""Pipeline surum bilgisi — cache/stale kod teshisi icin."""

from __future__ import annotations

import subprocess
from datetime import datetime, timezone
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parents[3]
_STARTED_AT = datetime.now(timezone.utc).isoformat()
_COMMIT_CACHE: str | None = None


def git_commit_short() -> str:
    """git rev-parse --short HEAD; git yoksa unknown."""
    global _COMMIT_CACHE
    if _COMMIT_CACHE is not None:
        return _COMMIT_CACHE
    try:
        out = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=str(_REPO_ROOT),
            timeout=3,
            stderr=subprocess.DEVNULL,
        )
        _COMMIT_CACHE = out.decode("utf-8").strip() or "unknown"
    except Exception:
        _COMMIT_CACHE = "unknown"
    return _COMMIT_CACHE


def started_at() -> str:
    return _STARTED_AT


def version_payload() -> dict[str, str]:
    return {
        "commit": git_commit_short(),
        "started_at": started_at(),
        "service": "aura-vision",
    }
