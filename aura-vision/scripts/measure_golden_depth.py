#!/usr/bin/env python3
"""Golden-set max(depth) tablosu — yeni veri toplamaz, mevcut 12+ vakayı ölçer."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.config import settings  # noqa: E402
from app.services.garment_normalizer import garment_normalizer  # noqa: E402


def main() -> int:
    expected_path = ROOT / "tests" / "golden_set" / "orientation" / "expected.json"
    expected = json.loads(expected_path.read_text(encoding="utf-8"))
    threshold = float(settings.orient_min_absolute_depth)
    rows = []
    print(
        f"{'case':<28} {'exp_conf':<8} {'exp_edge':<8} {'max_d':>7} {'vs_0.30'}"
    )
    print("-" * 72)
    for case in expected["cases"]:
        raw = (expected_path.parent / case["file"]).read_bytes()
        result = garment_normalizer.normalize(
            raw,
            force_rembg=False,
            drop_shadow=False,
            long_side=256,
            debug=True,
            job_id=f"depth_{case['id']}",
        )
        scores = {}
        decision = Path(result.debug_dir or "") / "decision.json"
        if decision.is_file():
            scores = json.loads(decision.read_text(encoding="utf-8")).get("scores") or {}
        max_d = float(scores.get("max_depth_across_edges") or 0.0)
        exp_conf = case.get("expected_confidence", "?")
        exp_edge = case.get("expected_suggested") or "—"
        flag = "above" if max_d >= threshold else "BELOW"
        rows.append(
            {
                "case_name": case["id"],
                "expected_confidence": exp_conf,
                "expected_edge": exp_edge,
                "measured_max_depth": round(max_d, 3),
                "vs_threshold": flag,
            }
        )
        print(
            f"{case['id']:<28} {exp_conf:<8} {exp_edge:<8} {max_d:7.3f} {flag}"
        )
    high = [r for r in rows if r["expected_confidence"] == "high"]
    fake = [r for r in rows if r["case_name"] == "askisiz_hafif_saga"]
    print()
    print(f"threshold={threshold}")
    print(
        "dogru-yuksek min={0}  sahte-yaka max={1}".format(
            min(r["measured_max_depth"] for r in high) if high else None,
            max(r["measured_max_depth"] for r in fake) if fake else None,
        )
    )
    fn = [r for r in high if r["vs_threshold"] == "BELOW"]
    print("false-negative (high beklenen ama esik alti):", [r["case_name"] for r in fn] or "yok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
