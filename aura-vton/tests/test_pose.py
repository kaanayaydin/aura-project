"""Pose guiding testleri — ONNX indirmeden (sentetik SCHP seg + keypoints)."""

import numpy as np
from PIL import Image

from app.config import settings
from app.services.catvton.pose_estimator import (
    PoseResult,
    estimate_pose,
    keypoints_from_schp_seg,
)
from app.services.catvton.pose_guide import (
    apply_pose_soft_structure,
    guide_mask_with_pose,
    refine_mask_with_pose,
    render_pose_map,
)


def _synthetic_upper_seg(h: int = 120, w: int = 80) -> np.ndarray:
    seg = np.zeros((h, w), dtype=np.int64)
    # Yuz
    seg[8:22, 30:50] = 13
    # Ust giysi
    seg[24:55, 22:58] = 5
    # Kollar
    seg[28:52, 8:20] = 14
    seg[28:52, 60:72] = 15
    # Pantolon (alt)
    seg[56:100, 26:54] = 9
    # Bacaklar
    seg[70:110, 28:38] = 16
    seg[70:110, 42:52] = 17
    return seg


def test_keypoints_from_schp_seg_finds_shoulders_and_hips():
    seg = _synthetic_upper_seg()
    pose = keypoints_from_schp_seg(seg)
    assert pose.source == "schp-pseudo"
    assert pose.keypoints.shape == (17, 2)
    assert (pose.scores >= 0.35).sum() >= 4
    # Omuzlar / kalcalar dolu olmali
    assert not np.isnan(pose.keypoints[5]).any() or not np.isnan(pose.keypoints[6]).any()


def test_render_pose_map_draws_non_black_pixels():
    pose = PoseResult(
        keypoints=np.array(
            [
                [40, 20],
                [35, 18],
                [45, 18],
                [30, 20],
                [50, 20],
                [25, 40],
                [55, 40],
                [20, 60],
                [60, 60],
                [18, 80],
                [62, 80],
                [30, 90],
                [50, 90],
                [28, 110],
                [52, 110],
                [26, 130],
                [54, 130],
            ],
            dtype=np.float32,
        ),
        scores=np.ones(17, dtype=np.float32),
        source="synthetic",
    )
    canvas = render_pose_map((80, 140), pose)
    arr = np.array(canvas)
    assert arr.shape == (140, 80, 3)
    assert arr.max() > 0


def test_refine_mask_with_pose_expands_upper_limbs(monkeypatch):
    monkeypatch.setattr(settings, "pose_refine_mask", True)
    monkeypatch.setattr(settings, "pose_min_score", 0.3)
    base = Image.new("L", (80, 120), 0)
    # Kucuk torso
    for y in range(30, 55):
        for x in range(30, 50):
            base.putpixel((x, y), 255)

    pose = PoseResult(
        keypoints=np.full((17, 2), np.nan, dtype=np.float32),
        scores=np.zeros(17, dtype=np.float32),
        source="synthetic",
    )
    # Sol kol hattı
    pose.keypoints[5] = (25, 40)
    pose.keypoints[7] = (15, 55)
    pose.keypoints[9] = (10, 70)
    pose.scores[5] = pose.scores[7] = pose.scores[9] = 0.9
    pose.keypoints[6] = (55, 40)
    pose.scores[6] = 0.9

    refined = refine_mask_with_pose(base, pose, cloth_type="upper")
    arr = np.array(refined)
    assert arr[55, 15] > 0 or arr.max() > np.array(base).sum() / 255


def test_soft_structure_reduces_mask_along_bones(monkeypatch):
    monkeypatch.setattr(settings, "pose_hint_alpha", 0.5)
    monkeypatch.setattr(settings, "pose_min_score", 0.3)
    mask = Image.new("L", (60, 80), 255)
    pose = PoseResult(
        keypoints=np.full((17, 2), np.nan, dtype=np.float32),
        scores=np.zeros(17, dtype=np.float32),
        source="synthetic",
    )
    pose.keypoints[5] = (20, 20)
    pose.keypoints[7] = (20, 40)
    pose.keypoints[9] = (20, 60)
    pose.scores[5] = pose.scores[7] = pose.scores[9] = 1.0

    soft = apply_pose_soft_structure(mask, pose)
    soft_arr = np.array(soft)
    # Iskelet hatti civarinda maske 255'ten dusuk olmali
    assert soft_arr[40, 20] < 255


def test_guide_mask_none_pose_passthrough():
    mask = Image.new("L", (40, 60), 128)
    out, source = guide_mask_with_pose(mask, None, cloth_type="upper")
    assert source == "none"
    assert list(out.getdata()) == list(mask.getdata())


def test_estimate_pose_disabled_returns_none(monkeypatch):
    monkeypatch.setattr(settings, "pose_enabled", False)
    person = Image.new("RGB", (64, 96), color=(50, 50, 50))
    assert estimate_pose(person) is None


def test_estimate_pose_from_seg_without_onnx(monkeypatch):
    monkeypatch.setattr(settings, "pose_enabled", True)
    monkeypatch.setattr(settings, "pose_onnx_enabled", False)
    monkeypatch.setattr(settings, "pose_min_score", 0.3)
    person = Image.new("RGB", (80, 120), color=(40, 40, 40))
    seg = _synthetic_upper_seg()
    pose = estimate_pose(person, seg=seg)
    assert pose is not None
    assert pose.source == "schp-pseudo"


def test_config_drape_hyperparams():
    assert settings.num_inference_steps >= 30
    assert 1.0 <= settings.guidance_scale <= 5.0
