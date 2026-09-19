"""Kategori (CLIP) debug enstrümantasyonu — Adım 1.

debug=True iken `debug_output/{job_id}/` altına cutout + ham skor + karar yazar.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional

from PIL import Image

from app.core.build_info import git_commit_short
from app.core.config import BASE_DIR, settings

logger = logging.getLogger("aura.vision.category.debug")


def debug_root() -> Path:
    raw = getattr(settings, "studio_debug_dir", None)
    if raw:
        return Path(str(raw))
    return BASE_DIR / "debug_output"


def write_category_debug(
    *,
    job_id: str,
    cutout: Optional[Image.Image],
    all_scores: dict[str, float],
    chosen_category: Optional[str],
    confidence: Optional[float],
    threshold: float,
    rejected_reason: Optional[str],
    yolo_count: int,
    fallback_used: bool,
    cutout_source: str,
) -> Path:
    out_dir = debug_root() / job_id
    out_dir.mkdir(parents=True, exist_ok=True)

    if cutout is not None:
        rgb = cutout.convert("RGBA") if cutout.mode == "RGBA" else cutout.convert("RGB")
        rgb.save(out_dir / "cat_1_cutout_input.png")

    ranked = sorted(all_scores.items(), key=lambda kv: kv[1], reverse=True)
    (out_dir / "cat_2_clip_scores.json").write_text(
        json.dumps(
            {
                "scores": {k: round(float(v), 4) for k, v in all_scores.items()},
                "ranking": [{"label": k, "score": round(float(v), 4)} for k, v in ranked],
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    above = confidence is not None and confidence >= threshold
    decision: dict[str, Any] = {
        "job_id": job_id,
        "chosen_category": chosen_category,
        "confidence": None if confidence is None else round(float(confidence), 4),
        "threshold": float(threshold),
        "threshold_side": (
            "above" if above else "below" if confidence is not None else "n/a"
        ),
        "rejected_reason": rejected_reason,
        "yolo_count": yolo_count,
        "fallback_used": fallback_used,
        "cutout_source": cutout_source,
        "pipeline_git_commit": git_commit_short(),
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
    (out_dir / "cat_decision.json").write_text(
        json.dumps(decision, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    logger.info("Category debug yazildi: %s reason=%s", out_dir, rejected_reason)
    return out_dir
