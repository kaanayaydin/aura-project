"""Orientation debug enstrümantasyonu — kanıt dosyaları (v0.20.3 Adım 1).

Prod'da kapalı. debug=True / ?debug=1 ile `debug_output/{job_id}/` yazılır.
Skor mantığını DEĞİŞTİRMEZ; yalnızca gözlem üretir.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from app.core.build_info import git_commit_short
from app.core.config import BASE_DIR, settings

logger = logging.getLogger("aura.vision.orientation.debug")

# Skor formulu degistirilmeden kayit altina alinan sızıntı (hafif_saga).
KNOWN_ISSUE_HAFIF_SAGA_LEFT = (
    "chroma/kapali-yaka: top depth=0 ve centrality=0 (yaka deligi yok). "
    "deskew=0; hafif sag yatiklik sol kol acikligini sol kenarin dikey "
    "ortasina kaydiriyor (left centrality~1, tek nonzero depth). "
    "pair=left/right, gap>0.15 → yanlis HIGH + 270. "
    "Bu turda skor formulu degistirilmedi."
)


def annotate_known_orientation_issues(decision: dict[str, Any]) -> dict[str, Any]:
    """Yüksek-güven yanlış rotasyon imzasını decision.json'a yazar (skor değiştirmez)."""
    scores = decision.get("scores") or {}
    detail = scores.get("detail") or {}
    left = detail.get("left") or {}
    top = detail.get("top") or {}
    deg = int(decision.get("rotation_deg_applied") or 0)
    low = bool(decision.get("low_confidence"))
    best = str(decision.get("best_edge") or "")
    if (
        not low
        and deg in (90, 270)
        and best in ("left", "right")
        and float(top.get("depth_score") or 0.0) <= 0.05
        and float(left.get("centrality_score") or 0.0) >= 0.8
    ):
        decision["known_issue_id"] = "hafif_saga_false_high_left"
        decision["known_issue_note"] = KNOWN_ISSUE_HAFIF_SAGA_LEFT
    return decision

_EDGE_COLORS = {
    "top": (220, 50, 50),
    "right": (40, 180, 80),
    "bottom": (50, 90, 220),
    "left": (230, 180, 40),
}


@dataclass
class OrientationTrace:
    """Polish zinciri boyunca toplanan ara görseller + karar."""

    job_id: str
    mask_raw: Optional[Image.Image] = None
    mask_deskewed: Optional[Image.Image] = None
    candidates: Optional[Image.Image] = None
    mask_rotated: Optional[Image.Image] = None
    framed: Optional[Image.Image] = None
    scores: dict[str, Any] = field(default_factory=dict)
    best_edge: str = "top"
    deskew_angle_applied: float = 0.0
    deskew_step_executed: Optional[bool] = None
    deskew_input_angle_estimated: Optional[float] = None
    deskew_skip_reason: Optional[str] = None
    deskew_min_abs_deg: Optional[float] = None
    rotation_deg_applied: int = 0
    rotation_method: str = "none"
    cutout_source: str = ""
    low_confidence: bool = False
    rotation_suggested: str = "top"
    requires_confirmation: bool = False
    ensemble_confidence: str = ""


def debug_root() -> Path:
    raw = getattr(settings, "studio_debug_dir", None)
    if raw:
        return Path(str(raw))
    return BASE_DIR / "debug_output"


def _alpha_preview(rgba: Image.Image, *, size: tuple[int, int] | None = None) -> Image.Image:
    """RGBA cutout → siyah zemin + gri/renkli giysi onizleme."""
    img = rgba.convert("RGBA")
    if size is not None:
        img = img.copy()
        img.thumbnail(size, Image.Resampling.BILINEAR)
    bg = Image.new("RGB", img.size, (24, 24, 24))
    bg.paste(img.convert("RGB"), mask=img.split()[-1])
    return bg


def _mask_only(rgba: Image.Image) -> Image.Image:
    alpha = np.asarray(rgba.convert("RGBA").split()[-1], dtype=np.uint8)
    return Image.fromarray(alpha, mode="L").convert("RGB")


def render_candidates_overlay(rgba: Image.Image, scores: dict[str, Any]) -> Image.Image:
    """4 kenarı renkli bant + skor yazısı ile çiz."""
    preview = _alpha_preview(rgba)
    w, h = preview.size
    draw = ImageDraw.Draw(preview)
    try:
        font = ImageFont.load_default()
    except Exception:
        font = None

    band = max(6, min(w, h) // 28)
    combined = scores.get("combined") or {}
    detail = scores.get("detail") or {}
    best = str(scores.get("best") or "")

    # Kenar bantları
    draw.rectangle([0, 0, w - 1, band], outline=_EDGE_COLORS["top"], width=band)
    draw.rectangle([w - band, 0, w - 1, h - 1], outline=_EDGE_COLORS["right"], width=band)
    draw.rectangle([0, h - band, w - 1, h - 1], outline=_EDGE_COLORS["bottom"], width=band)
    draw.rectangle([0, 0, band, h - 1], outline=_EDGE_COLORS["left"], width=band)

    lines = []
    for edge, xy in (
        ("top", (band + 4, 2)),
        ("right", (max(band + 4, w - 170), band + 4)),
        ("bottom", (band + 4, h - band - 14)),
        ("left", (band + 4, h // 2)),
    ):
        tot = combined.get(edge, 0.0)
        d = detail.get(edge) or {}
        mark = "*" if edge == best else " "
        txt = "{0}{1} tot={2:.2f} d={3:.2f} s={4:.2f} c={5:.2f}".format(
            mark,
            edge.upper(),
            float(d.get("total", tot)),
            float(d.get("depth_score", 0.0)),
            float(d.get("symmetry_score", 0.0)),
            float(d.get("centrality_score", 0.0)),
        )
        lines.append((txt, xy, _EDGE_COLORS[edge]))

    for txt, xy, color in lines:
        draw.text(xy, txt, fill=color, font=font)
    return preview


def _hstack_labeled(panels: list[tuple[str, Image.Image]], *, height: int = 280) -> Image.Image:
    """Yatay şerit: etiketli paneller."""
    resized: list[Image.Image] = []
    for _label, img in panels:
        rgb = img.convert("RGB")
        scale = height / max(rgb.size[1], 1)
        nw = max(1, int(round(rgb.size[0] * scale)))
        resized.append(rgb.resize((nw, height), Image.Resampling.BILINEAR))

    gap = 8
    label_h = 22
    total_w = sum(p.size[0] for p in resized) + gap * (len(resized) + 1)
    canvas = Image.new("RGB", (total_w, height + label_h + 8), (18, 18, 18))
    draw = ImageDraw.Draw(canvas)
    try:
        font = ImageFont.load_default()
    except Exception:
        font = None
    x = gap
    for (label, _src), panel in zip(panels, resized):
        canvas.paste(panel, (x, label_h))
        draw.text((x, 4), label, fill=(240, 240, 240), font=font)
        x += panel.size[0] + gap
    return canvas


def write_orientation_debug(trace: OrientationTrace) -> Path:
    """debug_output/{job_id}/ altına kanıt dosyalarını yazar."""
    out_dir = debug_root() / trace.job_id
    out_dir.mkdir(parents=True, exist_ok=True)
    commit = git_commit_short()

    raw = trace.mask_raw or Image.new("RGB", (8, 8), (0, 0, 0))
    deskewed = trace.mask_deskewed or raw
    rotated = trace.mask_rotated or deskewed
    candidates = trace.candidates
    if candidates is None and trace.mask_deskewed is not None:
        candidates = render_candidates_overlay(deskewed, trace.scores)
    if candidates is None:
        candidates = _mask_only(deskewed)

    _mask_only(raw).save(out_dir / "1_mask_raw.png")
    _mask_only(deskewed).save(out_dir / "2_mask_deskewed.png")
    candidates.convert("RGB").save(out_dir / "3_candidates.png")
    _alpha_preview(rotated).save(out_dir / "4_rotation_applied.png")

    strip = _hstack_labeled(
        [
            ("1 raw mask", _mask_only(raw)),
            ("2 deskewed", _mask_only(deskewed)),
            ("3 candidates", candidates.convert("RGB")),
            ("4 rotated", _alpha_preview(rotated)),
        ]
    )
    strip.save(out_dir / "debug_strip.png")

    if trace.framed is not None:
        fname = "result_{0}_{1}.png".format(trace.job_id, commit)
        trace.framed.convert("RGB").save(out_dir / fname)

    decision = {
        "job_id": trace.job_id,
        "best_edge": trace.best_edge,
        "scores": trace.scores,
        "deskew_step_executed": (
            True if trace.deskew_step_executed is None else bool(trace.deskew_step_executed)
        ),
        "deskew_input_angle_estimated": trace.deskew_input_angle_estimated,
        "deskew_angle_applied": trace.deskew_angle_applied,
        "deskew_skip_reason": trace.deskew_skip_reason,
        "deskew_min_abs_deg": trace.deskew_min_abs_deg,
        "rotation_deg_applied": trace.rotation_deg_applied,
        "rotation_method": trace.rotation_method,
        "rotation_suggested": trace.rotation_suggested or trace.best_edge,
        "requires_confirmation": bool(
            trace.requires_confirmation
            or trace.low_confidence
            or (trace.scores or {}).get("low_confidence")
        ),
        "low_confidence": bool(
            trace.low_confidence or (trace.scores or {}).get("low_confidence")
        ),
        "candidate_pair": (trace.scores or {}).get("candidate_pair"),
        "pipeline_git_commit": commit,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "cutout_source": trace.cutout_source,
        "result_filename": "result_{0}_{1}.png".format(trace.job_id, commit),
        "ensemble": (trace.scores or {}).get("ensemble"),
        "rotnet": (trace.scores or {}).get("rotnet"),
        "geometric": (trace.scores or {}).get("geometric"),
        "ensemble_confidence": trace.ensemble_confidence
        or (trace.scores or {}).get("ensemble_confidence"),
    }
    decision = annotate_known_orientation_issues(decision)
    (out_dir / "decision.json").write_text(
        json.dumps(decision, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    logger.info("Orientation debug yazildi: %s", out_dir)
    return out_dir
