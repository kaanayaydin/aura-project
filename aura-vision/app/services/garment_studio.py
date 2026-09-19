"""Garment studio framing — flat-lay / e-ticaret katalog formati.

RGBA kesimi alir; alpha bbox ile ortalar, 3:4 (veya 1:1) kanvas + soft stüdyo
arka plan + opsiyonel damla golge uygular.
"""

from __future__ import annotations

import logging
from typing import Literal, Tuple

import numpy as np
from PIL import Image, ImageFilter

logger = logging.getLogger("aura.vision.studio")

StudioAspect = Literal["3:4", "1:1"]

_DEFAULT_BG = (248, 249, 250)  # #F8F9FA
_WHITE = (255, 255, 255)


def parse_hex_color(raw: str, fallback: Tuple[int, int, int] = _DEFAULT_BG) -> Tuple[int, int, int]:
    text = (raw or "").strip().lstrip("#")
    if len(text) == 6:
        try:
            return int(text[0:2], 16), int(text[2:4], 16), int(text[4:6], 16)
        except ValueError:
            return fallback
    return fallback


def canvas_size_for_aspect(aspect: StudioAspect, long_side: int) -> Tuple[int, int]:
    long_side = max(256, int(long_side))
    if aspect == "1:1":
        return long_side, long_side
    # 3:4 portrait
    w = int(round(long_side * 3 / 4))
    return w, long_side


def alpha_bbox(rgba: Image.Image, *, alpha_threshold: int = 16) -> Tuple[int, int, int, int] | None:
    """Saydam olmayan piksellerin bounding box'i (left, top, right, bottom)."""
    if rgba.mode != "RGBA":
        rgba = rgba.convert("RGBA")
    alpha = np.asarray(rgba.split()[-1])
    ys, xs = np.where(alpha > alpha_threshold)
    if ys.size == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def has_meaningful_alpha(rgba: Image.Image, *, min_transparent_ratio: float = 0.02) -> bool:
    if rgba.mode != "RGBA":
        return False
    alpha = np.asarray(rgba.split()[-1])
    transparent = (alpha < 16).mean()
    opaque = (alpha > 200).mean()
    return bool(transparent >= min_transparent_ratio and opaque >= 0.05)


def _border_opaque_ratio(alpha: np.ndarray, *, thr: int = 127) -> float:
    h, w = alpha.shape
    if h < 2 or w < 2:
        return float((alpha > thr).mean())
    border = np.concatenate(
        [
            alpha[0, :],
            alpha[-1, :],
            alpha[1:-1, 0],
            alpha[1:-1, -1],
        ]
    )
    return float((border > thr).mean())


def _center_opaque_ratio(alpha: np.ndarray, *, thr: int = 127, frac: float = 0.35) -> float:
    h, w = alpha.shape
    ch = max(1, int(h * frac / 2))
    cw = max(1, int(w * frac / 2))
    cy, cx = h // 2, w // 2
    center = alpha[max(0, cy - ch) : cy + ch, max(0, cx - cw) : cx + cw]
    if center.size == 0:
        return 0.0
    return float((center > thr).mean())


def keep_largest_opaque_component(alpha_u8: np.ndarray) -> np.ndarray:
    """Opak bagli bilesenlerin en buyugunu tut (kiyafet); gurultuyu at.

    Bilesen yoksa (num_labels<=1 / sadece arka plan) girisi oldugu gibi dondurur — crash yok.
    OpenCV → scipy → numpy BFS.
    """
    alpha_u8 = np.asarray(alpha_u8)
    if alpha_u8.ndim != 2:
        raise ValueError(f"alpha 2D olmali, geldi: {alpha_u8.shape}")
    if alpha_u8.dtype != np.uint8:
        alpha_u8 = np.clip(alpha_u8, 0, 255).astype(np.uint8)

    binary = alpha_u8 > 127
    if not np.any(binary):
        return alpha_u8

    # 1) OpenCV connectedComponentsWithStats
    try:
        import cv2  # type: ignore

        # mask: uint8 0/255, shape (H, W) — OpenCV ayni duzeni bekler
        mask_u8 = np.where(binary, 255, 0).astype(np.uint8)
        num_labels, labels, stats, _centroids = cv2.connectedComponentsWithStats(
            mask_u8, connectivity=8
        )
        # label 0 = background. num_labels==1 → hic foreground yok
        if num_labels <= 1:
            return alpha_u8
        # En buyuk foreground (area = stats[i, cv2.CC_STAT_AREA])
        areas = stats[1:, cv2.CC_STAT_AREA]
        if areas.size == 0:
            return alpha_u8
        keep_id = int(np.argmax(areas)) + 1
        if keep_id <= 0 or stats[keep_id, cv2.CC_STAT_AREA] <= 0:
            return alpha_u8
        out = np.where(labels == keep_id, alpha_u8, 0).astype(np.uint8)
        logger.info(
            "LCC(cv2): %s labelden en buyuk id=%s area=%s",
            num_labels - 1,
            keep_id,
            int(stats[keep_id, cv2.CC_STAT_AREA]),
        )
        return out
    except Exception as exc:  # noqa: BLE001
        logger.debug("cv2 LCC yok/basarisiz (%s)", exc)

    # 2) scipy
    try:
        from scipy import ndimage  # type: ignore

        labeled, n = ndimage.label(binary)
        if n <= 0:
            return alpha_u8
        counts = np.bincount(labeled.ravel())
        if counts.size <= 1:
            return alpha_u8
        counts[0] = 0
        keep_id = int(np.argmax(counts))
        if keep_id <= 0 or counts[keep_id] <= 0:
            return alpha_u8
        out = np.where(labeled == keep_id, alpha_u8, 0).astype(np.uint8)
        logger.info("LCC(scipy): %s bilesenden id=%s px=%s", n, keep_id, counts[keep_id])
        return out
    except Exception as exc:  # noqa: BLE001
        logger.warning("scipy LCC basarisiz (%s) — numpy BFS", exc)
        return _largest_component_numpy(alpha_u8)


def _odd_filter_size(size: int) -> int:
    """PIL Min/MaxFilter odd size ister."""
    s = max(3, int(size))
    if s % 2 == 0:
        s += 1
    return min(s, 15)


def _largest_component_numpy(alpha_u8: np.ndarray) -> np.ndarray:
    """scipy yoksa 4-bagli BFS ile en buyuk opak bilesen."""
    h, w = alpha_u8.shape
    visited = np.zeros((h, w), dtype=bool)
    best_coords: list[tuple[int, int]] = []
    binary = alpha_u8 > 127
    for y0 in range(h):
        for x0 in range(w):
            if not binary[y0, x0] or visited[y0, x0]:
                continue
            stack = [(y0, x0)]
            visited[y0, x0] = True
            coords: list[tuple[int, int]] = []
            while stack:
                y, x = stack.pop()
                coords.append((y, x))
                for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < h and 0 <= nx < w and binary[ny, nx] and not visited[ny, nx]:
                        visited[ny, nx] = True
                        stack.append((ny, nx))
            if len(coords) > len(best_coords):
                best_coords = coords
    out = np.zeros_like(alpha_u8)
    for y, x in best_coords:
        out[y, x] = alpha_u8[y, x]
    return out


def _force_border_transparent(alpha: np.ndarray, *, margin: int = 4) -> np.ndarray:
    out = alpha.copy()
    m = max(1, int(margin))
    out[:m, :] = 0
    out[-m:, :] = 0
    out[:, :m] = 0
    out[:, -m:] = 0
    return out


def mask_opaque_stats(alpha: np.ndarray) -> dict[str, float]:
    opaque = alpha > 127
    return {
        "mean_opaque": float(opaque.mean()),
        "border_opaque": _border_opaque_ratio(alpha),
        "center_opaque": _center_opaque_ratio(alpha),
    }


def is_low_confidence_mask(alpha: np.ndarray) -> bool:
    """Neredeyse tamamen dolu/bos veya kenar hala opak → guven dusuk."""
    s = mask_opaque_stats(alpha)
    if s["mean_opaque"] > 0.80 or s["mean_opaque"] < 0.06:
        return True
    if s["border_opaque"] > 0.30:
        return True
    # Merkez zayif, kenar guclu — hâlâ sorunlu
    if s["center_opaque"] < 0.15 and s["mean_opaque"] > 0.4:
        return True
    return False


def boost_contrast(rgb: Image.Image, *, factor: float = 1.75) -> Image.Image:
    """Dusuk kontrast (beyaz/beyaz) icin rembg oncesi kontrast artir."""
    from PIL import ImageEnhance

    img = rgb.convert("RGB")
    img = ImageEnhance.Contrast(img).enhance(factor)
    img = ImageEnhance.Sharpness(img).enhance(1.25)
    return img


def refine_garment_alpha(
    rgba: Image.Image,
    *,
    force_aggressive: bool = False,
) -> Image.Image:
    """Polarite + (dusuk guvende) agresif erosion + LCC — carsafı sil, tişörtü kurtar.

    Ham gorseli oldugu gibi birakMAZ; stüdyo framing icin her zaman seffaf kenar hedefler.
    """
    if rgba.mode != "RGBA":
        rgba = rgba.convert("RGBA")

    # 1) Polarite
    rgba = ensure_foreground_polarity(rgba)
    alpha = np.asarray(rgba.split()[-1], dtype=np.uint8).copy()
    stats = mask_opaque_stats(alpha)
    low = force_aggressive or is_low_confidence_mask(alpha)

    if low:
        logger.info(
            "Low-confidence mask — aggressive refine "
            "(mean=%.2f border=%.2f center=%.2f)",
            stats["mean_opaque"],
            stats["border_opaque"],
            stats["center_opaque"],
        )
        mask = Image.fromarray(alpha, mode="L")
        # Agresif asindirma: carsaf baglantisini kopar
        for size in (7, 5, 5):
            mask = mask.filter(ImageFilter.MinFilter(size=_odd_filter_size(size)))
        alpha = np.asarray(mask, dtype=np.uint8)
        alpha = keep_largest_opaque_component(alpha)
        # Hafif geri genislet (kiyafet kenarlari) — asiri buyutme
        mask = Image.fromarray(alpha, mode="L")
        mask = mask.filter(ImageFilter.MaxFilter(size=_odd_filter_size(3)))
        alpha = np.asarray(mask, dtype=np.uint8)
        alpha = _force_border_transparent(alpha, margin=max(4, min(alpha.shape) // 40))
        # Hâlâ kenar opaksa / neredeyse tam doluysa daha agresif kirp
        if _border_opaque_ratio(alpha) > 0.15 or (alpha > 127).mean() > 0.72:
            mask = Image.fromarray(alpha, mode="L")
            for _ in range(2):
                mask = mask.filter(ImageFilter.MinFilter(size=_odd_filter_size(7)))
            alpha = keep_largest_opaque_component(np.asarray(mask, dtype=np.uint8))
            alpha = _force_border_transparent(alpha, margin=max(6, min(alpha.shape) // 30))
        # Son care: merkez elips ile kirp (carsaf kalintisi)
        if (alpha > 127).mean() > 0.70:
            h, w = alpha.shape
            yy, xx = np.ogrid[:h, :w]
            cy, cx = h / 2.0, w / 2.0
            ry, rx = h * 0.42, w * 0.36
            ellipse = ((yy - cy) / max(ry, 1)) ** 2 + ((xx - cx) / max(rx, 1)) ** 2 <= 1.0
            alpha = np.where(ellipse & (alpha > 127), 255, 0).astype(np.uint8)
            logger.info("Low-confidence: merkez elips kirpimi uygulandi")
    else:
        alpha = keep_largest_opaque_component(alpha)
        alpha = _force_border_transparent(alpha, margin=2)

    # Bos maske guvenlik agi: merkez kutuyu yumusak foreground yap (carsaf yerine)
    if (alpha > 127).mean() < 0.02:
        logger.warning("Refine sonrasi maske bos — merkez elips fallback")
        h, w = alpha.shape
        yy, xx = np.ogrid[:h, :w]
        cy, cx = h / 2.0, w / 2.0
        ry, rx = h * 0.38, w * 0.32
        ellipse = ((yy - cy) / max(ry, 1)) ** 2 + ((xx - cx) / max(rx, 1)) ** 2 <= 1.0
        alpha = np.where(ellipse, 255, 0).astype(np.uint8)

    out = rgba.copy()
    alpha = np.asarray(alpha, dtype=np.uint8)
    if alpha.ndim != 2:
        raise ValueError(f"refine alpha 2D degil: {alpha.shape}")
    if alpha.shape[0] != rgba.size[1] or alpha.shape[1] != rgba.size[0]:
        # PIL size=(W,H), numpy=(H,W)
        raise ValueError(
            f"alpha boyut uyusmazligi: alpha={alpha.shape} imageWH={rgba.size}"
        )
    out.putalpha(Image.fromarray(alpha, mode="L"))
    return out


def ensure_foreground_polarity(rgba: Image.Image) -> Image.Image:
    """Kenarlar arka plan (seffaf), merkez kiyafet (opak) olmali.

    Ters maskede kenarlar dolu / merkez delik → alfa tersine cevrilir.
    Not: agresif refine icin `refine_garment_alpha` kullan.
    """
    if rgba.mode != "RGBA":
        rgba = rgba.convert("RGBA")
    alpha = np.asarray(rgba.split()[-1], dtype=np.uint8).copy()
    border = _border_opaque_ratio(alpha)
    center = _center_opaque_ratio(alpha)
    inverted = border > 0.45 and center < 0.40
    if not inverted and border > 0.70 and border - center > 0.25:
        inverted = True

    if inverted:
        logger.info(
            "Mask polarity INVERTED (border_opaque=%.2f center_opaque=%.2f) — flipping",
            border,
            center,
        )
        alpha = (255 - alpha).astype(np.uint8)

    out = rgba.copy()
    out.putalpha(Image.fromarray(alpha, mode="L"))
    return out


def chroma_cutout(
    image: Image.Image,
    *,
    color_distance: float = 38.0,
) -> Image.Image:
    """Kose orneklemeli basit arka plan silme (rembg/SAM yoksa fallback)."""
    rgb = image.convert("RGB")
    # Dusuk kontrastta mesafe esigini dusur
    arr = np.asarray(rgb, dtype=np.float32)
    h, w, _ = arr.shape
    samples = [
        arr[0:4, 0:4].reshape(-1, 3),
        arr[0:4, w - 4 : w].reshape(-1, 3),
        arr[h - 4 : h, 0:4].reshape(-1, 3),
        arr[h - 4 : h, w - 4 : w].reshape(-1, 3),
    ]
    bg = np.median(np.concatenate(samples, axis=0), axis=0)
    dist = np.linalg.norm(arr - bg, axis=2)
    # Adaptif esik: median mesafenin uzeri foreground
    med = float(np.median(dist))
    thr = max(12.0, min(color_distance, med * 0.85 + 8.0))
    alpha = np.where(dist > thr, 255, 0).astype(np.uint8)
    mask = Image.fromarray(alpha, mode="L")
    mask = mask.filter(ImageFilter.MaxFilter(size=5))
    mask = mask.filter(ImageFilter.MinFilter(size=3))
    alpha = np.asarray(mask)
    rgba = rgb.convert("RGBA")
    rgba.putalpha(Image.fromarray(alpha, mode="L"))
    return refine_garment_alpha(rgba, force_aggressive=True)


def compose_studio(
    cutout_rgba: Image.Image,
    *,
    aspect: StudioAspect = "3:4",
    long_side: int = 1024,
    background: Tuple[int, int, int] = _DEFAULT_BG,
    margin_ratio: float = 0.08,
    drop_shadow: bool = True,
    vertical_bias: float = 0.08,
) -> Image.Image:
    """Kiyafeti simetrik stüdyo kutusuna ortala (yatay merkeze kilitli).

    vertical_bias: 0 = geometrik orta; >0 kiyafeti biraz yukarı kaydırır (katalog).
    """
    rgba = cutout_rgba.convert("RGBA")
    box = alpha_bbox(rgba)
    if box is None:
        canvas_w, canvas_h = canvas_size_for_aspect(aspect, long_side)
        return Image.new("RGB", (canvas_w, canvas_h), background)

    # Simetrik pad: bbox'a esit pay ekle (kirpik / yaka kesilmesin)
    left, top, right, bottom = box
    gw0, gh0 = right - left, bottom - top
    pad = max(2, int(round(max(gw0, gh0) * 0.02)))
    left = max(0, left - pad)
    top = max(0, top - pad)
    right = min(rgba.size[0], right + pad)
    bottom = min(rgba.size[1], bottom + pad)

    garment = rgba.crop((left, top, right, bottom))
    canvas_w, canvas_h = canvas_size_for_aspect(aspect, long_side)
    max_w = int(canvas_w * (1.0 - 2 * margin_ratio))
    max_h = int(canvas_h * (1.0 - 2 * margin_ratio))
    gw, gh = garment.size
    scale = min(max_w / max(gw, 1), max_h / max(gh, 1))
    new_w = max(1, int(round(gw * scale)))
    new_h = max(1, int(round(gh * scale)))
    garment = garment.resize((new_w, new_h), Image.LANCZOS)

    canvas = Image.new("RGBA", (canvas_w, canvas_h), (*background, 255))
    # Yatay: kesin merkez. Dikey: hafif yukarı bias (e-ticaret flat-lay).
    x = (canvas_w - new_w) // 2
    y_center = (canvas_h - new_h) // 2
    y = int(round(y_center - vertical_bias * canvas_h * 0.5))
    y = max(int(margin_ratio * canvas_h), min(y, canvas_h - new_h - int(margin_ratio * canvas_h)))

    if drop_shadow:
        alpha = garment.split()[-1]
        shadow_layer = Image.new("RGBA", (new_w, new_h), (0, 0, 0, 55))
        shadow_layer.putalpha(alpha.point(lambda a: int(a * 0.35)))
        shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(radius=max(6, new_w // 40)))
        canvas.alpha_composite(shadow_layer, (x + max(2, new_w // 80), y + max(3, new_h // 60)))

    canvas.alpha_composite(garment, (x, y))
    return canvas.convert("RGB")
