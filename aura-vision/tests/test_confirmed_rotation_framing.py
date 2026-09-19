"""Onaylı 90/270 yükleme: rotasyon korunur, 3:4 framing uygulanır (B1).

skip_orientation=true: rembg + askı temizliği + deskew/cardinal/ensemble +
catalog press atlanır; yalnız compose_studio (3:4) uygulanır.
"""

from __future__ import annotations

import io
import os
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from app.core.config import settings
from app.services.garment_normalizer import GarmentNormalizer, garment_normalizer
from app.services.garment_polish import detect_neckline_edge, rotate_neckline_to_north
from app.services.garment_studio import canvas_size_for_aspect

ROOT = Path(__file__).resolve().parent / "golden_set" / "orientation"
LONG_SIDE = 256
PORTRAIT = canvas_size_for_aspect("3:4", LONG_SIDE)

# ground_truth_neck → onaylı CCW (Flutter'ın uyguladığı ile aynı matematik)
_USER_CCW = {"left": 270, "right": 90, "bottom": 180, "top": 0}

_CASES = [
    ("real_duz_r90", "images/real_duz_r90.png", "left"),
    ("real_duz_r270", "images/real_duz_r270.png", "right"),
    ("synthetic_uneck_r90", "images/synthetic_uneck_r90.png", "left"),
    ("synthetic_uneck_r270", "images/synthetic_uneck_r270.png", "right"),
]


def _png_bytes(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def _rotate_ccw(img: Image.Image, deg: int) -> Image.Image:
    deg = int(deg) % 360
    if deg == 0:
        return img
    op = {
        90: Image.Transpose.ROTATE_90,
        180: Image.Transpose.ROTATE_180,
        270: Image.Transpose.ROTATE_270,
    }[deg]
    return img.transpose(op)


def _rgba_tshirt_neckline(size=(180, 240)) -> Image.Image:
    w, h = size
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    px = img.load()
    for y in range(70, 225):
        for x in range(48, 132):
            px[x, y] = (200, 60, 60, 255)
    for y in range(52, 78):
        for x in range(22, 158):
            px[x, y] = (200, 60, 60, 255)
    cx, cy, r = w // 2, 55, 24
    for y in range(35, 82):
        for x in range(cx - r - 2, cx + r + 3):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and y < cy + 10:
                px[x, y] = (0, 0, 0, 0)
    for y in range(218, 228):
        for x in range(50, 130):
            px[x, y] = (200, 60, 60, 255)
    return img


def test_skip_orientation_keeps_user_90cw_and_reframes_3_4():
    """B1 regresyon: yaka sağda (onaylı 90 CW) framing ile ezilmez, kanvas 3:4."""
    upright = _rgba_tshirt_neckline()
    sideways = upright.rotate(270, expand=True, fillcolor=(0, 0, 0, 0))
    assert detect_neckline_edge(np.asarray(sideways.split()[-1])) == "right"

    skipped = garment_normalizer.normalize(
        _png_bytes(sideways),
        force_rembg=False,
        drop_shadow=False,
        long_side=LONG_SIDE,
        skip_orientation=True,
    )

    assert (skipped.width, skipped.height) == PORTRAIT
    assert skipped.width < skipped.height
    assert skipped.rotation_deg_applied == 0
    assert skipped.rotation_method == "skipped_already_normalized"
    assert skipped.cutout_rgba is not None
    assert detect_neckline_edge(np.asarray(skipped.cutout_rgba.split()[-1])) == "right"

    # Ensemble olmadan bile yaka→kuzey 90 uygulardı; skip bunu yapmadı.
    _fixed, deg, edge, method = rotate_neckline_to_north(sideways)
    assert edge == "right" and deg == 90
    assert "ROTATE_90" in method


@pytest.mark.parametrize("case_id,rel,neck", _CASES, ids=[c[0] for c in _CASES])
def test_confirmed_rotation_golden_skip_orientation_3_4(case_id, rel, neck):
    raw = (ROOT / rel).read_bytes()
    first = garment_normalizer.normalize(
        raw, force_rembg=False, drop_shadow=False, long_side=LONG_SIDE
    )
    assert (first.width, first.height) == PORTRAIT, case_id
    confirmed = _rotate_ccw(first.image, _USER_CCW[neck])
    assert confirmed.size[0] > confirmed.size[1], case_id  # 4:3 yatay (B1 hali)

    framed = garment_normalizer.normalize(
        _png_bytes(confirmed),
        force_rembg=False,
        drop_shadow=False,
        long_side=LONG_SIDE,
        skip_orientation=True,
    )
    assert (framed.width, framed.height) == PORTRAIT, case_id
    assert framed.width < framed.height, case_id
    assert framed.rotation_deg_applied == 0, case_id
    assert framed.rotation_method == "skipped_already_normalized", case_id
    assert framed.cutout_rgba is not None, case_id
    # Skor "top" chroma sonrası kırılgan; 90/270 ezilirse bbox yatay olur.
    alpha = np.asarray(framed.cutout_rgba.split()[-1]) > 127
    ys, xs = np.where(alpha)
    assert ys.size > 0, case_id
    assert (ys.max() - ys.min()) >= (xs.max() - xs.min()) * 0.85, (
        case_id,
        ys.max() - ys.min(),
        xs.max() - xs.min(),
    )


def test_normalize_endpoint_skip_orientation_form():
    from fastapi.testclient import TestClient
    from main import app

    shirt = _rgba_tshirt_neckline()
    landscape = shirt.rotate(270, expand=True, fillcolor=(0, 0, 0, 0)).convert("RGB")
    buf = io.BytesIO()
    landscape.save(buf, format="PNG")
    client = TestClient(app)
    resp = client.post(
        "/api/v1/vision/normalize-garment",
        files={"file": ("g.png", buf.getvalue(), "image/png")},
        data={"skip_orientation": "true", "drop_shadow": "false"},
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["rotation_method"] == "skipped_already_normalized"
    assert body["rotation_deg_applied"] == 0
    assert body["width"] < body["height"]
    assert body["aspect"] == "3:4"


def _rgb_studio_png() -> bytes:
    """Flutter onaylı yükleme gibi: RGB stüdyo, alfa yok."""
    img = Image.new("RGB", (256, 192), (248, 249, 250))
    px = img.load()
    for y in range(40, 152):
        for x in range(30, 226):
            px[x, y] = (200, 40, 40)
    return _png_bytes(img)


def _count_try_rembg(monkeypatch):
    calls = {"n": 0}

    def fake(rgb):
        calls["n"] += 1
        out = Image.new("RGBA", rgb.size, (0, 0, 0, 0))
        box = (rgb.size[0] // 4, rgb.size[1] // 4, 3 * rgb.size[0] // 4, 3 * rgb.size[1] // 4)
        crop = out.crop(box)
        crop.paste((180, 50, 50, 255), (0, 0, crop.size[0], crop.size[1]))
        out.paste(crop, box)
        return out

    monkeypatch.setattr(GarmentNormalizer, "_try_rembg", staticmethod(fake))
    return calls


def test_skip_orientation_never_calls_rembg_when_production_flags_on(monkeypatch):
    """F2: AURA_STUDIO_REMBG=true + PREFER=true + skip_orientation → rembg 0.

    Eski kod prefer=False ile 3. dalı (enabled and not prefer) açık bırakıyordu.
    Mock, canlı koşudaki _try_rembg sayacını simüle eder.
    """
    object.__setattr__(settings, "studio_rembg_enabled", True)
    object.__setattr__(settings, "studio_prefer_rembg", True)
    calls = _count_try_rembg(monkeypatch)
    result = garment_normalizer.normalize(
        _rgb_studio_png(),
        skip_orientation=True,
        drop_shadow=False,
        long_side=LONG_SIDE,
    )
    assert calls["n"] == 0
    assert result.cutout_source == "chroma"
    assert result.rotation_method == "skipped_already_normalized"
    assert (result.width, result.height) == PORTRAIT


def test_prefer_false_fallback_rembg_runs_without_skip(monkeypatch):
    """3. dal niyeti: rembg açık, prefer kapalı, skip yok → alfa yoksa rembg."""
    object.__setattr__(settings, "studio_rembg_enabled", True)
    object.__setattr__(settings, "studio_prefer_rembg", False)
    calls = _count_try_rembg(monkeypatch)
    result = garment_normalizer.normalize(
        _rgb_studio_png(),
        skip_orientation=False,
        drop_shadow=False,
        long_side=LONG_SIDE,
    )
    assert calls["n"] == 1
    assert result.cutout_source == "rembg"


def test_prefer_false_fallback_rembg_skipped_with_skip_orientation(monkeypatch):
    """F2 regresyon: 3. dal skip_orientation iken de kapalı."""
    object.__setattr__(settings, "studio_rembg_enabled", True)
    object.__setattr__(settings, "studio_prefer_rembg", False)
    calls = _count_try_rembg(monkeypatch)
    result = garment_normalizer.normalize(
        _rgb_studio_png(),
        skip_orientation=True,
        drop_shadow=False,
        long_side=LONG_SIDE,
    )
    assert calls["n"] == 0
    assert result.cutout_source != "rembg"


def test_skip_orientation_prefers_existing_alpha_not_rembg(monkeypatch):
    object.__setattr__(settings, "studio_rembg_enabled", True)
    object.__setattr__(settings, "studio_prefer_rembg", True)
    calls = _count_try_rembg(monkeypatch)
    result = garment_normalizer.normalize(
        _png_bytes(_rgba_tshirt_neckline()),
        skip_orientation=True,
        drop_shadow=False,
        long_side=LONG_SIDE,
    )
    assert calls["n"] == 0
    assert result.cutout_source == "alpha"


@pytest.mark.skipif(
    os.environ.get("AURA_STUDIO_REMBG_LIVE") != "1",
    reason="Canlı rembg: AURA_STUDIO_REMBG_LIVE=1 (model indirir; CI varsayılan kapalı)",
)
@pytest.mark.parametrize(
    "case_id,rel",
    [
        ("real_duz_r90", "images/real_duz_r90.png"),
        ("real_duz_r270", "images/real_duz_r270.png"),
        ("synthetic_uneck_r270", "images/synthetic_uneck_r270.png"),
    ],
    ids=["real_duz_r90", "real_duz_r270", "synthetic_uneck_r270"],
)
def test_live_skip_orientation_does_not_invoke_real_rembg(case_id, rel, monkeypatch):
    """Canlı rembg + üretim bayrakları + skip_orientation; _try_rembg sarmalayıcı sayar."""
    object.__setattr__(settings, "studio_rembg_enabled", True)
    object.__setattr__(settings, "studio_prefer_rembg", True)
    orig = GarmentNormalizer._try_rembg
    calls = {"n": 0}

    def wrapped(rgb):
        calls["n"] += 1
        return orig(rgb)

    monkeypatch.setattr(GarmentNormalizer, "_try_rembg", staticmethod(wrapped))
    raw = (ROOT / rel).read_bytes()
    first = garment_normalizer.normalize(
        raw, skip_orientation=False, drop_shadow=False, long_side=LONG_SIDE
    )
    rembg_on_full = calls["n"]
    assert rembg_on_full >= 1, (
        f"{case_id}: tam normalize _try_rembg cagirmadi (canli rembg kirik?)"
    )
    calls["n"] = 0
    confirmed = _rotate_ccw(first.image, _USER_CCW["left" if "r90" in case_id else "right"])
    framed = garment_normalizer.normalize(
        _png_bytes(confirmed),
        skip_orientation=True,
        drop_shadow=False,
        long_side=LONG_SIDE,
    )
    assert framed.rotation_method == "skipped_already_normalized", case_id
    assert (framed.width, framed.height) == PORTRAIT, case_id
    assert calls["n"] == 0, (
        f"{case_id}: skip_orientation iken gerçek rembg {calls['n']} kez çağrıldı "
        f"(tam normalize {rembg_on_full} kez)"
    )
