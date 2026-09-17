"""Maskeleme testleri — SCHP ONNX indirmeden (sentetik seg + fallback)."""

import numpy as np
from PIL import Image

from app.config import settings
from app.services.catvton.mask import (
    LIP_LOWER_PROTECT_IDS,
    LIP_MASK_IDS,
    apply_semantic_upper_guard,
    build_agnostic_mask,
    build_torso_mask,
    parsing_to_agnostic_mask,
)
from app.services.catvton.schp_parser import SCHP_INPUT_SIZE, preprocess_schp


def test_torso_mask_matches_person_size():
    person = Image.new("RGB", (200, 300), color=(80, 80, 80))
    mask = build_torso_mask(person)
    assert mask.size == person.size
    assert mask.mode == "L"
    assert mask.getextrema()[1] > 0


def test_parsing_to_agnostic_upper_includes_arms_and_clothes():
    h, w = 100, 80
    seg = np.zeros((h, w), dtype=np.int64)
    seg[20:60, 20:60] = 5
    seg[25:55, 5:18] = 14
    seg[25:55, 62:75] = 15
    seg[5:18, 30:50] = 13

    mask, protect = parsing_to_agnostic_mask(seg, cloth_type="upper", dilate_px=3)
    arr = np.array(mask)
    assert arr.shape == (h, w)
    assert protect is not None
    assert np.asarray(protect).max() == 0  # alt giyim yok
    assert arr[40, 40] > 0
    assert arr[40, 10] > 0
    assert arr[10, 40] < 32  # yuz — guclu inpaint yok (blur soft bleed olabilir)


def test_parsing_to_agnostic_lower():
    seg = np.zeros((60, 40), dtype=np.int64)
    seg[30:55, 10:30] = 9
    mask, protect = parsing_to_agnostic_mask(seg, cloth_type="lower", dilate_px=1)
    assert protect is None
    assert np.array(mask).max() > 0


def test_cloth_type_upper_excludes_pants_includes_upper_clothes():
    h, w = 80, 60
    seg = np.zeros((h, w), dtype=np.int64)
    seg[10:35, 15:45] = 5
    seg[45:75, 15:45] = 9
    upper, protect = parsing_to_agnostic_mask(seg, cloth_type="upper", dilate_px=0)
    lower, _ = parsing_to_agnostic_mask(seg, cloth_type="lower", dilate_px=0)
    upper_a = np.array(upper)
    lower_a = np.array(lower)
    assert protect is not None
    assert np.asarray(protect)[60, 30] > 0
    assert upper_a[20, 30] > 0
    assert upper_a[60, 30] == 0
    assert lower_a[20, 30] == 0
    assert lower_a[60, 30] > 0


def test_semantic_guard_covers_shirt_hem_without_horizontal_cut():
    """Eğik/asimetrik kemer: yatay cut yok; label=5 etek maskede, pantolon protect'te."""
    h, w = 100, 60
    seg = np.zeros((h, w), dtype=np.int64)
    seg[15:55, 15:45] = 5
    # Egik pantolon ustu: solda y=48, sagda y=55
    for x in range(15, 45):
        y0 = 48 + (x - 15) // 4
        seg[y0:95, x] = 9
    # Gomlek etegi pantolon ust bandina sarkmis (ust giysi label)
    seg[50:58, 20:40] = 5

    mask, protect = parsing_to_agnostic_mask(seg, cloth_type="upper", dilate_px=5)
    arr = np.array(mask)
    prot = np.asarray(protect)
    assert prot[70, 30] > 0
    assert arr[70, 30] == 0  # derin pantolon
    assert arr[25, 30] > 0
    # Kemer bandindaki gomlek etegi (label 5) inpaint'te — crop-top/beyaz serit yok
    assert arr[52, 30] > 0


def test_apply_semantic_upper_guard_dilate_then_protect():
    h, w = 80, 50
    seg = np.zeros((h, w), dtype=np.int64)
    seg[10:45, 15:35] = 5
    seg[40:75, 15:35] = 9
    seg[40:48, 18:32] = 5  # etek artiklari
    upper = np.isin(seg, list(LIP_MASK_IDS["upper"])).astype(np.uint8) * 255
    out, protect = apply_semantic_upper_guard(
        upper, seg, seam_dilate_px=5, protect_dilate_px=1
    )
    assert out[20, 25] > 0
    assert out[42, 25] > 0  # etek dilate + protect disinda
    assert protect[55, 25] > 0
    assert out[55, 25] == 0  # pantolon kumasina tasmaz
    assert out[65, 25] == 0


def test_cloth_type_overall_covers_upper_and_lower():
    h, w = 80, 60
    seg = np.zeros((h, w), dtype=np.int64)
    seg[10:35, 15:45] = 5
    seg[45:75, 15:45] = 9
    overall, protect = parsing_to_agnostic_mask(seg, cloth_type="overall", dilate_px=0)
    assert protect is None
    arr = np.array(overall)
    assert arr[20, 30] > 0
    assert arr[60, 30] > 0


def test_build_agnostic_passes_cloth_type_when_schp_disabled(monkeypatch):
    monkeypatch.setattr(settings, "schp_enabled", False)
    person = Image.new("RGB", (120, 180), color=(40, 40, 40))
    mask, source, protect = build_agnostic_mask(person, cloth_type="lower")
    assert source == "torso-fallback"
    assert protect is None
    assert mask.size == person.size


def test_build_agnostic_falls_back_when_schp_disabled(monkeypatch):
    monkeypatch.setattr(settings, "schp_enabled", False)
    person = Image.new("RGB", (120, 180), color=(40, 40, 40))
    mask, source, protect = build_agnostic_mask(person)
    assert source == "torso-fallback"
    assert protect is None
    assert mask.size == person.size
    assert mask.getextrema()[1] > 0


def test_build_agnostic_falls_back_on_schp_load_error(monkeypatch):
    monkeypatch.setattr(settings, "schp_enabled", True)

    class _Boom:
        def ensure_loaded(self):
            return False

        @property
        def load_error(self):
            return "no-model"

    monkeypatch.setattr(
        "app.services.catvton.schp_parser.get_schp_parser",
        lambda: _Boom(),
    )
    person = Image.new("RGB", (100, 140), color=(10, 10, 10))
    mask, source, protect = build_agnostic_mask(person)
    assert source == "torso-fallback"
    assert protect is None
    assert mask.getextrema()[1] > 0


def test_preprocess_schp_shape():
    img = Image.new("RGB", (320, 480), color=(120, 90, 70))
    arr = preprocess_schp(img)
    assert arr.shape == (1, 3, SCHP_INPUT_SIZE, SCHP_INPUT_SIZE)
    assert arr.dtype == np.float32


def test_lip_mask_ids_cover_expected_parts():
    assert 5 in LIP_MASK_IDS["upper"]
    assert 14 in LIP_MASK_IDS["upper"]
    assert 9 in LIP_MASK_IDS["lower"]
    assert 5 in LIP_MASK_IDS["overall"]
    assert LIP_LOWER_PROTECT_IDS == {9, 12, 16, 17}
