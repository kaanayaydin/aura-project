"""RotNet ensemble karar mantığı — geometrik skor değişmez."""

from __future__ import annotations

from app.services import rotnet_inference as ri


def _geo(edge: str, low: bool, depth: float = 1.0) -> dict:
    return {
        "best": edge,
        "low_confidence": low,
        "score_gap": 0.05 if low else 0.4,
        "max_depth_across_edges": depth,
        "decision_note": None,
    }


def test_ensemble_agreement_geo_low_is_medium(monkeypatch):
    """Kenar eşleşmesi tek başına high değil — geometri kanıtsızsa medium."""
    monkeypatch.setattr(
        ri,
        "infer_rotnet",
        lambda _a: {
            "predicted_class": 270,
            "predicted_edge": "left",
            "confidence": 0.99,
            "probs": {},
            "available": True,
        },
    )
    out = ri.apply_orientation_ensemble(_geo("left", True, depth=0.004), __import__("numpy").zeros((8, 8)))
    assert out["ensemble"]["final_confidence"] == "medium"
    assert out["ensemble"]["reason"] == "agreement_geometry_low"
    assert out["requires_confirmation"] is True
    assert out["low_confidence"] is False
    assert out["best"] == "left"


def test_ensemble_agreement_is_high(monkeypatch):
    monkeypatch.setattr(
        ri,
        "infer_rotnet",
        lambda _a: {
            "predicted_class": 0,
            "predicted_edge": "top",
            "confidence": 0.55,
            "probs": {},
            "available": True,
        },
    )
    out = ri.apply_orientation_ensemble(_geo("top", False), __import__("numpy").zeros((8, 8)))
    assert out["ensemble"]["final_confidence"] == "high"
    assert out["ensemble"]["final_edge"] == "top"
    assert out["low_confidence"] is False
    assert out["requires_confirmation"] is False
    assert out["geometric"]["chosen_edge"] == "top"


def test_ensemble_rotnet_override_medium(monkeypatch):
    monkeypatch.setattr(
        ri,
        "infer_rotnet",
        lambda _a: {
            "predicted_class": 90,
            "predicted_edge": "right",
            "confidence": 0.91,
            "probs": {},
            "available": True,
        },
    )
    out = ri.apply_orientation_ensemble(_geo("bottom", True), __import__("numpy").zeros((8, 8)))
    assert out["ensemble"]["reason"] == "rotnet_override_low_geometry"
    assert out["ensemble"]["final_confidence"] == "medium"
    assert out["best"] == "right"
    assert out["low_confidence"] is False
    assert out["requires_confirmation"] is True


def test_ensemble_disagreement_stays_low(monkeypatch):
    monkeypatch.setattr(
        ri,
        "infer_rotnet",
        lambda _a: {
            "predicted_class": 90,
            "predicted_edge": "right",
            "confidence": 0.4,
            "probs": {},
            "available": True,
        },
    )
    out = ri.apply_orientation_ensemble(_geo("left", True), __import__("numpy").zeros((8, 8)))
    assert out["ensemble"]["final_confidence"] == "low"
    assert out["low_confidence"] is True


def test_ensemble_noop_when_rotnet_missing(monkeypatch):
    monkeypatch.setattr(ri, "infer_rotnet", lambda _a: None)
    geo = _geo("top", False)
    out = ri.apply_orientation_ensemble(geo, __import__("numpy").zeros((8, 8)))
    assert out["ensemble"]["reason"] == "rotnet_unavailable"
    assert out["best"] == "top"
    assert out["low_confidence"] is False
