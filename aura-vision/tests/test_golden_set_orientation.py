"""Orientation golden-set + idempotency (CI her PR).

low → deg=0 güvenlik ağı. medium/high → ensemble kenarı uygulanır.
Sentetik 90: sızıntısız RotNet conf < 0.8, faz hatası açık. 270 medium override.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest

from app.core.config import settings
from app.services.garment_normalizer import garment_normalizer
from app.services.garment_polish import compute_orientation_scores

ROOT = Path(__file__).resolve().parent / "golden_set" / "orientation"
EXPECTED = json.loads((ROOT / "expected.json").read_text(encoding="utf-8"))


def _normalize(rel: str):
    raw = (ROOT / rel).read_bytes()
    return garment_normalizer.normalize(
        raw, force_rembg=False, drop_shadow=False, long_side=256
    )


@pytest.mark.parametrize("case", EXPECTED["cases"], ids=lambda c: c["id"])
def test_golden_orientation_contract(case):
    result = _normalize(case["file"])
    low = bool(result.low_confidence)
    exp = case["expected_confidence"]
    if exp == "high":
        assert not low, case["id"]
        assert result.rotation_suggested == case["expected_suggested"]
        assert result.rotation_deg_applied == case["expected_deg"]
    elif exp == "medium":
        assert not low, case["id"]
        assert result.rotation_suggested == case["expected_suggested"]
        assert result.rotation_deg_applied == case["expected_deg"]
        assert result.ensemble_confidence == "medium"
    else:
        assert low, case["id"]
        assert result.rotation_deg_applied == 0

    if case.get("idempotent_high") and result.cutout_rgba is not None and not case.get(
        "known_bad_rotation"
    ):
        scores = compute_orientation_scores(np.asarray(result.cutout_rgba.split()[-1]))
        assert scores["best"] == "top"
        assert scores["low_confidence"] is False
        assert scores["score_gap"] >= 0.15


def test_idempotent_synthetic_uneck():
    result1 = _normalize("images/synthetic_uneck.png")
    assert result1.cutout_rgba is not None
    scores = compute_orientation_scores(np.asarray(result1.cutout_rgba.split()[-1]))
    assert scores["best"] == "top"
    assert scores["low_confidence"] is False
    assert result1.rotation_deg_applied == 0


def test_phase_error_synthetic_90_rotnet_below_override():
    """Sızıntısız model: RotNet left der ama conf < 0.8 → low, deg=0."""
    result = _normalize("images/synthetic_uneck_r90.png")
    assert result.low_confidence is True
    assert result.rotation_deg_applied == 0
    assert result.ensemble_confidence == "low"


def test_phase_error_synthetic_270_resolved_by_rotnet():
    result = _normalize("images/synthetic_uneck_r270.png")
    assert result.low_confidence is False
    assert result.ensemble_confidence == "medium"
    assert result.rotation_suggested == "right"
    assert result.rotation_deg_applied == 90


def test_deskew_step_always_executes(tmp_path, monkeypatch):
    """hafif_saga: deskew adımı çalışır; 0° = eşik altı, atlanmış adım değil."""
    from app.services import orientation_debug as od

    monkeypatch.setattr(od, "debug_root", lambda: tmp_path)
    raw = (ROOT / "images/askisiz_hafif_saga.png").read_bytes()
    result = garment_normalizer.normalize(
        raw, force_rembg=False, drop_shadow=False, long_side=256,
        debug=True, job_id="deskew_exec",
    )
    assert result.deskew_step_executed is True
    decision = json.loads(
        (tmp_path / "deskew_exec" / "decision.json").read_text(encoding="utf-8")
    )
    assert decision["deskew_step_executed"] is True
    assert decision["deskew_skip_reason"] == "angle_below_min_threshold"
    assert abs(float(decision["deskew_input_angle_estimated"])) < float(
        decision.get("deskew_min_abs_deg") or settings.studio_deskew_min_abs_deg
    )


def test_hafif_saga_depth_gate_still_fires_no_false_270(tmp_path, monkeypatch):
    """Geometri depth kapısı durur; RotNet top der, 270 uygulanmaz."""
    from app.services import orientation_debug as od

    monkeypatch.setattr(od, "debug_root", lambda: tmp_path)
    raw = (ROOT / "images/askisiz_hafif_saga.png").read_bytes()
    result = garment_normalizer.normalize(
        raw, force_rembg=False, drop_shadow=False, long_side=256,
        debug=True, job_id="hafif_saga_abs",
    )
    assert result.rotation_deg_applied == 0
    assert result.rotation_suggested == "top"
    decision = json.loads(
        (tmp_path / "hafif_saga_abs" / "decision.json").read_text(encoding="utf-8")
    )
    geo = decision.get("geometric") or decision["scores"].get("geometric") or {}
    assert geo.get("decision_note") == "no_edge_shows_real_notch_depth"
    assert float(geo.get("max_depth_across_edges") or 0) < float(settings.orient_min_absolute_depth)
    rot = decision.get("rotnet") or {}
    if rot.get("available"):
        assert rot.get("predicted_edge") == "top"
        assert result.ensemble_confidence == "medium"


def test_crewneck_shallow_notch_stays_above_depth_threshold():
    """Sığ crew-neck FN-risk: max_depth 0.30 üstünde, high+top."""
    result = _normalize("images/synthetic_crewneck.png")
    assert result.low_confidence is False
    assert result.rotation_suggested == "top"
    assert result.rotation_deg_applied == 0
    scores = compute_orientation_scores(np.asarray(result.cutout_rgba.split()[-1]))
    assert float(scores["max_depth_across_edges"]) >= float(settings.orient_min_absolute_depth)


def test_no_false_high_confidence_without_real_notch(tmp_path, monkeypatch):
    """Ensemble final=high ise geometrik max_depth eşiğin altında olamaz.

    low_confidence continue ile atlanmaz — aksi halde geo-low + rotnet
    eşleşmesi 'high' sızıntısını kaçırır (real_duz_r90).
    """
    from app.services import orientation_debug as od

    monkeypatch.setattr(od, "debug_root", lambda: tmp_path)
    min_depth = float(settings.orient_min_absolute_depth)
    for case in EXPECTED["cases"]:
        raw = (ROOT / case["file"]).read_bytes()
        result = garment_normalizer.normalize(
            raw,
            force_rembg=False,
            drop_shadow=False,
            long_side=256,
            debug=True,
            job_id=f"nofalse_{case['id']}",
        )
        ens = (result.ensemble_confidence or "").strip()
        if ens != "high":
            continue
        decision = json.loads(
            (tmp_path / f"nofalse_{case['id']}" / "decision.json").read_text(encoding="utf-8")
        )
        geo = decision.get("geometric") or {}
        max_d = float(geo.get("max_depth_across_edges") or 0.0)
        assert max_d >= min_depth, (
            "{0}: ensemble high ama geometric max_depth={1} < {2}".format(
                case["id"], max_d, min_depth
            )
        )
