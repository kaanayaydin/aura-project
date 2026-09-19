"""Garment studio polish — askı, yaka yönelimi, deskew, flat-lay press (v0.20.4).

Cutout sonrası / stüdyo framing öncesi:
1) Askı temizliği
2) Yaka (neckline) ile 0/90/180/270 upright
3) PCA ince deskew (±45°)
4) Frekans ayrıştırmalı güçlü katalog ütüleme
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Literal, Optional, Tuple

import numpy as np
from PIL import Image

from app.core.config import settings

logger = logging.getLogger("aura.vision.studio.polish")


def _alpha_u8(rgba: Image.Image) -> np.ndarray:
    if rgba.mode != "RGBA":
        rgba = rgba.convert("RGBA")
    return np.asarray(rgba.split()[-1], dtype=np.uint8)


def _apply_alpha(rgba: Image.Image, alpha: np.ndarray) -> Image.Image:
    out = rgba.convert("RGBA")
    alpha = np.asarray(alpha, dtype=np.uint8)
    if alpha.shape[0] != out.size[1] or alpha.shape[1] != out.size[0]:
        raise ValueError(
            f"alpha boyut uyusmazligi: alpha={alpha.shape} imageWH={out.size}"
        )
    out.putalpha(Image.fromarray(alpha, mode="L"))
    return out


def _bbox_mask(binary: np.ndarray) -> Tuple[int, int, int, int] | None:
    ys, xs = np.where(binary)
    if ys.size == 0:
        return None
    return int(ys.min()), int(ys.max()) + 1, int(xs.min()), int(xs.max()) + 1


def remove_hanger_artifacts(
    rgba: Image.Image,
    *,
    top_frac: float = 0.28,
    width_ratio_thr: float = 0.38,
    morph_open_px: int = 3,
) -> Image.Image:
    """Yaka üstü ince askı / metal-plastik çıkıntıları ve dar parazitleri sil."""
    if rgba.mode != "RGBA":
        rgba = rgba.convert("RGBA")
    alpha = _alpha_u8(rgba).copy()
    binary = alpha > 127
    if not np.any(binary):
        return rgba

    try:
        import cv2  # type: ignore
    except Exception:
        logger.warning("cv2 yok — hanger temizligi atlandi")
        return rgba

    h, w = alpha.shape
    mask = np.where(binary, 255, 0).astype(np.uint8)

    k = max(3, int(morph_open_px))
    if k % 2 == 0:
        k += 1
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k))
    opened = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel, iterations=1)

    ys, xs = np.where(opened > 0)
    if ys.size == 0:
        return _apply_alpha(rgba, np.zeros_like(alpha))

    y0, y1 = int(ys.min()), int(ys.max())
    body_h = max(1, y1 - y0 + 1)
    row_widths = (opened > 0).sum(axis=1).astype(np.float64)
    mid_a = y0 + int(body_h * 0.25)
    mid_b = y0 + int(body_h * 0.75)
    mid_widths = row_widths[mid_a : mid_b + 1]
    ref_w = float(np.median(mid_widths)) if mid_widths.size else float(row_widths.max())
    ref_w = max(ref_w, 1.0)

    search_end = min(h - 1, y0 + max(2, int(body_h * top_frac)))
    shoulder_y = y0
    for y in range(y0, search_end + 1):
        if row_widths[y] >= width_ratio_thr * ref_w:
            shoulder_y = y
            break
    else:
        shoulder_y = search_end

    pad = max(1, int(body_h * 0.02))
    clear_until = max(y0, shoulder_y - pad)
    cleaned = opened.copy()
    for y in range(y0, clear_until):
        if row_widths[y] < width_ratio_thr * ref_w:
            cleaned[y, :] = 0

    top_band = cleaned.copy()
    top_band[shoulder_y:, :] = 0
    num_labels, labels, stats, _ = cv2.connectedComponentsWithStats(top_band, connectivity=8)
    if num_labels > 1:
        for lab in range(1, num_labels):
            area = int(stats[lab, cv2.CC_STAT_AREA])
            bw = int(stats[lab, cv2.CC_STAT_WIDTH])
            bh = int(stats[lab, cv2.CC_STAT_HEIGHT])
            thin = (bw <= max(4, int(0.12 * ref_w))) or (
                bh > 2 * max(bw, 1) and area < 0.04 * ref_w * body_h
            )
            small = area < max(20, int(0.015 * ref_w * body_h))
            if thin or small:
                cleaned[labels == lab] = 0

    from app.services.garment_studio import keep_largest_opaque_component

    alpha_out = np.where(cleaned > 0, alpha, 0).astype(np.uint8)
    alpha_out = keep_largest_opaque_component(alpha_out)

    removed = int(((mask > 0) & (alpha_out == 0)).sum())
    if removed > 0:
        logger.info(
            "Hanger temizligi: omuz_y=%s ref_w=%.0f silinen_px=%s",
            shoulder_y,
            ref_w,
            removed,
        )
    return _apply_alpha(rgba, alpha_out)


# ---------------------------------------------------------------------------
# Axis-snap deskew + 4-kenar yaka tespiti (neck → North)
# Uzun kenarı dikeye zorlama YOK; yaka Doğu/Batı/Güney'deyse 90/180/270.
# ---------------------------------------------------------------------------


def _row_widths(binary_crop: np.ndarray) -> np.ndarray:
    return binary_crop.sum(axis=1).astype(np.float64)


def _largest_contour(binary: np.ndarray):
    try:
        import cv2  # type: ignore
    except Exception:
        return None
    mask = np.where(binary, 255, 0).astype(np.uint8)
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None
    return max(contours, key=cv2.contourArea)


def _warp_rotate_rgba(rgba: Image.Image, rot_ccw_deg: float) -> Image.Image:
    """Tek seferlik sürekli açı döndürme (expand canvas)."""
    if abs(rot_ccw_deg) < 1e-3:
        return rgba
    try:
        import cv2  # type: ignore

        arr = np.asarray(rgba.convert("RGBA"))
        h, w = arr.shape[:2]
        center = (w / 2.0, h / 2.0)
        m = cv2.getRotationMatrix2D(center, float(rot_ccw_deg), 1.0)
        cos = abs(m[0, 0])
        sin = abs(m[0, 1])
        nw = int(h * sin + w * cos)
        nh = int(h * cos + w * sin)
        m[0, 2] += (nw / 2.0) - center[0]
        m[1, 2] += (nh / 2.0) - center[1]
        rotated = cv2.warpAffine(
            arr,
            m,
            (nw, nh),
            flags=cv2.INTER_LINEAR,
            borderMode=cv2.BORDER_CONSTANT,
            borderValue=(0, 0, 0, 0),
        )
        return Image.fromarray(rotated, mode="RGBA")
    except Exception as exc:  # noqa: BLE001
        logger.warning("warpAffine basarisiz (%s) — PIL", exc)
        return rgba.rotate(
            rot_ccw_deg, resample=Image.Resampling.BICUBIC, expand=True, fillcolor=(0, 0, 0, 0)
        )


def estimate_deskew_angle_deg(alpha: np.ndarray) -> float:
    """Eksen hizası için sürekli açı — en yakın yatay/dikeye snap (≤45°).

    Uzun kenarı dikeye zorlamaz. OpenCV minAreaRect angle doğrudan uygulanır
    (işaret: image'i -box_angle ile değil, normalize edilmiş angle ile çevir).
    """
    binary = alpha > 127
    if int(binary.sum()) < 50:
        return 0.0

    try:
        import cv2  # type: ignore

        contour = _largest_contour(binary)
        if contour is not None and len(contour) >= 5:
            (_cx, _cy), (_rw, _rh), angle = cv2.minAreaRect(contour)
            rot = float(angle)
            while rot > 45.0:
                rot -= 90.0
            while rot < -45.0:
                rot += 90.0
            return float(rot)
    except Exception:
        pass

    ys, xs = np.where(binary)
    pts = np.column_stack([xs.astype(np.float64), ys.astype(np.float64)])
    if pts.shape[0] < 20:
        return 0.0
    try:
        mean = pts.mean(axis=0)
        centered = pts - mean
        cov = np.cov(centered.T)
        eigvals, eigvecs = np.linalg.eigh(cov)
        axis = eigvecs[:, int(np.argmax(eigvals))]
        ang = float(np.degrees(np.arctan2(axis[1], axis[0])))
        # En yakın eksene kısa yol
        targets = (0.0, 90.0, -90.0, 180.0, -180.0)
        target = min(targets, key=lambda t: abs(((ang - t + 180.0) % 360.0) - 180.0))
        needed = ((target - ang + 180.0) % 360.0) - 180.0
        while needed > 45.0:
            needed -= 90.0
        while needed < -45.0:
            needed += 90.0
        return float(needed)
    except Exception:
        return 0.0


@dataclass
class DeskewReport:
    """Deskew 0°: adım atlandı mı, yoksa ölçülen açı eşiğin altında mı?"""

    executed: bool
    input_angle_estimated: float
    angle_applied: float
    skip_reason: Optional[str]
    min_abs_deg: float
    max_abs_deg: float


def deskew_garment_detailed(
    rgba: Image.Image,
    *,
    max_abs_deg: float = 45.0,
    min_abs_deg: Optional[float] = None,
) -> Tuple[Image.Image, DeskewReport]:
    """Deskew çalıştırır; 0° uygulansa bile ölçümü ve skip nedenini döner."""
    if min_abs_deg is None:
        min_abs_deg = float(settings.studio_deskew_min_abs_deg)
    if rgba.mode != "RGBA":
        rgba = rgba.convert("RGBA")
    alpha = _alpha_u8(rgba)
    estimated = float(estimate_deskew_angle_deg(alpha))
    clipped = float(np.clip(estimated, -abs(max_abs_deg), abs(max_abs_deg)))
    if abs(clipped) < float(min_abs_deg):
        report = DeskewReport(
            executed=True,
            input_angle_estimated=estimated,
            angle_applied=0.0,
            skip_reason="angle_below_min_threshold",
            min_abs_deg=float(min_abs_deg),
            max_abs_deg=float(max_abs_deg),
        )
        logger.info(
            "Deskew executed, apply skipped: estimated=%.3f min_abs=%.3f",
            estimated,
            min_abs_deg,
        )
        return rgba, report
    out = _warp_rotate_rgba(rgba, clipped)
    report = DeskewReport(
        executed=True,
        input_angle_estimated=estimated,
        angle_applied=float(clipped),
        skip_reason=None,
        min_abs_deg=float(min_abs_deg),
        max_abs_deg=float(max_abs_deg),
    )
    logger.info("Continuous deskew (axis-snap): rotate=%.2f deg (est=%.2f)", clipped, estimated)
    return out, report


def deskew_garment(
    rgba: Image.Image,
    *,
    max_abs_deg: float = 45.0,
    min_abs_deg: float = 0.5,
) -> Tuple[Image.Image, float]:
    """Sürekli açı ile eksenlere oturt (snap ≤45°). Uzun kenar dikeye zorlanmaz."""
    out, report = deskew_garment_detailed(
        rgba, max_abs_deg=max_abs_deg, min_abs_deg=min_abs_deg
    )
    return out, report.angle_applied


def _edge_depths(crop: np.ndarray, side: str) -> np.ndarray:
    """Kenardan içeri ilk opak mesafe dizisi (NaN = boş sütun/satır)."""
    ch, cw = crop.shape
    if side == "top":
        depths = np.full(cw, np.nan, dtype=np.float64)
        for x in range(cw):
            hits = np.flatnonzero(crop[:, x])
            if hits.size:
                depths[x] = float(hits[0])
        return depths
    if side == "bottom":
        depths = np.full(cw, np.nan, dtype=np.float64)
        for x in range(cw):
            hits = np.flatnonzero(crop[:, x])
            if hits.size:
                depths[x] = float(ch - 1 - hits[-1])
        return depths
    if side == "left":
        depths = np.full(ch, np.nan, dtype=np.float64)
        for y in range(ch):
            hits = np.flatnonzero(crop[y, :])
            if hits.size:
                depths[y] = float(hits[0])
        return depths
    # right
    depths = np.full(ch, np.nan, dtype=np.float64)
    for y in range(ch):
        hits = np.flatnonzero(crop[y, :])
        if hits.size:
            depths[y] = float(cw - 1 - hits[-1])
    return depths


def edge_neckline_score(binary: np.ndarray, side: str) -> float:
    """Bir kenarın orta %40 dilimindeki yaka (içbükey çukur) skoru.

    Yaka: omuzlar arasında içbükey + merkez şeffaf, omuzlar opak.
    Etek: düz/dolu.
    Kol açıklığı (uzun kenar): bbox'ın uzun kenarında sahte çukur üretir —
    bu yüzden kısa kenar (neck–hem ekseni) prior ile ödüllendirilir.
    """
    bb = _bbox_mask(binary)
    if bb is None:
        return -1e9
    y0, y1, x0, x1 = bb
    crop = binary[y0:y1, x0:x1]
    ch, cw = crop.shape
    if ch < 12 or cw < 12:
        return -1e9

    depths = _edge_depths(crop, side)
    valid = ~np.isnan(depths)
    if int(valid.sum()) < 6:
        return -1e9

    idx = np.where(valid)[0]
    i0 = int(len(idx) * 0.30)
    i1 = int(len(idx) * 0.70)
    side_n = max(1, int(len(idx) * 0.15))
    center_idx = idx[i0:i1]
    shoulder_idx = np.concatenate([idx[:side_n], idx[-side_n:]])
    if center_idx.size == 0 or shoulder_idx.size == 0:
        return -1e9

    center_d = float(np.median(depths[center_idx]))
    shoulder_d = float(np.median(depths[shoulder_idx]))
    span = float(ch if side in ("top", "bottom") else cw)
    notch = (center_d - shoulder_d) / max(span * 0.25, 1.0)

    tip_n = max(3, (ch if side in ("top", "bottom") else cw) // 10)
    c0 = int(len(idx) * 0.30)
    c1 = int(len(idx) * 0.70)
    # Uç dilimde omuz dolu / merkez boş (gerçek yaka)
    if side == "top":
        tip = crop[:tip_n, :]
        tip_center = float(tip[:, int(cw * 0.30) : int(cw * 0.70)].mean())
        tip_side = float(
            np.concatenate([tip[:, : max(1, cw // 6)], tip[:, cw - max(1, cw // 6) :]]).mean()
        )
    elif side == "bottom":
        tip = crop[-tip_n:, :]
        tip_center = float(tip[:, int(cw * 0.30) : int(cw * 0.70)].mean())
        tip_side = float(
            np.concatenate([tip[:, : max(1, cw // 6)], tip[:, cw - max(1, cw // 6) :]]).mean()
        )
    elif side == "left":
        tip = crop[:, :tip_n]
        tip_center = float(tip[int(ch * 0.30) : int(ch * 0.70), :].mean())
        tip_side = float(
            np.concatenate([tip[: max(1, ch // 6), :], tip[ch - max(1, ch // 6) :, :]]).mean()
        )
    else:
        tip = crop[:, -tip_n:]
        tip_center = float(tip[int(ch * 0.30) : int(ch * 0.70), :].mean())
        tip_side = float(
            np.concatenate([tip[: max(1, ch // 6), :], tip[ch - max(1, ch // 6) :, :]]).mean()
        )

    # Gerçek yaka: yanlar opak, orta boş
    neck_gap = float(tip_side - tip_center)
    opening = float(1.0 - tip_center)

    d_std = float(np.std(depths[valid]))
    flatness = float(np.clip(1.0 - d_std / max(span * 0.15, 1.0), 0.0, 1.0))

    raw = (
        2.8 * max(notch, 0.0)
        + 3.0 * max(neck_gap, 0.0)
        + 2.0 * opening
        - 2.0 * flatness * tip_center
    )

    # Kısa kenar prior: kol–kol uzun kenarındaki sahte çukuru bastır
    edge_len = float(cw if side in ("top", "bottom") else ch)
    short = float(min(ch, cw))
    prior = short / max(edge_len, 1.0)  # 1.0 kısa kenarda, <1 uzun kenarda
    score = raw * (0.25 + 0.75 * prior)
    return float(score)


def _geometric_edge_metrics(binary: np.ndarray, side: str) -> dict[str, float]:
    """Basit geometrik yaka/etek metrikleri (bir kenar).

    Yaka: iki uçta omuz tepeleri, ortada derin çukur (yüksek notch, düşük tip_center).
    Etek: boydan boya dolu, düşük varyans (yüksek flatness, yüksek tip_center).
    """
    bb = _bbox_mask(binary)
    empty = {"neck": -1e9, "hem": -1e9, "notch": 0.0, "flatness": 0.0, "tip_center": 1.0, "tip_side": 0.0}
    if bb is None:
        return empty
    y0, y1, x0, x1 = bb
    crop = binary[y0:y1, x0:x1]
    ch, cw = crop.shape
    if ch < 12 or cw < 12:
        return empty

    depths = _edge_depths(crop, side)
    valid = ~np.isnan(depths)
    if int(valid.sum()) < 6:
        return empty

    idx = np.where(valid)[0]
    side_n = max(1, int(len(idx) * 0.15))
    center_idx = idx[int(len(idx) * 0.30) : int(len(idx) * 0.70)]
    shoulder_idx = np.concatenate([idx[:side_n], idx[-side_n:]])
    span = float(ch if side in ("top", "bottom") else cw)
    center_d = float(np.median(depths[center_idx]))
    shoulder_d = float(np.median(depths[shoulder_idx]))
    notch = (center_d - shoulder_d) / max(span * 0.22, 1.0)
    flatness = float(
        np.clip(1.0 - float(np.std(depths[valid])) / max(span * 0.14, 1.0), 0.0, 1.0)
    )

    tip_n = max(3, int(span * 0.08))
    if side == "top":
        tip = crop[:tip_n, :]
        tc = tip[:, int(cw * 0.30) : int(cw * 0.70)]
        ts = np.concatenate([tip[:, : max(1, cw // 6)], tip[:, cw - max(1, cw // 6) :]], axis=1)
    elif side == "bottom":
        tip = crop[-tip_n:, :]
        tc = tip[:, int(cw * 0.30) : int(cw * 0.70)]
        ts = np.concatenate([tip[:, : max(1, cw // 6)], tip[:, cw - max(1, cw // 6) :]], axis=1)
    elif side == "left":
        tip = crop[:, :tip_n]
        tc = tip[int(ch * 0.30) : int(ch * 0.70), :]
        ts = np.concatenate([tip[: max(1, ch // 6), :], tip[ch - max(1, ch // 6) :, :]], axis=0)
    else:
        tip = crop[:, -tip_n:]
        tc = tip[int(ch * 0.30) : int(ch * 0.70), :]
        ts = np.concatenate([tip[: max(1, ch // 6), :], tip[ch - max(1, ch // 6) :, :]], axis=0)

    tip_center = float(tc.mean()) if tc.size else 1.0
    tip_side = float(ts.mean()) if ts.size else 0.0
    neck_gap = tip_side - tip_center  # yaka: yan dolu, orta boş

    # Kısa kenar prior (kol–kol uzun kenar sahte çukur)
    edge_len = float(cw if side in ("top", "bottom") else ch)
    prior = float(min(ch, cw)) / max(edge_len, 1.0)

    neck = (2.5 * max(notch, 0.0) + 3.0 * max(neck_gap, 0.0) + 2.0 * (1.0 - tip_center)) * (
        0.3 + 0.7 * prior
    )
    hem = (2.8 * flatness + 2.5 * tip_center + 1.2 * tip_side - 2.5 * max(notch, 0.0)) * (
        0.3 + 0.7 * prior
    )
    return {
        "neck": float(neck),
        "hem": float(hem),
        "notch": float(notch),
        "flatness": float(flatness),
        "tip_center": tip_center,
        "tip_side": tip_side,
    }


def _profile_neck_signals(depths: np.ndarray) -> dict[str, float]:
    """Kenar derinlik profilinden 3 sinyal: derinlik, simetri, merkezîlik.

    Gerçek yaka: ortada simetrik çukur. Koltuk altı: kenara kaymış asimetrik çukur.
    """
    empty = {"depth_score": 0.0, "symmetry_score": 0.0, "centrality_score": 0.0}
    d = np.asarray(depths, dtype=np.float64)
    n = int(d.size)
    if n < 8:
        return empty
    valid = np.isfinite(d)
    if int(valid.sum()) < 6:
        return empty
    filled = d.copy()
    med = float(np.nanmedian(d))
    filled[~valid] = med
    span = float(max(np.nanmax(d) - np.nanmin(d), 1.0))

    i0, i1 = int(n * 0.30), int(n * 0.70)
    sn = max(1, int(n * 0.15))
    center_d = float(np.median(filled[i0:i1]))
    shoulder_d = float(np.median(np.concatenate([filled[:sn], filled[-sn:]])))
    depth_score = float(np.clip((center_d - shoulder_d) / span, 0.0, 1.0))

    idx = np.where(valid)[0]
    imax = int(idx[int(np.argmax(d[idx]))])
    center = (n - 1) / 2.0
    centrality_score = float(
        np.clip(1.0 - abs(imax - center) / max(center, 1.0), 0.0, 1.0)
    )

    mid = n // 2
    left = filled[:mid]
    right = filled[n - mid :][::-1]
    mlen = min(left.size, right.size)
    if mlen < 4:
        symmetry_score = 0.0
    else:
        diff = float(np.mean(np.abs(left[:mlen] - right[:mlen]))) / span
        symmetry_score = float(np.clip(1.0 - diff, 0.0, 1.0))

    return {
        "depth_score": depth_score,
        "symmetry_score": symmetry_score,
        "centrality_score": centrality_score,
    }


def _total_neck_score(sig: dict[str, float]) -> float:
    """Ağırlıklı yaka skoru — ağırlıklar config'te."""
    return float(
        settings.orient_w_depth * float(sig.get("depth_score") or 0.0)
        + settings.orient_w_symmetry * float(sig.get("symmetry_score") or 0.0)
        + settings.orient_w_centrality * float(sig.get("centrality_score") or 0.0)
    )


def _neck_hem_candidate_pair(totals: dict[str, float], signals: dict[str, dict[str, float]]) -> tuple[str, str]:
    """Kol-kol (uzun/asimetrik) çifti ele, yaka-etek çiftini bırak.

    Çift kalitesi = o eksendeki en iyi (simetri+merkezîlik). Derinlik kullanılmaz:
    koltuk altı da derin çukur üretir.
    """

    def pair_quality(a: str, b: str) -> float:
        def q(side: str) -> float:
            s = signals[side]
            return 0.5 * float(s["symmetry_score"]) + 0.5 * float(s["centrality_score"])

        return max(q(a), q(b))

    tb = pair_quality("top", "bottom")
    lr = pair_quality("left", "right")
    if tb >= lr:
        return ("top", "bottom")
    return ("left", "right")


def compute_orientation_scores(alpha: np.ndarray) -> dict[str, object]:
    """Yaka kenarı: simetri + merkezîlik + derinlik; yalnızca yaka-etek çifti."""
    binary = alpha > 127
    sides = ("top", "right", "bottom", "left")
    bb = _bbox_mask(binary)
    signals: dict[str, dict[str, float]] = {s: {"depth_score": 0.0, "symmetry_score": 0.0, "centrality_score": 0.0} for s in sides}
    detail: dict[str, dict[str, float]] = {}
    net: dict[str, float] = {}
    totals: dict[str, float] = {s: 0.0 for s in sides}

    if bb is not None:
        y0, y1, x0, x1 = bb
        crop = binary[y0:y1, x0:x1]
        for side in sides:
            m = _geometric_edge_metrics(binary, side)
            detail[side] = m
            net[side] = float(m["neck"] - 0.85 * m["hem"])
            depths = _edge_depths(crop, side)
            sig = _profile_neck_signals(depths)
            signals[side] = sig
            totals[side] = _total_neck_score(sig)
            detail[side].update(sig)
            detail[side]["total"] = totals[side]
    else:
        for side in sides:
            detail[side] = {
                "neck": -1e9,
                "hem": -1e9,
                "notch": 0.0,
                "flatness": 0.0,
                "tip_center": 1.0,
                "tip_side": 0.0,
                "depth_score": 0.0,
                "symmetry_score": 0.0,
                "centrality_score": 0.0,
                "total": 0.0,
            }
            net[side] = -1e9

    candidate_pair = _neck_hem_candidate_pair(totals, signals)
    ranked = sorted(candidate_pair, key=lambda s: totals[s], reverse=True)
    best = ranked[0]
    second = ranked[1]
    gap = float(totals[best] - totals[second])
    low_confidence = bool(gap < float(settings.orient_low_confidence_gap))

    max_depth = max(float(signals[s]["depth_score"]) for s in sides)
    min_abs_depth = float(settings.orient_min_absolute_depth)
    decision_note = None
    if max_depth < min_abs_depth:
        # Ağırlıkları değiştirmez; göreli gap'ten bağımsız güvenlik katmanı
        low_confidence = True
        decision_note = "no_edge_shows_real_notch_depth"

    # rot90 eski sinyal — karara karışmaz, yalnızca debug
    rot90_scores: dict[str, float] = {}
    for k, side in enumerate(sides):
        rot90_scores[side] = float(neckline_upright_score(np.rot90(binary, k)))

    payload = {
        "best": best,
        "combined": {s: round(totals[s], 3) for s in sides},
        "net_neck_minus_hem": {s: round(net[s], 3) for s in sides},
        "rot90_upright": {s: round(rot90_scores[s], 3) for s in sides},
        "detail": {
            s: {k: round(float(v), 3) for k, v in detail[s].items()} for s in sides
        },
        "candidate_pair": list(candidate_pair),
        "score_gap": round(gap, 3),
        "low_confidence": low_confidence,
        "max_depth_across_edges": round(max_depth, 3),
        "min_absolute_depth": min_abs_depth,
        "decision_note": decision_note,
        "index": {0: "top", 1: "right", 2: "bottom", 3: "left"},
    }
    logger.info("Orientation scores: %s", payload)
    return payload


def detect_neckline_edge(alpha: np.ndarray) -> str:
    """Yaka çukurunun bulunduğu kenar: top|right|bottom|left."""
    scores = compute_orientation_scores(alpha)
    best = str(scores["best"])
    logger.info("Neckline edge decision: best=%s", best)
    return best


# Kenar → yakayı KUZEYE getiren cv2.rotate sabiti / CCW derece
_NECK_EDGE_TO_CCW = {
    "top": 0,
    "right": 90,  # Doğu → Kuzey: ROTATE_90_COUNTERCLOCKWISE
    "bottom": 180,
    "left": 270,  # Batı → Kuzey: ROTATE_90_CLOCKWISE (= CCW 270)
}


def apply_cardinal_rotation(
    image: Image.Image,
    edge: Literal["top", "right", "bottom", "left"],
) -> Tuple[Image.Image, int, str]:
    """Yalnızca cv2.rotate — continuous deskew ile karıştırılmaz.

    right → ROTATE_90_COUNTERCLOCKWISE
    bottom → ROTATE_180
    left → ROTATE_90_CLOCKWISE
    top → yok
    """
    rgba = image.convert("RGBA") if image.mode != "RGBA" else image
    if edge == "top":
        return rgba, 0, "none"
    try:
        import cv2  # type: ignore

        arr = np.asarray(rgba.convert("RGBA"))
        if edge == "right":
            # Sağ → Üst: 90° CCW — tereddütsüz
            out = cv2.rotate(arr, cv2.ROTATE_90_COUNTERCLOCKWISE)
            deg = 90
            method = "cv2.ROTATE_90_COUNTERCLOCKWISE"
        elif edge == "bottom":
            out = cv2.rotate(arr, cv2.ROTATE_180)
            deg = 180
            method = "cv2.ROTATE_180"
        elif edge == "left":
            out = cv2.rotate(arr, cv2.ROTATE_90_CLOCKWISE)
            deg = 270
            method = "cv2.ROTATE_90_CLOCKWISE"
        else:
            return rgba, 0, "none"
        logger.info(
            "Cardinal rotate applied: edge=%s deg_ccw=%s cv2_rotate_ok shape %s→%s",
            edge,
            deg,
            arr.shape[:2],
            out.shape[:2],
        )
        return Image.fromarray(out, mode="RGBA"), deg, method
    except Exception as exc:  # noqa: BLE001
        logger.warning("cv2.rotate basarisiz (%s) — warpAffine fallback", exc)
        deg = int(_NECK_EDGE_TO_CCW.get(edge, 0))
        return _warp_rotate_rgba(rgba, float(deg)), deg, "warpAffine"


def _verify_neck_north(rgba: Image.Image) -> None:
    """Döndürme sonrası bbox / yaka kontrolü — uyarı logu."""
    alpha = _alpha_u8(rgba)
    binary = alpha > 127
    bb = _bbox_mask(binary)
    if bb is None:
        logger.warning("Orientation verify: maske bos")
        return
    y0, y1, x0, x1 = bb
    h, w = y1 - y0, x1 - x0
    edge = detect_neckline_edge(alpha)
    # Portre beklenir (H >= W); yatay kilitlendiyse uyarı
    if h < w * 0.92:
        logger.warning(
            "Orientation verify: bbox hala yatay (H=%s W=%s) — yaka edge=%s",
            h,
            w,
            edge,
        )
    if edge != "top":
        logger.warning(
            "Orientation verify: yaka hala Kuzey'de degil (edge=%s) — H=%s W=%s",
            edge,
            h,
            w,
        )
    else:
        # Yaka üst merkezde mi? (tip_center düşük olmalı)
        top_m = _geometric_edge_metrics(binary, "top")
        if top_m["tip_center"] > 0.55 and top_m["notch"] < 0.05:
            logger.warning(
                "Orientation verify: ust kenar yaka gibi gorunmuyor tip_center=%.2f notch=%.2f",
                top_m["tip_center"],
                top_m["notch"],
            )
        else:
            logger.info(
                "Orientation verify OK: neck=top bbox HxW=%sx%s tip_center=%.2f",
                h,
                w,
                top_m["tip_center"],
            )


def rotate_neckline_to_north(
    rgba: Image.Image,
    *,
    scores: Optional[dict] = None,
    apply_if_low_confidence: bool = False,
) -> Tuple[Image.Image, int, str, str]:
    """Yaka hangi kenardaysa o kenarı üste getir (cv2.rotate 0/90/180/270).

    low_confidence ise rotasyon uygulanmaz (deskew korunur).
    Donen: (gorsel, ccw_derece, kenar, method)

    TODO(flutter-confirm): Geçici köprü — Flutter onay UI gelince
    final_confidence=='medium' için skipped_pending_confirmation kaldırılıp
    onay akışına bağlanacak. Şimdilik yalnızca high otomatik uygulanır;
    medium/low etiketleri decision.json'da ayrı kalır.
    """
    if rgba.mode != "RGBA":
        rgba = rgba.convert("RGBA")
    payload = scores if scores is not None else compute_orientation_scores(_alpha_u8(rgba))
    edge = str(payload["best"])
    low = bool(payload.get("low_confidence"))
    ens = str(payload.get("ensemble_confidence") or "")
    if not ens:
        ens = "low" if low else "high"
    if ens == "low" and not apply_if_low_confidence:
        logger.info(
            "Cardinal rotate SKIPPED low_confidence suggested=%s gap=%s",
            edge,
            payload.get("score_gap"),
        )
        return rgba, 0, edge, "skipped_low_confidence"
    # TODO(flutter-confirm): UI gelince bu bloğu kaldır; medium onay sonrası uygulanır.
    if ens == "medium":
        logger.info(
            "Cardinal rotate SKIPPED pending_confirmation suggested=%s",
            edge,
        )
        return rgba, 0, edge, "skipped_pending_confirmation"
    out, deg, method = apply_cardinal_rotation(rgba, edge)  # type: ignore[arg-type]
    logger.info(
        "Neckline→North: edge=%s rotate_ccw=%s method=%s scores=%s",
        edge,
        deg,
        method,
        payload.get("combined"),
    )
    if deg != 0:
        _verify_neck_north(out)
    return out, deg, edge, method


def _end_band_features(binary: np.ndarray, which: str) -> dict[str, float]:
    """Üst/alt %30 — skor / test yardımcıları."""
    bb = _bbox_mask(binary)
    empty = {
        "opening": 0.0,
        "notch": 0.0,
        "flatness": 0.0,
        "tip_center": 1.0,
        "tip_side": 0.0,
        "max_width_n": 0.0,
        "width_cv": 0.0,
        "mass": 0.0,
        "p90_width": 0.0,
    }
    if bb is None or which not in ("top", "bottom"):
        return empty
    y0, y1, x0, x1 = bb
    crop = binary[y0:y1, x0:x1]
    ch, cw = crop.shape
    if ch < 10 or cw < 10:
        return empty

    tip_n = max(2, ch // 12)
    c0, c1 = int(cw * 0.32), int(cw * 0.68)
    s_w = max(1, cw // 6)
    depths = _edge_depths(crop, which)
    valid = ~np.isnan(depths)
    if int(valid.sum()) < 4:
        return empty

    if which == "top":
        region = crop[: max(4, int(ch * 0.30))]
        tip = crop[:tip_n]
    else:
        region = crop[-max(4, int(ch * 0.30)) :]
        tip = crop[-tip_n:]

    center_mask = np.zeros(cw, dtype=bool)
    center_mask[c0:c1] = True
    side_mask = np.zeros(cw, dtype=bool)
    side_mask[:s_w] = True
    side_mask[cw - s_w :] = True
    center_d = float(np.nanmedian(depths[center_mask & valid])) if np.any(center_mask & valid) else 0.0
    shoulder_d = float(np.nanmedian(depths[side_mask & valid])) if np.any(side_mask & valid) else 0.0
    notch = float(np.clip((center_d - shoulder_d) / max(ch * 0.28, 1.0), -1.0, 1.5))
    mid = region[:, c0:c1]
    opening = float(1.0 - mid.mean()) if mid.size else 0.0
    flatness = float(np.clip(1.0 - float(np.std(depths[valid])) / max(ch * 0.18, 1.0), 0.0, 1.0))
    tip_c = tip[:, c0:c1]
    tip_s = np.concatenate([tip[:, :s_w], tip[:, cw - s_w :]], axis=1) if tip.size else tip
    widths = _row_widths(region)
    mean_w = float(widths.mean()) if widths.size else 1.0
    return {
        "opening": opening,
        "notch": notch,
        "flatness": flatness,
        "tip_center": float(tip_c.mean()) if tip_c.size else 1.0,
        "tip_side": float(tip_s.mean()) if tip_s.size else 0.0,
        "max_width_n": float(widths.max()) / max(cw, 1) if widths.size else 0.0,
        "width_cv": float(np.std(widths) / max(mean_w, 1.0)),
        "mass": float(region.mean()) if region.size else 0.0,
        "p90_width": float(np.percentile(widths, 90)) / max(cw, 1) if widths.size else 0.0,
    }


def _hem_end_score(f: dict[str, float]) -> float:
    return (
        2.5 * f["p90_width"]
        + 2.0 * f["flatness"]
        + 2.2 * f["tip_center"]
        + 1.0 * f["mass"]
        - 2.5 * f["opening"]
        - 2.0 * max(f["notch"], 0.0)
    )


def _collar_end_score(f: dict[str, float]) -> float:
    return (
        3.0 * f["opening"]
        + 2.5 * max(f["notch"], 0.0)
        + 2.0 * (1.0 - f["tip_center"])
        + 0.8 * f["tip_side"]
        - 1.5 * f["flatness"] * f["tip_center"]
    )


def neckline_upright_score(binary: np.ndarray) -> float:
    """Yaka üstte varsayımı skoru."""
    top_f = _end_band_features(binary, "top")
    bot_f = _end_band_features(binary, "bottom")
    return float(
        _collar_end_score(top_f)
        + _hem_end_score(bot_f)
        - 0.9 * _collar_end_score(bot_f)
        - 0.9 * _hem_end_score(top_f)
    )


def should_flip_180(alpha: np.ndarray) -> bool:
    """Yaka altta / etek üstte ise True."""
    return detect_neckline_edge(alpha) == "bottom"


def correct_hem_polarity(rgba: Image.Image) -> Tuple[Image.Image, bool]:
    out, deg, edge, _method = rotate_neckline_to_north(rgba)
    return out, deg == 180 and edge == "bottom"


def align_garment_upright(rgba: Image.Image, *, trace: dict | None = None) -> Image.Image:
    """1) Eksen snap deskew  2) Yaka→Kuzey (cv2.ROTATE_*)."""
    if rgba.mode != "RGBA":
        rgba = rgba.convert("RGBA")
    out, report = deskew_garment_detailed(rgba, max_abs_deg=45.0)
    if trace is not None:
        trace["mask_deskewed"] = out.copy()
        trace["deskew_angle_applied"] = float(report.angle_applied)
        trace["deskew_step_executed"] = bool(report.executed)
        trace["deskew_input_angle_estimated"] = float(report.input_angle_estimated)
        trace["deskew_skip_reason"] = report.skip_reason
        trace["deskew_min_abs_deg"] = float(report.min_abs_deg)
        trace["deskew_below_min_threshold"] = bool(
            report.skip_reason == "angle_below_min_threshold"
        )
    ang = report.angle_applied
    scores = compute_orientation_scores(_alpha_u8(out))
    # Geometrik skoru değiştirme — yanına RotNet ensemble ekle
    try:
        from app.services.rotnet_inference import apply_orientation_ensemble

        scores = apply_orientation_ensemble(scores, _alpha_u8(out))
    except Exception:
        logger.exception("RotNet ensemble atlandi — yalnizca geometrik skor")
    out, card, edge, method = rotate_neckline_to_north(out, scores=scores)
    ensemble_conf = str(scores.get("ensemble_confidence") or ("low" if scores.get("low_confidence") else "high"))
    if trace is not None:
        trace["mask_rotated"] = out.copy()
        trace["scores"] = scores
        trace["best_edge"] = edge
        trace["rotation_deg_applied"] = int(card)
        trace["rotation_method"] = method
        trace["rotation_suggested"] = edge
        trace["low_confidence"] = bool(scores.get("low_confidence"))
        trace["ensemble_confidence"] = ensemble_conf
        trace["requires_confirmation"] = bool(
            scores.get("requires_confirmation")
            if "requires_confirmation" in scores
            else scores.get("low_confidence")
        )
    logger.info(
        "Align upright: deskew=%.2f neck_edge=%s cardinal_ccw=%s method=%s low_conf=%s ensemble=%s",
        ang,
        edge,
        card,
        method,
        scores.get("low_confidence"),
        ensemble_conf,
    )
    return out


def detect_neckline_cardinal_rotation(alpha: np.ndarray) -> int:
    """Yakayı üste getiren CCW derece (0/90/180/270)."""
    edge = detect_neckline_edge(alpha)
    return int(_NECK_EDGE_TO_CCW[edge])


def upright_by_neckline(rgba: Image.Image) -> Tuple[Image.Image, int]:
    out, deg, _edge, _method = rotate_neckline_to_north(rgba)
    return out, deg


# ---------------------------------------------------------------------------
# Güçlü flat-lay / catalog press (frequency separation)
# ---------------------------------------------------------------------------


def _guided_smooth(rgb: np.ndarray, guide: np.ndarray, radius: int, eps: float) -> np.ndarray:
    """Basit guided-filter yaklasimi (box filter mean). opencv ximgproc gerekmez."""
    try:
        import cv2  # type: ignore

        r = max(2, int(radius))
        k = 2 * r + 1
        guide_f = guide.astype(np.float32) / 255.0
        src_f = rgb.astype(np.float32) / 255.0
        mean_i = cv2.blur(guide_f, (k, k))
        mean_p = cv2.blur(src_f, (k, k))
        mean_ii = cv2.blur(guide_f * guide_f, (k, k))
        mean_ip = cv2.blur(guide_f[..., None] * src_f, (k, k))
        var_i = mean_ii - mean_i * mean_i
        cov_ip = mean_ip - mean_i[..., None] * mean_p
        a = cov_ip / (var_i[..., None] + eps)
        b = mean_p - a * mean_i[..., None]
        mean_a = cv2.blur(a, (k, k))
        mean_b = cv2.blur(b, (k, k))
        q = mean_a * guide_f[..., None] + mean_b
        return np.clip(q * 255.0, 0, 255).astype(np.uint8)
    except Exception:
        return rgb


def catalog_press(
    rgba: Image.Image,
    *,
    diameter: int = 11,
    sigma_color: float = 55.0,
    sigma_space: float = 55.0,
    detail_keep: float = 0.22,
    strong: bool = True,
) -> Image.Image:
    """Flat-lay katalog ütüleme: soft luminance flatten + frequency separation.

    low = guided/bilateral baz; high = kirisiklik; high * detail_keep ile bastirilir.
    Global kontrast germesi YAPILMAZ (kirisikliklari buyutur).
    """
    if rgba.mode != "RGBA":
        rgba = rgba.convert("RGBA")
    rgb = np.asarray(rgba.convert("RGB"), dtype=np.uint8)
    alpha = _alpha_u8(rgba)
    opaque = alpha > 127
    if not np.any(opaque):
        return rgba

    try:
        import cv2  # type: ignore

        work = rgb.astype(np.float32)

        # 1) Soft luminance flatten — lokal ortalamaya cek (golge kirisikligi)
        lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB)
        l_ch, a_ch, b_ch = cv2.split(lab)
        l_f = l_ch.astype(np.float32)
        k_flat = 31 if strong else 21
        if k_flat % 2 == 0:
            k_flat += 1
        l_blur = cv2.GaussianBlur(l_f, (k_flat, k_flat), 0)
        mix = 0.55 if strong else 0.35
        l_flat = l_f * (1.0 - mix) + l_blur * mix
        # Medyani hafifce hedefe cek (renk kaydirmadan)
        med = float(np.median(l_flat[opaque]))
        l_flat = l_flat + (140.0 - med) * 0.25
        l_flat = np.clip(l_flat, 0, 255)
        l_out = np.where(opaque, l_flat, l_f).astype(np.uint8)
        work_u8 = cv2.cvtColor(cv2.merge([l_out, a_ch, b_ch]), cv2.COLOR_LAB2RGB)

        # 2) Frequency separation + edge-preserving low
        gray = cv2.cvtColor(work_u8, cv2.COLOR_RGB2GRAY)
        radius = 9 if strong else 5
        low = _guided_smooth(work_u8, gray, radius=radius, eps=5e-4)
        d = int(diameter)
        if d % 2 == 0:
            d += 1
        d = max(5, min(d, 15))
        low = cv2.bilateralFilter(low, d, sigma_color, sigma_space)
        if strong:
            low = cv2.bilateralFilter(low, 9, sigma_color * 0.9, sigma_space * 0.9)

        high = work_u8.astype(np.float32) - low.astype(np.float32)
        keep = float(np.clip(detail_keep, 0.05, 0.6))
        pressed = np.clip(low.astype(np.float32) + high * keep, 0, 255).astype(np.uint8)
        out_rgb = np.where(opaque[..., None], pressed, rgb).astype(np.uint8)
    except Exception as exc:  # noqa: BLE001
        logger.warning("strong catalog press basarisiz (%s) — soft fallback", exc)
        from PIL import ImageFilter

        soft = rgba.convert("RGB").filter(ImageFilter.SMOOTH_MORE)
        soft_arr = np.asarray(soft, dtype=np.uint8)
        out_rgb = np.where(opaque[..., None], soft_arr, rgb).astype(np.uint8)

    out = Image.fromarray(out_rgb, mode="RGB").convert("RGBA")
    out.putalpha(Image.fromarray(alpha, mode="L"))
    logger.info(
        "Catalog press strong=%s detail_keep=%.2f opaque_px=%s",
        strong,
        detail_keep,
        int(opaque.sum()),
    )
    return out


def polish_studio_cutout(
    rgba: Image.Image,
    *,
    remove_hanger: bool = True,
    deskew: bool = True,
    press: bool = True,
    trace: dict | None = None,
) -> Image.Image:
    """Askı → yaka upright + deskew → güçlü flat-lay press."""
    if rgba.mode != "RGBA":
        rgba = rgba.convert("RGBA")
    out = rgba
    if remove_hanger:
        try:
            out = remove_hanger_artifacts(out)
        except Exception:
            logger.exception("Hanger temizligi basarisiz — devam")
    if trace is not None:
        trace["mask_raw"] = out.copy()
    if deskew:
        try:
            out = align_garment_upright(out, trace=trace)
        except Exception:
            logger.exception("Upright/deskew basarisiz — devam")
    elif trace is not None:
        trace["deskew_step_executed"] = False
        trace["deskew_skip_reason"] = "step_not_reached"
        trace["deskew_input_angle_estimated"] = 0.0
        trace["deskew_angle_applied"] = 0.0
    if press:
        try:
            out = catalog_press(out, strong=True, detail_keep=0.32)
        except Exception:
            logger.exception("Catalog press basarisiz — devam")
    return out
