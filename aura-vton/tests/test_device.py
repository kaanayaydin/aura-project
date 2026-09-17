"""Torch cihaz cozumleme — torch yoksa skip."""

import os

import pytest

from app.services.device import configure_mps_fallback


def test_configure_mps_fallback_sets_env(monkeypatch):
    monkeypatch.delenv("PYTORCH_ENABLE_MPS_FALLBACK", raising=False)
    monkeypatch.delenv("PYTORCH_MPS_HIGH_WATERMARK_RATIO", raising=False)
    configure_mps_fallback()
    assert os.environ.get("PYTORCH_ENABLE_MPS_FALLBACK") == "1"
    assert os.environ.get("PYTORCH_MPS_HIGH_WATERMARK_RATIO") == "0.0"


def test_resolve_device_cpu_forced():
    torch = pytest.importorskip("torch")
    from app.services.device import resolve_torch_device

    assert resolve_torch_device("cpu") == "cpu"
