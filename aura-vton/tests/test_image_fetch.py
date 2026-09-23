"""image_fetch — SSRF allowlist + stream tavan."""

from __future__ import annotations

import io

import httpx
import pytest
from PIL import Image

from app.config import settings
from app.services.errors import VtonInferenceError
from app.services.image_fetch import load_image_from_url
from app.services.url_allowlist import assert_object_url_allowed, reset_allowlist_cache


@pytest.fixture(autouse=True)
def _pin_storage(monkeypatch):
    monkeypatch.setattr(settings, "s3_endpoint", "http://127.0.0.1:9000")
    monkeypatch.setattr(settings, "s3_public_base_url", "http://127.0.0.1:9000")
    monkeypatch.setattr(settings, "s3_vton_bucket", "aura-vton")
    monkeypatch.setattr(settings, "s3_wardrobe_bucket", "aura-wardrobe")
    monkeypatch.setattr(settings, "s3_avatars_bucket", "aura-avatars")
    monkeypatch.setattr(settings, "max_download_bytes", 20 * 1024 * 1024)
    reset_allowlist_cache()
    yield
    reset_allowlist_cache()


def _png_bytes(size=(8, 8)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, (12, 34, 56)).save(buf, format="PNG")
    return buf.getvalue()


def test_metadata_url_rejected_without_http():
    with pytest.raises(VtonInferenceError) as ei:
        assert_object_url_allowed("http://169.254.169.254/latest/meta-data/")
    assert ei.value.code == "SSRF"


def test_loopback_worker_rejected():
    with pytest.raises(VtonInferenceError) as ei:
        assert_object_url_allowed("http://127.0.0.1:8001/internal")
    assert ei.value.code == "SSRF"


def test_external_ip_bomb_rejected():
    with pytest.raises(VtonInferenceError) as ei:
        assert_object_url_allowed("http://203.0.113.1/bomb.jpg")
    assert ei.value.code == "SSRF"


def test_allowlisted_memory_path_ok():
    assert_object_url_allowed("http://127.0.0.1:9000/memory/aura-vton/person/abc.jpg")


def test_allowlisted_path_style_ok():
    assert_object_url_allowed("http://127.0.0.1:9000/aura-vton/person/abc.jpg")


def test_fetch_allowlisted_png(monkeypatch):
    png = _png_bytes()

    def handler(request: httpx.Request) -> httpx.Response:
        assert str(request.url) == "http://127.0.0.1:9000/aura-vton/person/abc.jpg"
        return httpx.Response(
            200,
            headers={"content-length": str(len(png))},
            content=png,
        )

    img = load_image_from_url(
        "http://127.0.0.1:9000/aura-vton/person/abc.jpg",
        transport=httpx.MockTransport(handler),
    )
    assert img.size == (8, 8)


def test_fetch_rejects_content_length_over_cap():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"content-length": str(800 * 1024 * 1024)},
            content=b"x",
        )

    with pytest.raises(VtonInferenceError) as ei:
        load_image_from_url(
            "http://127.0.0.1:9000/aura-vton/person/huge.jpg",
            transport=httpx.MockTransport(handler),
        )
    assert ei.value.code == "TOO_LARGE"


def test_fetch_cuts_stream_without_content_length(monkeypatch):
    monkeypatch.setattr(settings, "max_download_bytes", 1024)
    reset_allowlist_cache()
    blob = b"a" * 4096

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=blob)

    with pytest.raises(VtonInferenceError) as ei:
        load_image_from_url(
            "http://127.0.0.1:9000/aura-vton/person/stream.jpg",
            transport=httpx.MockTransport(handler),
        )
    assert ei.value.code == "TOO_LARGE"


def test_redirect_to_metadata_rejected():
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.host in {"127.0.0.1", "198.51.100.10"}:
            return httpx.Response(
                302,
                headers={"location": "http://169.254.169.254/latest/meta-data/"},
            )
        raise AssertionError("metadata hostuna istek gitmemeli")

    with pytest.raises(VtonInferenceError) as ei:
        load_image_from_url(
            "http://127.0.0.1:9000/aura-vton/person/abc.jpg",
            transport=httpx.MockTransport(handler),
        )
    assert ei.value.code == "SSRF"


def test_connect_uses_pinned_ip_not_later_dns(monkeypatch):
    """TOCTOU: 1-2. cozumleme CDN, 3+ metadata — TCP yalniz pin'li IP."""
    import ipaddress

    from app.services import url_allowlist

    cdn = ipaddress.ip_address("198.51.100.10")
    meta = ipaddress.ip_address("169.254.169.254")
    calls = {"n": 0}

    def fake_resolve(host: str):
        if host == "198.51.100.10":
            return [cdn]
        if host != "files.aura.test":
            raise OSError(host)
        calls["n"] += 1
        if calls["n"] <= 2:
            return [cdn]
        return [meta]

    monkeypatch.setattr(url_allowlist, "_resolve", fake_resolve)
    monkeypatch.setattr(settings, "s3_endpoint", "http://files.aura.test:9000")
    monkeypatch.setattr(settings, "s3_public_base_url", "http://files.aura.test:9000")
    reset_allowlist_cache()

    seen: list[str] = []
    png = _png_bytes()

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request.url.host)
        ips = fake_resolve(request.url.host)
        if meta in ips:
            raise AssertionError("metadata sunucusuna TCP")
        assert request.headers.get("host") == "files.aura.test:9000"
        return httpx.Response(200, headers={"content-length": str(len(png))}, content=png)

    img = load_image_from_url(
        "http://files.aura.test:9000/aura-vton/person/abc.jpg",
        transport=httpx.MockTransport(handler),
    )
    assert img.size == (8, 8)
    assert seen == ["198.51.100.10"]
    assert calls["n"] == 2

