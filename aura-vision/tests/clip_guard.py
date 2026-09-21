"""CLIP yokken kategori golden'ı sessiz skip etmesin."""

from __future__ import annotations

import warnings

import pytest

CLIP_SKIP_REASON = "CLIP modeli bulunamadığı için atlandı"
CLIP_BANNER_FMT = "{n} test CLIP modeli bulunamadığı için atlandı"


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


def clip_skip_count(skipped_reports) -> int:
    n = 0
    for rep in skipped_reports or []:
        text = str(getattr(rep, "longrepr", "") or "")
        if CLIP_SKIP_REASON in text:
            n += 1
    return n


def write_clip_skip_summary(terminalreporter, skipped_reports) -> int:
    """Oturum sonu sarı bant. 0 ise yazmaz. Dönüş: skip sayısı."""
    n = clip_skip_count(skipped_reports)
    if not n:
        return 0
    terminalreporter.write_sep(
        "!",
        CLIP_BANNER_FMT.format(n=n),
        yellow=True,
        bold=True,
    )
    terminalreporter.write_line(
        "Kategori golden-set CLIP gerektiren vakalar bu koşuda kapsamamıyor. "
        "CI: aura-vision/models/huggingface önbelleğini restore edin (repo README)."
    )
    return n
