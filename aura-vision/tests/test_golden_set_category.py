"""YOLO-boş full-frame fallback kategori golden-set (CI).

CLIP gereken vakalar: gerçek CLIP. CLIP yoksa skip + görünür uyarı (FAIL değil).
Red vakaları (empty_mask / cutout_failed) CLIP indirmez; CLIP çağrılırsa fail.
"""

from __future__ import annotations

import base64
import json
from io import BytesIO
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from app.core.exceptions import ModelUnavailableError
from app.services.garment_normalizer import garment_normalizer
from app.services.image_analyzer import ImageAnalyzer
from app.services.style_classifier import style_classifier
from tests.clip_guard import (
    CLIP_SKIP_REASON,
    ClipModelMissingWarning,
    load_clip_or_warn_skip,
)

ROOT = Path(__file__).resolve().parent / "golden_set" / "category"
EXPECTED = json.loads((ROOT / "expected.json").read_text(encoding="utf-8"))
_MIN_FOREGROUND = EXPECTED["foreground_gates"]["low_confidence_mean_opaque_min"]


class _EmptyYolo:
    model_name = "mock-yolo"

    def detect(self, image):
        return []


class _ClipMustNotRun:
    model_name = "clip-must-not-run"

    def classify_detailed(self, images):
        raise AssertionError("CLIP bos-sahne reddinde cagrilmamali")

    def classify(self, images):
        raise AssertionError("CLIP bos-sahne reddinde cagrilmamali")


@pytest.fixture(scope="session")
def _clip():
    return load_clip_or_warn_skip()


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


def test_clip_unavailable_emits_visible_warning(monkeypatch):
    """CLIP yoksa sessiz skip değil — ClipModelMissingWarning + skip reason."""
    monkeypatch.setattr(
        type(style_classifier),
        "load",
        lambda self: (_ for _ in ()).throw(ModelUnavailableError("yok")),
    )
    with pytest.warns(ClipModelMissingWarning, match="CLIP modeli bulunamad"):
        with pytest.raises(pytest.skip.Exception, match=CLIP_SKIP_REASON):
            load_clip_or_warn_skip()


@pytest.mark.parametrize(
    "case",
    EXPECTED["cases"],
    ids=[c["id"] for c in EXPECTED["cases"]],
)
def test_yolo_empty_fallback_golden_category(case, request):
    path = ROOT / case["file"]
    raw = path.read_bytes()

    if case.get("known_failure"):
        cut, _src = garment_normalizer._cutout(
            Image.open(BytesIO(raw)), prefer_rembg=True
        )
        alpha = np.asarray(cut.convert("RGBA").split()[-1])
        mean_op = float((alpha > 127).mean())
        lo, hi = case["mean_opaque_range"]
        assert lo <= mean_op < hi, (case["id"], mean_op, case.get("known_issue_note"))
        assert ImageAnalyzer._empty_or_unusable_mask_reason(cut) is case.get(
            "expected_empty_reason"
        ), (case["id"], "geometrik kapı bu çarşaf-duvarı hâlâ kıyafet sanıyor")
        return

    if case.get("needs_clip"):
        clip = request.getfixturevalue("_clip")
        analyzer = ImageAnalyzer(detector=_EmptyYolo(), classifier=clip)
    else:
        analyzer = ImageAnalyzer(detector=_EmptyYolo(), classifier=_ClipMustNotRun())
    result = analyzer.analyze(raw, path.name, debug=False, job_id=case["id"][:12])
    mean_op = _mean_opaque(result, raw)

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

    if case.get("expect_near_min_foreground"):
        lo, hi = case["mean_opaque_range"]
        assert lo <= mean_op < hi, (
            case["id"],
            mean_op,
            f"esik={_MIN_FOREGROUND} aralik={lo}-{hi} (bu PNG'ye gore kilit)",
        )
    if case.get("expect_below_min_foreground"):
        assert mean_op < case["mean_opaque_max"], (case["id"], mean_op, _MIN_FOREGROUND)
