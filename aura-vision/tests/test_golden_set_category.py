"""YOLO-boş full-frame fallback kategori golden-set (CI).

Önceki kapsama: tests/test_category_fallback.py — 1 sentetik tee (mock CLIP),
1 below_threshold, 1 mock boş-maske. Golden-set/category altında vaka yoktu.

Bu dosya gerçek cutout (chroma; rembg conftest ile kapalı) + gerçek CLIP ile
çeşitli profilleri kilitler. YOLO her zaman boş mock (fallback yolu zorunlu).
"""

from __future__ import annotations

import base64
import json
from io import BytesIO
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from app.services.garment_normalizer import garment_normalizer
from app.services.image_analyzer import ImageAnalyzer
from app.services.style_classifier import style_classifier

ROOT = Path(__file__).resolve().parent / "golden_set" / "category"
EXPECTED = json.loads((ROOT / "expected.json").read_text(encoding="utf-8"))
# is_low_confidence_mask(mean_opaque < 0.06) — kullanıcı dilindeki MIN_FOREGROUND_RATIO
_MIN_FOREGROUND = EXPECTED["foreground_gates"]["low_confidence_mean_opaque_min"]


class _EmptyYolo:
    model_name = "mock-yolo"

    def detect(self, image):
        return []


@pytest.fixture(scope="session")
def _clip():
    try:
        style_classifier.load()
    except Exception as exc:  # noqa: BLE001
        pytest.skip(f"CLIP yuklenemedi: {exc}")
    return style_classifier


def _mean_opaque(result, raw: bytes) -> float:
    item = result.detected_items[0] if result.detected_items else None
    if item is not None and item.cutout_image_base64:
        cut = Image.open(BytesIO(base64.b64decode(item.cutout_image_base64)))
    else:
        cut, _src = garment_normalizer._cutout(
            Image.open(BytesIO(raw)), prefer_rembg=True
        )
    alpha = np.asarray(cut.convert("RGBA").split()[-1])
    return float((alpha > 127).mean())


@pytest.mark.parametrize(
    "case",
    EXPECTED["cases"],
    ids=[c["id"] for c in EXPECTED["cases"]],
)
def test_yolo_empty_fallback_golden_category(case, _clip):
    path = ROOT / case["file"]
    raw = path.read_bytes()
    analyzer = ImageAnalyzer(detector=_EmptyYolo(), classifier=_clip)
    result = analyzer.analyze(raw, path.name, debug=False, job_id=case["id"][:12])

    categorized = [item for item in result.detected_items if item.category]
    want_cat = case.get("expected_category")
    want_reason = case.get("expected_rejected_reason")
    allowed = case.get("acceptable_categories") or ([want_cat] if want_cat else [])

    if want_cat:
        assert categorized, (case["id"], result.rejected_reason, result.stages_completed)
        assert categorized[0].category in allowed, (
            case["id"],
            categorized[0].category,
            allowed,
        )
        assert categorized[0].label == "full_frame"
        assert result.rejected_reason is None
        assert "fullframe_fallback" in result.stages_completed
        assert want_reason is None
    else:
        assert not categorized, (case["id"], [i.category for i in result.detected_items])
        assert result.rejected_reason == want_reason, (case["id"], result.rejected_reason)

    mean_op = _mean_opaque(result, raw)
    if case.get("expect_near_min_foreground"):
        lo, hi = case["mean_opaque_range"]
        assert lo <= mean_op < hi, (
            case["id"],
            mean_op,
            f"esik={_MIN_FOREGROUND} aralik={lo}-{hi} (bu PNG'ye gore kilit)",
        )
    if case.get("expect_below_min_foreground"):
        assert mean_op < case["mean_opaque_max"], (case["id"], mean_op, _MIN_FOREGROUND)
