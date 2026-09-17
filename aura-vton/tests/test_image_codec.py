"""image_codec — data-URI / padding / RGB decode testleri."""

from __future__ import annotations

import base64
import io

import pytest
from PIL import Image

from app.services.errors import VtonInferenceError
from app.services.image_codec import decode_base64_image, normalize_base64_payload


def _tiny_png_b64(*, pad_strip: int = 0) -> str:
    buf = io.BytesIO()
    Image.new("RGB", (8, 8), color=(12, 34, 56)).save(buf, format="PNG")
    raw = base64.b64encode(buf.getvalue()).decode("ascii")
    if pad_strip:
        raw = raw.rstrip("=")
        # leave missing padding intentionally
    return raw


def test_normalize_strips_data_uri_and_whitespace():
    bare = _tiny_png_b64()
    wrapped = f"data:image/png;base64,{bare[:20]}\n{bare[20:]}"
    assert normalize_base64_payload(wrapped) == bare


def test_normalize_pads_missing_equals():
    bare = _tiny_png_b64()
    stripped = bare.rstrip("=")
    assert len(stripped) % 4 != 0 or stripped != bare
    normalized = normalize_base64_payload(stripped)
    assert len(normalized) % 4 == 0
    assert base64.b64decode(normalized) == base64.b64decode(bare)


def test_decode_data_uri_png():
    bare = _tiny_png_b64()
    img = decode_base64_image(f"data:image/png;base64,{bare}", label="person")
    assert img is not None
    assert img.mode == "RGB"
    assert img.size == (8, 8)


def test_decode_unpadded_base64():
    bare = _tiny_png_b64()
    unpadded = bare.rstrip("=")
    img = decode_base64_image(unpadded, label="garment")
    assert img is not None
    assert img.size == (8, 8)


def test_decode_bad_payload_raises_bad_image():
    with pytest.raises(VtonInferenceError) as ei:
        decode_base64_image("data:image/png;base64,not-an-image!!!", label="garmentImageBase64")
    assert ei.value.code == "BAD_IMAGE"
    assert "garmentImageBase64" in ei.value.message


def test_decode_none_and_empty():
    assert decode_base64_image(None) is None
    assert decode_base64_image("   ") is None
