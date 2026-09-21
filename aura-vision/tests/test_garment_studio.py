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


def test_refine_empty_mask_does_not_invent_ellipse():
    """Boş maske silüet uydurmaz (eski merkez-elips → CLIP dress FP)."""
    from app.services.garment_studio import chroma_cutout, refine_garment_alpha

    empty = Image.new("RGBA", (80, 100), (12, 12, 12, 0))
    out = refine_garment_alpha(empty, force_aggressive=True)
    assert int((np.asarray(out.split()[-1]) > 127).sum()) == 0

    wall = Image.new("RGB", (80, 100), (214, 206, 196))
    cut = chroma_cutout(wall)
    assert int((np.asarray(cut.split()[-1]) > 127).sum()) == 0


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


def _rgba_shirt_with_hanger(size=(160, 220)) -> Image.Image:
    """Tişört + üstte ince askı çubuğu (yaka dışına taşan)."""
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    px = img.load()
    # Gövde
    for y in range(55, 200):
        for x in range(40, 120):
            px[x, y] = (180, 50, 50, 255)
    # Omuz genişliği
    for y in range(48, 55):
        for x in range(35, 125):
            px[x, y] = (180, 50, 50, 255)
    # İnce askı kancası (üstte dar)
    for y in range(8, 48):
        for x in range(78, 82):
            px[x, y] = (90, 90, 90, 255)
    for x in range(60, 100):
        px[x, 10] = (90, 90, 90, 255)
    return img


def test_remove_hanger_clears_thin_top_protrusion():
    from app.services.garment_polish import remove_hanger_artifacts

    img = _rgba_shirt_with_hanger()
    assert np.asarray(img.split()[-1])[12, 80] > 200  # askı var
    cleaned = remove_hanger_artifacts(img)
    alpha = np.asarray(cleaned.split()[-1])
    assert alpha[12, 80] < 64  # askı silindi
    assert alpha[100, 80] > 200  # gövde kaldı


def test_deskew_reduces_tilt_toward_vertical():
    from app.services.garment_polish import deskew_garment, estimate_deskew_angle_deg

    base = Image.new("RGBA", (200, 260), (0, 0, 0, 0))
    px = base.load()
    for y in range(40, 220):
        for x in range(70, 130):
            px[x, y] = (40, 120, 200, 255)
    # 35° çapraz — axis-snap ile düzelmeli (≤45°)
    tilted = base.rotate(35, expand=True, fillcolor=(0, 0, 0, 0))
    before = abs(estimate_deskew_angle_deg(np.asarray(tilted.split()[-1])))
    upright, applied = deskew_garment(tilted, min_abs_deg=0.5, max_abs_deg=45.0)
    after = abs(estimate_deskew_angle_deg(np.asarray(upright.split()[-1])))
    assert before > 15.0
    assert abs(applied) > 15.0
    assert after < 8.0


def _rgba_tshirt_neckline(size=(180, 240)) -> Image.Image:
    """Omuzlu tişört + üstte U-yaka + düz geniş etek."""
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


def test_neckline_east_uses_cv2_rotate_90_ccw():
    """Sağ kenar yakası → ROTATE_90_COUNTERCLOCKWISE; skorlar loglanır."""
    from app.services.garment_polish import (
        align_garment_upright,
        compute_orientation_scores,
        detect_neckline_edge,
        rotate_neckline_to_north,
    )

    shirt = _rgba_tshirt_neckline()
    sideways = shirt.rotate(270, expand=True, fillcolor=(0, 0, 0, 0))
    scores = compute_orientation_scores(np.asarray(sideways.split()[-1]))
    assert scores["best"] == "right"
    assert scores["combined"]["right"] >= scores["combined"]["top"]

    fixed, deg, edge, method = rotate_neckline_to_north(sideways)
    assert edge == "right" and deg == 90
    assert method == "cv2.ROTATE_90_COUNTERCLOCKWISE"
    assert detect_neckline_edge(np.asarray(fixed.split()[-1])) == "top"
    # Portre bbox
    a = np.asarray(fixed.split()[-1]) > 127
    ys, xs = np.where(a)
    assert (ys.max() - ys.min()) >= (xs.max() - xs.min()) * 0.9

    out = align_garment_upright(sideways)
    assert detect_neckline_edge(np.asarray(out.split()[-1])) == "top"


def test_apply_cardinal_rotation_mapping():
    from app.services.garment_polish import apply_cardinal_rotation

    shirt = _rgba_tshirt_neckline()
    out, deg, method = apply_cardinal_rotation(shirt, "right")
    assert deg == 90 and method == "cv2.ROTATE_90_COUNTERCLOCKWISE"
    assert out.size[0] == shirt.size[1]
    out2, deg2, method2 = apply_cardinal_rotation(shirt, "top")
    assert deg2 == 0 and method2 == "none" and out2.size == shirt.size


def test_low_confidence_skips_cardinal_rotation(monkeypatch):
    """Yakin cagri: deskew koru, 90/270 uygulama."""
    from app.services import garment_polish as gp

    shirt = _rgba_tshirt_neckline()
    sideways = shirt.rotate(270, expand=True, fillcolor=(0, 0, 0, 0))

    def fake_scores(_alpha):
        return {
            "best": "right",
            "low_confidence": True,
            "score_gap": 0.01,
            "combined": {"top": 0.2, "right": 0.21, "bottom": 0.19, "left": 0.18},
        }

    monkeypatch.setattr(gp, "compute_orientation_scores", fake_scores)
    out, deg, edge, method = gp.rotate_neckline_to_north(sideways)
    assert edge == "right"
    assert deg == 0
    assert method == "skipped_low_confidence"
    assert out.size == sideways.size

    forced, fdeg, fedge, fmethod = gp.rotate_neckline_to_north(
        sideways, apply_if_low_confidence=True
    )
    assert fedge == "right" and fdeg == 90
    assert fmethod == "cv2.ROTATE_90_COUNTERCLOCKWISE"
    assert forced.size != sideways.size or forced.size[0] != sideways.size[0]


def test_medium_confidence_skips_pending_confirmation():
    """Geçici köprü: medium otomatik 90/270 uygulamaz; etiket low olmaz."""
    from app.services.garment_polish import rotate_neckline_to_north

    shirt = _rgba_tshirt_neckline()
    sideways = shirt.rotate(270, expand=True, fillcolor=(0, 0, 0, 0))
    scores = {
        "best": "right",
        "low_confidence": False,
        "ensemble_confidence": "medium",
        "score_gap": 0.2,
        "combined": {"top": 0.2, "right": 0.5, "bottom": 0.19, "left": 0.18},
    }
    out, deg, edge, method = rotate_neckline_to_north(sideways, scores=scores)
    assert edge == "right"
    assert deg == 0
    assert method == "skipped_pending_confirmation"
    assert out.size == sideways.size


def test_high_confidence_still_applies_cardinal_rotation():
    """high davranışına dokunma: 90 hâlâ uygulanır."""
    from app.services.garment_polish import rotate_neckline_to_north

    shirt = _rgba_tshirt_neckline()
    sideways = shirt.rotate(270, expand=True, fillcolor=(0, 0, 0, 0))
    scores = {
        "best": "right",
        "low_confidence": False,
        "ensemble_confidence": "high",
        "score_gap": 0.4,
        "combined": {"top": 0.2, "right": 0.8, "bottom": 0.1, "left": 0.1},
    }
    out, deg, edge, method = rotate_neckline_to_north(sideways, scores=scores)
    assert edge == "right"
    assert deg == 90
    assert method == "cv2.ROTATE_90_COUNTERCLOCKWISE"
    assert out.size != sideways.size or out.size[0] != sideways.size[0]


def test_continuous_deskew_and_polarity_180():
    from app.services.garment_polish import (
        align_garment_upright,
        detect_neckline_edge,
        deskew_garment,
        neckline_upright_score,
        should_flip_180,
    )

    shirt = _rgba_tshirt_neckline()
    alpha0 = np.asarray(shirt.split()[-1])
    assert detect_neckline_edge(alpha0) == "top"
    assert not should_flip_180(alpha0)

    tilted = shirt.rotate(35, expand=True, fillcolor=(0, 0, 0, 0))
    _deskewed, ang = deskew_garment(tilted, max_abs_deg=45.0)
    assert abs(ang) > 15.0
    out = align_garment_upright(tilted)
    assert detect_neckline_edge(np.asarray(out.split()[-1])) == "top"
    assert neckline_upright_score(np.asarray(out.split()[-1]) > 127) > 0.0

    upside = shirt.rotate(180, expand=True, fillcolor=(0, 0, 0, 0))
    assert detect_neckline_edge(np.asarray(upside.split()[-1])) == "bottom"
    assert should_flip_180(np.asarray(upside.split()[-1]))
    fixed = align_garment_upright(upside)
    assert detect_neckline_edge(np.asarray(fixed.split()[-1])) == "top"


def test_collar_preferred_over_hem_notch():
    from app.services.garment_polish import align_garment_upright, detect_neckline_edge

    shirt = _rgba_tshirt_neckline()
    px = shirt.load()
    w, h = shirt.size
    for y in range(h - 25, h - 5):
        for x in range(w // 2 - 15, w // 2 + 15):
            if (x - w // 2) ** 2 + (y - (h - 8)) ** 2 < 120:
                px[x, y] = (0, 0, 0, 0)
    assert detect_neckline_edge(np.asarray(shirt.split()[-1])) == "top"
    out = align_garment_upright(shirt)
    assert detect_neckline_edge(np.asarray(out.split()[-1])) == "top"


def test_align_garment_upright_handles_diagonal():
    from app.services.garment_polish import align_garment_upright, detect_neckline_edge

    shirt = _rgba_tshirt_neckline()
    diagonal = shirt.rotate(35, expand=True, fillcolor=(0, 0, 0, 0))
    out = align_garment_upright(diagonal)
    assert detect_neckline_edge(np.asarray(out.split()[-1])) == "top"


def test_catalog_press_preserves_alpha_and_softens():
    from app.services.garment_polish import catalog_press
    import cv2

    img = Image.new("RGBA", (80, 100), (0, 0, 0, 0))
    px = img.load()
    for y in range(20, 80):
        for x in range(20, 60):
            # Yapay "kırışıklık" şeritleri
            v = 160 + (18 if (x + y) % 6 < 3 else -18)
            px[x, y] = (v, v, v, 255)
    pressed = catalog_press(img, diameter=9, strong=True, detail_keep=0.2)
    assert pressed.mode == "RGBA"
    a0 = np.asarray(img.split()[-1])
    a1 = np.asarray(pressed.split()[-1])
    assert np.array_equal(a0 > 127, a1 > 127)
    # Yüksek frekans enerjisi (Laplacian) düşmeli
    g0 = cv2.cvtColor(np.asarray(img.convert("RGB")), cv2.COLOR_RGB2GRAY)
    g1 = cv2.cvtColor(np.asarray(pressed.convert("RGB")), cv2.COLOR_RGB2GRAY)
    m = a0 > 127
    e0 = float(cv2.Laplacian(g0, cv2.CV_32F)[m].var())
    e1 = float(cv2.Laplacian(g1, cv2.CV_32F)[m].var())
    assert e1 < e0 * 0.75


def test_polish_chain_on_cutout():
    from app.services.garment_polish import polish_studio_cutout

    img = _rgba_shirt_with_hanger().rotate(12, expand=True, fillcolor=(0, 0, 0, 0))
    out = polish_studio_cutout(img)
    assert out.mode == "RGBA"
    alpha = np.asarray(out.split()[-1])
    assert (alpha > 127).sum() > 100


def test_version_endpoint():
    from fastapi.testclient import TestClient

    from main import app

    client = TestClient(app)
    r = client.get("/version")
    assert r.status_code == 200
    body = r.json()
    assert "commit" in body and body["commit"]
    assert "started_at" in body and body["started_at"]


def test_orientation_debug_writes_artifacts(tmp_path, monkeypatch):
    """debug=True her katman icin kanit dosyasi yazar; skor mantigini degistirmez."""
    import json

    from app.services import orientation_debug as od

    monkeypatch.setattr(od, "debug_root", lambda: tmp_path)

    shirt = _rgba_tshirt_neckline()
    buf = io.BytesIO()
    shirt.save(buf, format="PNG")
    result = garment_normalizer.normalize(
        buf.getvalue(),
        force_rembg=False,
        debug=True,
        job_id="dbgstep1",
        drop_shadow=False,
        long_side=256,
    )
    out = tmp_path / "dbgstep1"
    for name in (
        "1_mask_raw.png",
        "2_mask_deskewed.png",
        "3_candidates.png",
        "4_rotation_applied.png",
        "debug_strip.png",
        "decision.json",
    ):
        assert (out / name).is_file(), name
    decision = json.loads((out / "decision.json").read_text(encoding="utf-8"))
    assert decision["job_id"] == "dbgstep1"
    assert decision["best_edge"] in ("top", "right", "bottom", "left")
    assert "scores" in decision
    assert "pipeline_git_commit" in decision
    assert result.result_filename.startswith("result_dbgstep1_")
    assert (out / result.result_filename).is_file()
    # debug kapaliyken dosya yazilmaz
    other = tmp_path / "nodbg"
    garment_normalizer.normalize(
        buf.getvalue(),
        force_rembg=False,
        debug=False,
        job_id="nodbg",
        drop_shadow=False,
        long_side=256,
    )
    assert not other.exists()


def test_production_deskewed_tshirt_picks_neck_top():
    """Kanıt: deskew sonrası yaka üstte; koltuk altı 'left' seçilmemeli."""
    from pathlib import Path

    from app.services.garment_polish import (
        compute_orientation_scores,
        detect_neckline_edge,
        rotate_neckline_to_north,
    )

    fixture = (
        Path(__file__).resolve().parent
        / "fixtures"
        / "orientation"
        / "tshirt_deskewed_neck_north.png"
    )
    img = Image.open(fixture).convert("L")
    alpha = np.asarray(img, dtype=np.uint8)
    scores = compute_orientation_scores(alpha)
    assert scores["best"] == "top"
    assert set(scores["candidate_pair"]) == {"top", "bottom"}
    assert scores["combined"]["top"] >= scores["combined"]["left"]
    assert detect_neckline_edge(alpha) == "top"

    rgba = Image.open(fixture).convert("RGBA")
    # L maskeyi alfa yap
    rgba.putalpha(img)
    out, deg, edge, _method = rotate_neckline_to_north(rgba)
    assert edge == "top" and deg == 0
    assert detect_neckline_edge(np.asarray(out.split()[-1])) == "top"


def test_orientation_signals_on_synthetic_tshirt():
    from app.services.garment_polish import compute_orientation_scores

    shirt = _rgba_tshirt_neckline()
    scores = compute_orientation_scores(np.asarray(shirt.split()[-1]))
    assert scores["best"] == "top"
    top = scores["detail"]["top"]
    left = scores["detail"]["left"]
    assert top["centrality_score"] >= left["centrality_score"]
    assert "depth_score" in top and "symmetry_score" in top
