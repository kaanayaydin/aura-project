#!/usr/bin/env python3
"""Golden-set orientation'ı HEAD koduyla çalıştırır; summary.json üretir.

Rapor kopyalamaz — her vaka için garment_normalizer.normalize(debug=True).
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.services import orientation_debug as od  # noqa: E402
from app.services.garment_normalizer import garment_normalizer  # noqa: E402

GOLDEN = ROOT / "tests" / "golden_set" / "orientation"
OUT = ROOT / "debug_output" / "_golden_report_head"


def main() -> int:
    expected = json.loads((GOLDEN / "expected.json").read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    od.debug_root = lambda: OUT
    rows = []
    for case in expected["cases"]:
        cid = case["id"]
        raw = (GOLDEN / case["file"]).read_bytes()
        result = garment_normalizer.normalize(
            raw,
            force_rembg=False,
            drop_shadow=False,
            long_side=256,
            debug=True,
            job_id=cid,
        )
        decision = json.loads((OUT / cid / "decision.json").read_text(encoding="utf-8"))
        geo = decision.get("geometric") or {}
        rot = decision.get("rotnet") or {}
        ens = decision.get("ensemble") or {}
        row = {
            "id": cid,
            "kind": case.get("kind"),
            "ground_truth_neck": case.get("ground_truth_neck"),
            "geo_edge": geo.get("chosen_edge"),
            "geo_conf": geo.get("confidence"),
            "max_depth": geo.get("max_depth_across_edges"),
            "decision_note": geo.get("decision_note"),
            "rot_edge": rot.get("predicted_edge"),
            "rot_class": rot.get("predicted_class"),
            "rot_conf": rot.get("confidence"),
            "rot_probs": rot.get("probs"),
            "ens_conf": ens.get("final_confidence") or result.ensemble_confidence,
            "ens_edge": ens.get("final_edge"),
            "ens_reason": ens.get("reason"),
            "deg": result.rotation_deg_applied,
            "method": decision.get("rotation_method") or result.rotation_method,
            "suggested": result.rotation_suggested,
            "low_confidence": result.low_confidence,
            "requires_confirmation": result.requires_confirmation,
        }
        rows.append(row)
        print(
            "OK {0} ens={1} deg={2} method={3}".format(
                cid, row["ens_conf"], row["deg"], row["method"]
            ),
            flush=True,
        )
    (OUT / "summary.json").write_text(
        json.dumps(rows, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print("WROTE", OUT / "summary.json", "n=", len(rows), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
