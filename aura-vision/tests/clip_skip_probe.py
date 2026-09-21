"""CLIP skip probe — pytest otomatik toplamaz (test_*.py değil)."""

import pytest

from tests.clip_guard import CLIP_SKIP_REASON


def test_force_clip_skip():
    pytest.skip(CLIP_SKIP_REASON)
