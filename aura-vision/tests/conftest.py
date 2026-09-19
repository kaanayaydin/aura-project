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


@pytest.fixture(autouse=True)
def _pin_chroma_cutout_for_tests():
    """Settings frozen dataclass — object.__setattr__ ile pin."""
    object.__setattr__(settings, "studio_rembg_enabled", False)
    object.__setattr__(settings, "studio_prefer_rembg", False)
    yield
