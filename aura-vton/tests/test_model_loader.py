"""Model loader / output / hata siniflandirma testleri — HF indirme yok."""

import base64
import io
from pathlib import Path

import pytest
from PIL import Image

from app.config import settings
from app.services.errors import VtonInferenceError, classify_exception
from app.services.model_loader import CatVtonModelLoader
from app.services.output_store import OutputStore


@pytest.fixture(autouse=True)
def _force_mock(monkeypatch, tmp_path):
    monkeypatch.setattr(settings, "mock_model", True)
    monkeypatch.setattr(settings, "mock_delay_seconds", 0.0)
    monkeypatch.setattr(settings, "output_dir", str(tmp_path / "outputs"))
    monkeypatch.setattr(settings, "public_base_url", "http://127.0.0.1:8001")


def test_mock_try_on_persists_png_and_base64(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "output_dir", str(tmp_path / "out"))
    store = OutputStore(output_dir=str(tmp_path / "out"), public_base_url="http://vton.test")
    monkeypatch.setattr("app.services.model_loader.output_store", store)

    loader = CatVtonModelLoader()
    result = loader.try_on(
        job_id="99",
        person_image_base64="aaa",
        garment_image_base64="bbb",
        cloth_type="lower",
    )

    assert result["model"] == "catvton-mock"
    assert result["clothType"] == "lower"
    assert result["resultImageBase64"]
    assert result["resultImageUri"].startswith("http://vton.test/outputs/")
    assert Path(result["filePath"]).is_file()
    assert loader.is_ready()
    assert loader.mode == "mock"


def test_output_store_writes_file(tmp_path):
    store = OutputStore(output_dir=str(tmp_path), public_base_url="http://127.0.0.1:8001")
    img = Image.new("RGB", (64, 96), color=(10, 20, 30))
    stored = store.save("job-1", img)
    assert Path(stored.file_path).exists()
    assert stored.result_image_uri.endswith("/outputs/job-1.png")
    raw = base64.b64decode(stored.result_image_base64)
    assert Image.open(io.BytesIO(raw)).size[0] <= 768


def test_classify_oom():
    err = classify_exception(RuntimeError("CUDA out of memory"))
    assert isinstance(err, VtonInferenceError)
    assert err.code == "OOM"
    assert "Bellek" in err.message or "bellek" in err.message.lower() or "CUDA" in err.message
