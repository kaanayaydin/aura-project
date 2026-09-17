"""Poz haritasi → CatVTON conditioning hint (mimariyi bozmadan).

1) Agnostic maskeyi eklem hatlari boyunca genislet (drapaj bolgesi)
2) Maske icinde iskelet boyunca soft structure birak (VAE'ye geometri ipucu)
"""

from __future__ import annotations

import logging
from typing import Literal

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from app.config import settings
from app.services.catvton.pose_estimator import COCO_SKELETON, PoseResult

logger = logging.getLogger("aura.vton.pose_guide")

ClothType = Literal["upper", "lower", "overall"]

# cloth_type → hangi iskelet kenarlari maskeye dahil
_UPPER_EDGES = {
    (5, 6),
    (5, 7),
    (7, 9),
    (6, 8),
    (8, 10),
    (5, 11),
    (6, 12),
    (11, 12),
}
_LOWER_EDGES = {
    (11, 12),
    (11, 13),
    (13, 15),
    (12, 14),
    (14, 16),
    (5, 11),
    (6, 12),
}


def _valid_points(pose: PoseResult, min_score: float) -> np.ndarray:
    return pose.scores >= min_score


def render_pose_map(
    size: tuple[int, int],
    pose: PoseResult,
    *,
    min_score: float | None = None,
    line_width: int | None = None,
) -> Image.Image:
    """RGB OpenPose-benzeri stick figure (siyah zemin)."""
    w, h = size
    canvas = Image.new("RGB", (w, h), (0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    thr = settings.pose_min_score if min_score is None else min_score
    valid = _valid_points(pose, thr)
    lw = line_width or max(2, min(w, h) // 120)

    colors = [
        (255, 0, 0),
        (255, 85, 0),
        (255, 170, 0),
        (255, 255, 0),
        (170, 255, 0),
        (85, 255, 0),
        (0, 255, 0),
        (0, 255, 85),
        (0, 255, 170),
        (0, 255, 255),
        (0, 170, 255),
        (0, 85, 255),
        (0, 0, 255),
        (85, 0, 255),
        (170, 0, 255),
        (255, 0, 255),
    ]

    for i, (a, b) in enumerate(COCO_SKELETON):
        if not (valid[a] and valid[b]):
            continue
        if np.isnan(pose.keypoints[a]).any() or np.isnan(pose.keypoints[b]).any():
            continue
        x0, y0 = pose.keypoints[a]
        x1, y1 = pose.keypoints[b]
        draw.line([(x0, y0), (x1, y1)], fill=colors[i % len(colors)], width=lw)

    for i in range(17):
        if not valid[i] or np.isnan(pose.keypoints[i]).any():
            continue
        x, y = pose.keypoints[i]
        r = max(2, lw)
        draw.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255))

    return canvas


def refine_mask_with_pose(
    mask: Image.Image,
    pose: PoseResult,
    *,
    cloth_type: ClothType = "upper",
) -> Image.Image:
    """Agnostic maskeyi poz eklem hatlariyla genislet (kol/bacak drapaji)."""
    if not settings.pose_refine_mask:
        return mask

    w, h = mask.size
    limb = Image.new("L", (w, h), 0)
    draw = ImageDraw.Draw(limb)
    thr = settings.pose_min_score
    valid = _valid_points(pose, thr)
    lw = max(6, min(w, h) // 28)

    if cloth_type == "lower":
        edges = _LOWER_EDGES
    elif cloth_type == "overall":
        edges = _UPPER_EDGES | _LOWER_EDGES
    else:
        edges = _UPPER_EDGES

    for a, b in edges:
        if not (valid[a] and valid[b]):
            continue
        if np.isnan(pose.keypoints[a]).any() or np.isnan(pose.keypoints[b]).any():
            continue
        x0, y0 = pose.keypoints[a]
        x1, y1 = pose.keypoints[b]
        draw.line([(x0, y0), (x1, y1)], fill=255, width=lw)

    # Omuz / kalca noktalarini genislet
    for idx in ((5, 6, 11, 12) if cloth_type != "lower" else (11, 12, 13, 14, 15, 16)):
        if idx >= 17 or not valid[idx] or np.isnan(pose.keypoints[idx]).any():
            continue
        x, y = pose.keypoints[idx]
        r = lw
        draw.ellipse([x - r, y - r, x + r, y + r], fill=255)

    limb = limb.filter(ImageFilter.MaxFilter(size=5))
    base = np.array(mask, dtype=np.uint8)
    add = np.array(limb, dtype=np.uint8)
    merged = np.maximum(base, add)
    out = Image.fromarray(merged, mode="L")
    blur = max(3, min(w, h) // 100)
    if blur % 2 == 0:
        blur += 1
    logger.info("Pose mask refine: cloth=%s limb_px=%s", cloth_type, int((add > 0).sum()))
    return out.filter(ImageFilter.GaussianBlur(radius=blur))


def apply_pose_soft_structure(
    mask: Image.Image,
    pose: PoseResult,
    *,
    strength: float | None = None,
) -> Image.Image:
    """Inpaint maskesinde iskelet boyunca soft residual birak (conditioning hint).

    CatVTON masked_image = image * (mask < 0.5); maskeyi iskelette dusurerek
    VAE'ye beden geometrisi ipucu kalir — UNet mimarisi degismez.
    """
    alpha = settings.pose_hint_alpha if strength is None else strength
    if alpha <= 0:
        return mask

    pose_map = render_pose_map(mask.size, pose, line_width=max(3, min(mask.size) // 90))
    gray = np.asarray(pose_map.convert("L"), dtype=np.float32) / 255.0
    # Kalinlastir
    from PIL import ImageFilter as _IF

    thick = (
        Image.fromarray((gray * 255).astype(np.uint8), mode="L")
        .filter(_IF.MaxFilter(size=7))
        .filter(_IF.GaussianBlur(radius=3))
    )
    bone = np.asarray(thick, dtype=np.float32) / 255.0
    m = np.asarray(mask, dtype=np.float32)
    # Beyaz maske (255) → iskelette (1-alpha*bone) oraninda dusur
    softened = m * (1.0 - float(alpha) * bone)
    softened = np.clip(softened, 0, 255).astype(np.uint8)
    logger.info("Pose soft-structure alpha=%.2f bone_px=%s", alpha, int((bone > 0.2).sum()))
    return Image.fromarray(softened, mode="L")


def guide_mask_with_pose(
    mask: Image.Image,
    pose: PoseResult | None,
    *,
    cloth_type: ClothType = "upper",
    protect_mask: Image.Image | None = None,
) -> tuple[Image.Image, str]:
    """Maske + opsiyonel poz guiding. pose None ise dokunulmaz.

    upper icin yatay cut_y yok; pose genisletmesi SCHP protect (pants|legs) ile kirpilir.
    """
    if pose is None:
        return mask, "none"
    try:
        from app.services.catvton.mask import apply_protect_to_mask

        refined = refine_mask_with_pose(mask, pose, cloth_type=cloth_type)
        guided = apply_pose_soft_structure(refined, pose)
        if cloth_type == "upper" and protect_mask is not None:
            guided = apply_protect_to_mask(guided, protect_mask)
            logger.info("Pose semantic protect re-applied")
        return guided, pose.source
    except Exception as exc:  # noqa: BLE001
        logger.warning("Pose guiding basarisiz, SCHP maskesi korunuyor: %s", exc)
        return mask, "none"
