"""CLIP yokken kategori golden'ı sessiz skip etmesin."""

from __future__ import annotations

import warnings

import pytest

CLIP_SKIP_REASON = "CLIP modeli bulunamadığı için atlandı"


class ClipModelMissingWarning(UserWarning):
    """HF önbelleğinde CLIP yok — paket yeşil kalır ama kapsam sıfırdır."""


def load_clip_or_warn_skip():
    """CLIP yükle; yoksa uyarı + skip (FAIL değil)."""
    from app.services.style_classifier import style_classifier

    try:
        style_classifier.load()
    except Exception as exc:  # noqa: BLE001
        warnings.warn(
            f"{CLIP_SKIP_REASON} ({type(exc).__name__}: {exc}). "
            "Kategori golden (CLIP gereken) bu koşuda kapsam sağlamaz. "
            "Önbellek: aura-vision/models/huggingface — README CI notu.",
            ClipModelMissingWarning,
            stacklevel=2,
        )
        pytest.skip(f"{CLIP_SKIP_REASON}: {exc}")
    return style_classifier
