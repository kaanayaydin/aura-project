#!/usr/bin/env python3
"""Category fixture'larını GERÇEK POST /normalize-garment ile ölçer.

Java VisionGarmentClient skip_orientation=false gönderir; bu script formda
skip_orientation alanını GÖNDERMEZ (endpoint varsayılanı False).

Pytest ile aynı chroma pini: rembg kapalı (conftest). Üretim rembg açık
olabilir; bu rapor CI tekrarlanabilir chroma hattıdır.

Ham kaynak: debug_output/_normalize_garment_fixture_report/summary.json
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

os.environ["AURA_STUDIO_REMBG"] = "false"
os.environ["AURA_STUDIO_PREFER_REMBG"] = "false"

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient  # noqa: E402

from app.core.config import settings  # noqa: E402
from app.services.garment_normalizer import (  # noqa: E402
    UnusableCutoutError,
    garment_normalizer,
)
from main import app  # noqa: E402

object.__setattr__(settings, "studio_rembg_enabled", False)
object.__setattr__(settings, "studio_prefer_rembg", False)

GOLDEN = ROOT / "tests" / "golden_set" / "category"
OUT = ROOT / "debug_output" / "_normalize_garment_fixture_report"


def _http_normalize(client: TestClient, raw: bytes, filename: str) -> dict:
    resp = client.post(
        "/api/v1/vision/normalize-garment",
        files={"file": (filename, raw, "image/png")},
    )
    body: dict | str
    try:
        body = resp.json()
    except Exception:  # noqa: BLE001
        body = resp.text
    detail = body.get("detail") if isinstance(body, dict) else None
    reason = None
    if isinstance(detail, dict):
        reason = detail.get("rejected_reason")
    return {
        "status_code": resp.status_code,
        "rejected_reason": reason,
        "body": body if resp.status_code != 200 else {"status": body.get("status")},
    }


def _internal_normalize(raw: bytes, job_id: str) -> dict:
    try:
        result = garment_normalizer.normalize(
            raw,
            skip_orientation=False,
            drop_shadow=False,
            debug=False,
            job_id=job_id[:12],
        )
        stats = result.gate_stats or {}
        return {
            "ok": True,
            "rejected_reason": None,
            "mean_opaque": stats.get("mean_opaque"),
            "fg_bg_l1": stats.get("fg_bg_l1"),
            "opaque_px": stats.get("opaque_px"),
            "cutout_source": result.cutout_source,
            "width": result.width,
            "height": result.height,
        }
    except UnusableCutoutError as exc:
        stats = exc.stats or {}
        return {
            "ok": False,
            "rejected_reason": exc.rejected_reason,
            "mean_opaque": stats.get("mean_opaque"),
            "fg_bg_l1": stats.get("fg_bg_l1"),
            "opaque_px": stats.get("opaque_px"),
            "cutout_source": None,
            "width": None,
            "height": None,
        }


def _write_expected(expected: dict, rows: list[dict]) -> None:
    """expected.json normalize_http alanlarını bu koşunun ham ölçümünden yazar."""
    by_id = {row["id"]: row for row in rows}
    for case in expected["cases"]:
        row = by_id[case["id"]]
        case["normalize_http"] = {
            "status": row["http_status"],
            "rejected_reason": row["http_rejected_reason"],
            "mean_opaque": row["mean_opaque"],
            "fg_bg_l1": row["fg_bg_l1"],
            "opaque_px": row["opaque_px"],
            "cutout_source": row["cutout_source"],
            "source": (
                "scripts/dump_normalize_garment_fixture_report.py "
                "POST /api/v1/vision/normalize-garment (skip_orientation omitted, rembg=false)"
            ),
        }
    expected["normalize_endpoint"] = {
        "path": "POST /api/v1/vision/normalize-garment",
        "skip_orientation": False,
        "rembg": False,
        "source_script": "scripts/dump_normalize_garment_fixture_report.py",
    }
    path = GOLDEN / "expected.json"
    path.write_text(
        json.dumps(expected, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print("WROTE", path, flush=True)


def main() -> int:
    expected = json.loads((GOLDEN / "expected.json").read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    client = TestClient(app)
    rows = []
    for case in expected["cases"]:
        cid = case["id"]
        path = GOLDEN / case["file"]
        raw = path.read_bytes()
        http = _http_normalize(client, raw, path.name)
        internal = _internal_normalize(raw, cid)
        row = {
            "id": cid,
            "file": case["file"],
            "http_status": http["status_code"],
            "http_rejected_reason": http["rejected_reason"],
            "internal_ok": internal["ok"],
            "internal_rejected_reason": internal["rejected_reason"],
            "mean_opaque": internal["mean_opaque"],
            "fg_bg_l1": internal["fg_bg_l1"],
            "opaque_px": internal["opaque_px"],
            "cutout_source": internal["cutout_source"],
            "skip_orientation_sent": False,
            "http_internal_reason_match": http["rejected_reason"]
            == internal["rejected_reason"]
            and (
                (http["status_code"] == 200 and internal["ok"])
                or (http["status_code"] == 422 and not internal["ok"])
            ),
        }
        rows.append(row)
        print(
            "OK {0} http={1} reason={2} mean={3} L1={4}".format(
                cid,
                row["http_status"],
                row["http_rejected_reason"],
                row["mean_opaque"],
                row["fg_bg_l1"],
            ),
            flush=True,
        )
    payload = {
        "endpoint": "POST /api/v1/vision/normalize-garment",
        "skip_orientation": False,
        "n": len(rows),
        "rows": rows,
    }
    out_path = OUT / "summary.json"
    out_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    print("WROTE", out_path, "n=", len(rows), flush=True)
    _write_expected(expected, rows)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
