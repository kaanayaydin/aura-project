"""Hafif poz / keypoint katmani (DensePose-benzeri guiding).

Kaynak onceligi:
1) Opsiyonel ONNX (HF) — `pose_onnx_enabled`
2) SCHP LIP bolge merkezlerinden pseudo-keypoints (agirliksiz)
3) Basarisizlik → None (SCHP-only boru hatti bozulmaz)
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import numpy as np
from PIL import Image

from app.config import settings

logger = logging.getLogger("aura.vton.pose")

# COCO-17 iskeleti
COCO_SKELETON: tuple[tuple[int, int], ...] = (
    (0, 1),
    (0, 2),
    (1, 3),
    (2, 4),
    (5, 6),
    (5, 7),
    (7, 9),
    (6, 8),
    (8, 10),
    (5, 11),
    (6, 12),
    (11, 12),
    (11, 13),
    (13, 15),
    (12, 14),
    (14, 16),
)


@dataclass(frozen=True)
class PoseResult:
    """(N, 2) xy + skorlar + kaynak etiketi."""

    keypoints: np.ndarray  # float32 (17, 2)
    scores: np.ndarray  # float32 (17,)
    source: str  # onnx | schp-pseudo | none


def _centroid(mask: np.ndarray) -> Optional[tuple[float, float]]:
    ys, xs = np.where(mask)
    if len(xs) == 0:
        return None
    return float(xs.mean()), float(ys.mean())


def _quantile_points(
    mask: np.ndarray,
    *,
    qs: tuple[float, ...] = (0.2, 0.5, 0.8),
) -> list[Optional[tuple[float, float]]]:
    ys, xs = np.where(mask)
    if len(xs) == 0:
        return [None] * len(qs)
    order = np.argsort(ys)
    xs_s, ys_s = xs[order], ys[order]
    points: list[Optional[tuple[float, float]]] = []
    for q in qs:
        idx = int(np.clip(q * (len(ys_s) - 1), 0, len(ys_s) - 1))
        points.append((float(xs_s[idx]), float(ys_s[idx])))
    return points


def keypoints_from_schp_seg(seg: np.ndarray) -> PoseResult:
    """LIP etiket haritasindan yaklasik COCO-17 keypoints."""
    h, w = seg.shape
    kps = np.full((17, 2), np.nan, dtype=np.float32)
    scores = np.zeros(17, dtype=np.float32)

    face = seg == 13
    left_arm = seg == 14
    right_arm = seg == 15
    left_leg = seg == 16
    right_leg = seg == 17
    upper = np.isin(seg, [5, 6, 7, 10])
    lower = np.isin(seg, [9, 12, 6, 10])

    face_c = _centroid(face)
    if face_c:
        kps[0] = face_c
        scores[0] = 0.85
        ys, xs = np.where(face)
        if len(xs) > 0:
            x0, x1 = float(xs.min()), float(xs.max())
            y0, y1 = float(ys.min()), float(ys.max())
            cy = (y0 + y1) / 2
            kps[1] = ((x0 + face_c[0]) / 2, cy - (y1 - y0) * 0.1)
            kps[2] = ((face_c[0] + x1) / 2, cy - (y1 - y0) * 0.1)
            kps[3] = (x0, cy)
            kps[4] = (x1, cy)
            scores[1:5] = 0.55

    if upper.any():
        ys, xs = np.where(upper)
        y_top = float(np.percentile(ys, 12))
        band = (ys >= y_top - 2) & (ys <= y_top + max(4, h * 0.03))
        if band.any():
            xb, yb = xs[band], ys[band]
            mid = float(np.median(xb))
            left_sel = xb <= mid
            right_sel = xb >= mid
            if left_sel.any():
                kps[5] = (float(xb[left_sel].min()), float(np.median(yb[left_sel])))
                scores[5] = 0.75
            if right_sel.any():
                kps[6] = (float(xb[right_sel].max()), float(np.median(yb[right_sel])))
                scores[6] = 0.75

    for arm_mask, shoulder_i, elbow_i, wrist_i in (
        (left_arm, 5, 7, 9),
        (right_arm, 6, 8, 10),
    ):
        pts = _quantile_points(arm_mask, qs=(0.15, 0.5, 0.88))
        for idx, pt, score in zip(
            (shoulder_i, elbow_i, wrist_i),
            pts,
            (0.7, 0.8, 0.75),
        ):
            if pt is None:
                continue
            if np.isnan(kps[idx, 0]):
                kps[idx] = pt
                scores[idx] = score

    if lower.any():
        ys, xs = np.where(lower)
        y_hip = float(np.percentile(ys, 8))
        band = (ys >= y_hip - 2) & (ys <= y_hip + max(4, h * 0.03))
        if band.any():
            xb = xs[band]
            mid = float(np.median(xb))
            left_x = xb[xb <= mid] if (xb <= mid).any() else xb
            right_x = xb[xb >= mid] if (xb >= mid).any() else xb
            kps[11] = (float(np.percentile(left_x, 20)), y_hip)
            kps[12] = (float(np.percentile(right_x, 80)), y_hip)
            scores[11] = scores[12] = 0.7

    for leg_mask, hip_i, knee_i, ankle_i in (
        (left_leg, 11, 13, 15),
        (right_leg, 12, 14, 16),
    ):
        pts = _quantile_points(leg_mask, qs=(0.12, 0.5, 0.9))
        for idx, pt, score in zip(
            (hip_i, knee_i, ankle_i),
            pts,
            (0.65, 0.8, 0.75),
        ):
            if pt is None:
                continue
            if np.isnan(kps[idx, 0]):
                kps[idx] = pt
                scores[idx] = score

    valid_n = int((scores >= settings.pose_min_score).sum())
    if valid_n == 0:
        return PoseResult(
            keypoints=np.zeros((17, 2), dtype=np.float32),
            scores=scores,
            source="none",
        )

    torso_c = _centroid(upper | lower)
    if torso_c and np.isnan(kps[5, 0]) and not np.isnan(kps[11, 0]):
        kps[5] = ((kps[11, 0] + torso_c[0]) / 2, (kps[11, 1] + torso_c[1]) / 2)
        scores[5] = 0.4
    if torso_c and np.isnan(kps[6, 0]) and not np.isnan(kps[12, 0]):
        kps[6] = ((kps[12, 0] + torso_c[0]) / 2, (kps[12, 1] + torso_c[1]) / 2)
        scores[6] = 0.4

    return PoseResult(keypoints=kps, scores=scores, source="schp-pseudo")


class PoseOnnxEstimator:
    """Opsiyonel HF ONNX pose — lazy load. Parse desteklenmezse None (fallback)."""

    def __init__(self) -> None:
        self._session = None
        self._lock = threading.Lock()
        self._load_error: str | None = None
        self._input_name: str | None = None

    @property
    def load_error(self) -> str | None:
        return self._load_error

    def ensure_loaded(self) -> bool:
        if not settings.pose_onnx_enabled:
            return False
        with self._lock:
            if self._session is not None:
                return True
            if self._load_error and not settings.pose_retry_on_fail:
                return False
            try:
                self._load()
                return self._session is not None
            except Exception as exc:  # noqa: BLE001
                self._load_error = str(exc)
                logger.warning("Pose ONNX yuklenemedi → SCHP-pseudo: %s", exc)
                return False

    def _load(self) -> None:
        import onnxruntime as ort
        from huggingface_hub import hf_hub_download

        cache = Path(settings.cache_dir).expanduser() / "pose"
        cache.mkdir(parents=True, exist_ok=True)
        model_path = hf_hub_download(
            repo_id=settings.pose_model_id,
            filename=settings.pose_onnx_file,
            cache_dir=str(cache / "hub"),
        )
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = max(1, settings.pose_num_threads)
        self._session = ort.InferenceSession(
            model_path,
            sess_options=opts,
            providers=["CPUExecutionProvider"],
        )
        self._input_name = self._session.get_inputs()[0].name
        logger.info(
            "Pose ONNX hazir: %s / %s",
            settings.pose_model_id,
            settings.pose_onnx_file,
        )

    def estimate(self, person: Image.Image) -> Optional[PoseResult]:
        if not self.ensure_loaded() or self._session is None:
            return None
        # DWPose/RTMPose simcc ciktisi modele ozgu; hook hazir, parse sonraki dilim.
        # Graceful: None → SCHP-pseudo.
        _ = person
        logger.info("Pose ONNX session var; simcc parse yok → SCHP-pseudo fallback")
        return None


_onnx_singleton: PoseOnnxEstimator | None = None
_onnx_lock = threading.Lock()


def get_pose_onnx() -> PoseOnnxEstimator:
    global _onnx_singleton
    with _onnx_lock:
        if _onnx_singleton is None:
            _onnx_singleton = PoseOnnxEstimator()
        return _onnx_singleton


def estimate_pose(
    person: Image.Image,
    *,
    seg: np.ndarray | None = None,
) -> Optional[PoseResult]:
    """Kisi gorselinden poz. Basarisizsa None (SCHP-only devam)."""
    if not settings.pose_enabled:
        return None

    try:
        result = get_pose_onnx().estimate(person)
        if result is not None and int((result.scores >= settings.pose_min_score).sum()) >= 4:
            return result
    except Exception as exc:  # noqa: BLE001
        logger.warning("Pose ONNX yolu atlandi: %s", exc)

    try:
        if seg is None and settings.schp_enabled:
            from app.services.catvton.schp_parser import get_schp_parser

            parser = get_schp_parser()
            if parser.ensure_loaded():
                seg = parser.parse(person)
        if seg is None:
            return None
        result = keypoints_from_schp_seg(seg)
        if result.source == "none" or int((result.scores >= settings.pose_min_score).sum()) < 3:
            logger.info("Pose: yetersiz keypoint → guiding kapali")
            return None
        logger.info(
            "Pose: schp-pseudo keypoints=%s",
            int((result.scores >= settings.pose_min_score).sum()),
        )
        return result
    except Exception as exc:  # noqa: BLE001
        logger.warning("Pose tahmin basarisiz (graceful): %s", exc)
        return None
