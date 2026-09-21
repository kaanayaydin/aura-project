"""Pytest: golden-set/chroma hattını varsayılan kes. Env dokümantasyona bırakılmaz.

Karar (Adım 2): rembg varsayılan KAPALI. Golden-set bu hatta kilitli.
Üretimde rembg istenirse AURA_STUDIO_REMBG=true (uvicorn zaten böyle).
"""

from __future__ import annotations

import os

# Test modülleri settings'i import etmeden önce pin.
os.environ["AURA_STUDIO_REMBG"] = "false"
os.environ["AURA_STUDIO_PREFER_REMBG"] = "false"

import pytest

from app.core.config import settings

from tests.clip_guard import CLIP_SKIP_REASON, ClipModelMissingWarning  # noqa: F401


@pytest.fixture(autouse=True)
def _pin_chroma_cutout_for_tests():
    """Settings frozen dataclass — object.__setattr__ ile pin."""
    object.__setattr__(settings, "studio_rembg_enabled", False)
    object.__setattr__(settings, "studio_prefer_rembg", False)
    yield


def pytest_terminal_summary(terminalreporter, exitstatus, config):
    """CLIP skip'leri 'N skipped' içinde kaybolmasın."""
    skipped = terminalreporter.stats.get("skipped", []) or []
    clip_n = 0
    for rep in skipped:
        text = str(getattr(rep, "longrepr", "") or "")
        if CLIP_SKIP_REASON in text:
            clip_n += 1
    if not clip_n:
        return
    terminalreporter.write_sep(
        "!",
        f"{clip_n} test CLIP modeli bulunamadığı için atlandı",
        yellow=True,
        bold=True,
    )
    terminalreporter.write_line(
        "Kategori golden-set CLIP gerektiren vakalar bu koşuda kapsamamıyor. "
        "CI: aura-vision/models/huggingface önbelleğini restore edin (repo README)."
    )
