"""Serverless handler & prewarm birim testleri — HF indirme yok."""

import pytest

from app.config import settings
from app.services.output_store import OutputStore


@pytest.fixture(autouse=True)
def _force_mock(monkeypatch, tmp_path):
    monkeypatch.setattr(settings, "mock_model", True)
    monkeypatch.setattr(settings, "mock_delay_seconds", 0.0)
    monkeypatch.setattr(settings, "output_dir", str(tmp_path / "outputs"))
    monkeypatch.setattr(settings, "public_base_url", "http://127.0.0.1:8001")
    monkeypatch.setenv("AURA_VTON_MOCK_MODEL", "true")
    store = OutputStore(
        output_dir=str(tmp_path / "outputs"),
        public_base_url="http://127.0.0.1:8001",
    )
    monkeypatch.setattr("app.services.model_loader.output_store", store)


def test_handle_job_mock_completes():
    from serverless_handler import handle_job

    tiny = (
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    )
    out = handle_job(
        {
            "jobId": "srv-1",
            "personImageBase64": tiny,
            "garmentImageBase64": tiny,
            "clothType": "lower",
        }
    )
    assert out["ok"] is True
    assert out["status"] == "COMPLETED"
    assert out["jobId"] == "srv-1"
    assert out["clothType"] == "lower"
    assert out["resultImageBase64"]


def test_runpod_handler_unwraps_input():
    from serverless_handler import runpod_handler

    tiny = (
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    )
    out = runpod_handler(
        {
            "input": {
                "jobId": "srv-2",
                "personImageBase64": tiny,
                "garmentImageBase64": tiny,
            }
        }
    )
    assert out["ok"] is True
    assert out["jobId"] == "srv-2"


def test_prewarm_skips_when_disabled(monkeypatch):
    monkeypatch.setenv("AURA_VTON_PREWARM", "false")
    from scripts.prewarm_cache import main

    assert main() == 0


def test_prewarm_main_graceful_on_schp_error(monkeypatch, tmp_path):
    import scripts.prewarm_cache as prewarm

    monkeypatch.setenv("AURA_VTON_PREWARM", "true")
    monkeypatch.setenv("AURA_VTON_PREWARM_CATVTON", "false")
    monkeypatch.setenv("AURA_VTON_CACHE_DIR", str(tmp_path))
    monkeypatch.setattr(
        prewarm,
        "prewarm_schp",
        lambda cache: (_ for _ in ()).throw(RuntimeError("offline")),
    )
    assert prewarm.main() == 0
