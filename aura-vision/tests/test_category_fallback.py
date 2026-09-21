"""YOLO bosken tam-kare CLIP fallback + kategori debug."""

from __future__ import annotations

import io
from pathlib import Path

import pytest
from PIL import Image

from app.services.image_analyzer import (
    USER_MSG_BELOW_THRESHOLD,
    USER_MSG_CUTOUT_FAILED,
    USER_MSG_GARMENT_TOO_SMALL,
    ImageAnalyzer,
)
from app.services.style_classifier import CategoryPrediction, ClassificationResult


def _rgb_shirt() -> bytes:
    img = Image.new("RGB", (120, 140), (200, 200, 200))
    for y in range(25, 120):
        for x in range(30, 90):
            img.putpixel((x, y), (30, 90, 200))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=90)
    return buf.getvalue()


class _EmptyYolo:
    model_name = "mock-yolo"

    def detect(self, image):
        return []


class _FakeClip:
    model_name = "mock-clip"

    def __init__(self, label="t-shirt", conf=0.71):
        self.label = label
        self.conf = conf

    def classify_detailed(self, images):
        scores = {self.label: self.conf, "shirt": 0.12, "pants": 0.05}
        pred = CategoryPrediction(self.label, self.conf, scores)
        return [ClassificationResult(prediction=pred, all_scores=scores)]

    def classify(self, images):
        return [row.prediction for row in self.classify_detailed(images)]


def test_empty_yolo_fallback_assigns_category(monkeypatch, tmp_path):
    monkeypatch.setattr("app.services.category_debug.debug_root", lambda: tmp_path)
    analyzer = ImageAnalyzer(detector=_EmptyYolo(), classifier=_FakeClip())
    result = analyzer.analyze(_rgb_shirt(), "shirt.jpg", debug=True, job_id="catfb1")
    assert result.detected_items
    assert result.detected_items[0].category == "t-shirt"
    assert result.detected_items[0].category_confidence == 0.71
    assert result.rejected_reason is None
    assert "fullframe_fallback" in result.stages_completed
    out = tmp_path / "catfb1"
    assert (out / "cat_1_cutout_input.png").is_file()
    assert (out / "cat_2_clip_scores.json").is_file()
    assert (out / "cat_decision.json").is_file()


def test_below_threshold_user_message():
    analyzer = ImageAnalyzer(detector=_EmptyYolo(), classifier=_FakeClip(conf=0.10))
    result = analyzer.analyze(_rgb_shirt(), "low.jpg", debug=False)
    assert not any(i.category for i in result.detected_items)
    assert result.rejected_reason == "below_threshold"
    assert result.user_message == USER_MSG_BELOW_THRESHOLD


def test_user_messages_are_actionable():
    assert "düz bir açıdan" in USER_MSG_BELOW_THRESHOLD
    assert "sade bir zeminde" in USER_MSG_CUTOUT_FAILED
    assert "daha yakından" in USER_MSG_GARMENT_TOO_SMALL


def test_fullframe_fallback_rejects_empty_mask(monkeypatch):
    empty = Image.new("RGBA", (80, 80), (0, 0, 0, 0))

    def _empty_cutout(image, prefer_rembg=True):
        return empty, "chroma"

    monkeypatch.setattr(
        "app.services.garment_normalizer.garment_normalizer._cutout",
        _empty_cutout,
    )
    analyzer = ImageAnalyzer(detector=_EmptyYolo(), classifier=_FakeClip())
    result = analyzer.analyze(_rgb_shirt(), "empty.jpg", debug=False)
    assert not any(i.category for i in result.detected_items)
    assert result.rejected_reason == "empty_mask"
    assert result.user_message == USER_MSG_CUTOUT_FAILED
