"""Garment studio / normalize birim testleri (rembg zorunlu degil)."""

from __future__ import annotations

import base64
import io

import numpy as np
from PIL import Image

from app.services.garment_normalizer import garment_normalizer
from app.services.garment_studio import (
    alpha_bbox,
    canvas_size_for_aspect,
    chroma_cutout,
    compose_studio,
    has_meaningful_alpha,
    parse_hex_color,
)


def _rgba_shirt(size=(200, 240)) -> Image.Image:
    """Saydam zemin + ortada kirmizi dikdortgen (tişört proxy)."""
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    pixels = img.load()
    for y in range(40, 200):
        for x in range(50, 150):
            pixels[x, y] = (200, 40, 40, 255)
    return img


def test_parse_hex_color():
    assert parse_hex_color("#FFFFFF") == (255, 255, 255)
    assert parse_hex_color("F8F9FA") == (248, 249, 250)


def test_canvas_size_3_4_and_1_1():
    assert canvas_size_for_aspect("3:4", 1024) == (768, 1024)
    assert canvas_size_for_aspect("1:1", 800) == (800, 800)


def test_alpha_bbox_and_meaningful_alpha():
    shirt = _rgba_shirt()
    assert has_meaningful_alpha(shirt)
    box = alpha_bbox(shirt)
    assert box is not None
    assert box[0] <= 50 and box[2] >= 150


def test_compose_studio_centers_on_soft_bg():
    shirt = _rgba_shirt()
    framed = compose_studio(
        shirt,
        aspect="3:4",
        long_side=400,
        background=(248, 249, 250),
        drop_shadow=False,
        margin_ratio=0.1,
    )
    assert framed.size == (300, 400)
    assert framed.mode == "RGB"
    # Koseler stüdyo grisi
    assert framed.getpixel((2, 2)) == (248, 249, 250)
    # Ortada kirmizi tonu kalmali
    cx, cy = framed.size[0] // 2, framed.size[1] // 2
    r, g, b = framed.getpixel((cx, cy))
    assert r > 150 and g < 100


def test_chroma_cutout_removes_corner_bg():
    rgb = Image.new("RGB", (120, 120), (240, 240, 240))
    for y in range(30, 90):
        for x in range(30, 90):
            rgb.putpixel((x, y), (20, 120, 200))
    cut = chroma_cutout(rgb, color_distance=30)
    assert cut.mode == "RGBA"
    assert cut.getpixel((5, 5))[3] < 16
    assert cut.getpixel((60, 60))[3] > 200


def test_normalizer_from_rgba_cutout():
    shirt = _rgba_shirt()
    buf = io.BytesIO()
    shirt.save(buf, format="PNG")
    # force_rembg=False → mevcut anlamli alfa kullanilir
    result = garment_normalizer.normalize(
        buf.getvalue(), aspect="3:4", drop_shadow=True, force_rembg=False
    )
    assert result.cutout_source == "alpha"
    assert result.aspect == "3:4"
    assert result.png_bytes.startswith(b"\x89PNG\r\n\x1a\n")
    assert len(result.png_bytes) > 64
    b64, _ = garment_normalizer.normalize_to_base64(
        buf.getvalue(), force_rembg=False
    )
    assert base64.b64decode(b64)[:8] == b"\x89PNG\r\n\x1a\n"


def test_ensure_foreground_polarity_flips_inverted_mask():
    from app.services.garment_studio import ensure_foreground_polarity, refine_garment_alpha

    # Ters maske: kenarlar opak (carsaf), merkez seffaf (tişört deligi)
    img = Image.new("RGBA", (100, 120), (200, 200, 200, 255))
    pixels = img.load()
    for y in range(25, 95):
        for x in range(20, 80):
            pixels[x, y] = (255, 255, 255, 0)  # merkez delik
    fixed = refine_garment_alpha(ensure_foreground_polarity(img))
    alpha = np.asarray(fixed.split()[-1])
    assert alpha[10, 10] < 64  # kenar seffaf olmali
    assert alpha[60, 50] > 180  # merkez opak (kiyafet)


def test_ensure_foreground_polarity_keeps_correct_mask():
    from app.services.garment_studio import refine_garment_alpha

    img = Image.new("RGBA", (100, 120), (0, 0, 0, 0))
    pixels = img.load()
    for y in range(25, 95):
        for x in range(20, 80):
            pixels[x, y] = (200, 40, 40, 255)
    fixed = refine_garment_alpha(img)
    alpha = np.asarray(fixed.split()[-1])
    assert alpha[10, 10] < 64
    assert alpha[60, 50] > 180


def test_refine_low_confidence_full_opaque_sheet_clears_border():
    """Neredeyse tamamen opak (carsaf+tişört) → agresif refine kenari siler."""
    from app.services.garment_studio import is_low_confidence_mask, refine_garment_alpha

    img = Image.new("RGBA", (120, 140), (240, 240, 240, 255))
    # Merkezde biraz daha "dolu" tişört sekli (ayni renk — dusuk kontrast)
    pixels = img.load()
    for y in range(30, 110):
        for x in range(25, 95):
            pixels[x, y] = (245, 245, 245, 255)
    alpha_before = np.asarray(img.split()[-1])
    assert is_low_confidence_mask(alpha_before)
    fixed = refine_garment_alpha(img, force_aggressive=True)
    alpha = np.asarray(fixed.split()[-1])
    assert alpha[2, 2] < 16  # kenar seffaf
    assert (alpha > 127).mean() < 0.85  # tam dolu degil
    assert (alpha > 127).mean() > 0.05  # tamamen bos degil


def test_normalizer_rgb_fallback_produces_studio():
    rgb = Image.new("RGB", (160, 200), (230, 230, 230))
    for y in range(40, 160):
        for x in range(40, 120):
            rgb.putpixel((x, y), (30, 30, 30))
    buf = io.BytesIO()
    rgb.save(buf, format="JPEG", quality=90)
    result = garment_normalizer.normalize(buf.getvalue(), aspect="1:1", long_side=512)
    assert result.aspect == "1:1"
    assert result.width == result.height == 512
    assert result.cutout_source in ("rembg", "chroma")


def test_lcc_empty_mask_no_crash():
    from app.services.garment_studio import keep_largest_opaque_component

    empty = np.zeros((40, 30), dtype=np.uint8)
    out = keep_largest_opaque_component(empty)
    assert out.shape == (40, 30)
    assert out.dtype == np.uint8
    assert out.sum() == 0


def test_as_rgba_from_ndarray_and_bytes():
    from app.services.garment_normalizer import _as_rgba_image

    arr = np.zeros((20, 10, 4), dtype=np.uint8)
    arr[5:15, 2:8] = (200, 40, 40, 255)
    img = _as_rgba_image(arr)
    assert img.mode == "RGBA"
    assert img.size == (10, 20)  # W,H from ndarray H,W,C

    buf = io.BytesIO()
    img.save(buf, format="PNG")
    img2 = _as_rgba_image(buf.getvalue())
    assert img2.mode == "RGBA"
    assert img2.size == (10, 20)


def test_normalize_endpoint_logs_and_returns_200():
    from fastapi.testclient import TestClient
    from main import app

    rgb = Image.new("RGB", (80, 100), (220, 220, 220))
    for y in range(20, 80):
        for x in range(15, 65):
            rgb.putpixel((x, y), (40, 40, 200))
    buf = io.BytesIO()
    rgb.save(buf, format="JPEG", quality=90)
    client = TestClient(app)
    resp = client.post(
        "/api/v1/vision/normalize-garment",
        files={"file": ("g.jpg", buf.getvalue(), "image/jpeg")},
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["status"] == "success"
    assert body["cutout_source"] in ("rembg", "chroma", "alpha")
    assert len(body["image_base64"]) > 32
