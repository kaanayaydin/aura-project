"""Worker S3 region — AURA_S3_REGION, varsayilan auto."""

from app.config import Settings


def test_s3_region_defaults_to_auto(monkeypatch):
    monkeypatch.delenv("AURA_S3_REGION", raising=False)
    monkeypatch.delenv("AURA_VTON_S3_REGION", raising=False)
    assert Settings().s3_region == "auto"


def test_s3_region_reads_aura_s3_region(monkeypatch):
    monkeypatch.setenv("AURA_S3_REGION", "auto")
    monkeypatch.delenv("AURA_VTON_S3_REGION", raising=False)
    assert Settings().s3_region == "auto"
