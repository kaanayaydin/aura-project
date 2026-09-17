"""CatVTON agnostic mask uretici.

1) SCHP (LIP) ONNX → ust/alt/overall giysi + kol/bacak bolgeleri
2) Upper: semantik sinir — upper_raw & ~protect, kenar dilate, tekrar & ~protect
   (sabit yatay cut_y YOK — egik kalca / asimetrik kemer icin)
3) Basarisizsa / kapaliysa → geometrik torso fallback
"""

from __future__ import annotations

import logging
from typing import Literal

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from app.config import settings

logger = logging.getLogger("aura.vton.mask")

ClothType = Literal["upper", "lower", "overall"]

# LIP sinif ID'leri → CatVTON AutoMasker MASK_CLOTH + kollar/bacaklar
LIP_MASK_IDS: dict[str, set[int]] = {
    "upper": {
        5,  # Upper-clothes
        6,  # Dress
        7,  # Coat
        10,  # Jumpsuits
        14,  # Left-arm
        15,  # Right-arm
    },
    "lower": {
        6,  # Dress
        9,  # Pants
        10,  # Jumpsuits
        12,  # Skirt
        16,  # Left-leg
        17,  # Right-leg
    },
    "overall": {
        5,
        6,
        7,
        9,
        10,
        12,
        14,
        15,
        16,
        17,
    },
}

# Koruma: SADECE pantolon / etek / bacak (semantik sinir; yatay cut yok)
LIP_LOWER_PROTECT_IDS: set[int] = {
    9,  # Pants
    12,  # Skirt
    16,  # Left-leg
    17,  # Right-leg
}

# Gomlek-pantolon kenarinda beyaz artigi kapatmak icin upper dilate (5x5 / 7x7)
_SEAM_DILATE_PX = 5
# Protect'e hafif yan genisleme (yukari agresif degil; 0=salt etiket)
_PROTECT_DILATE_PX = 1


def _odd_kernel(px: int, *, cap: int = 15) -> int:
    k = max(1, int(px))
    if k % 2 == 0:
        k += 1
    return min(k, cap)


def _dilate_binary(mask_u8: np.ndarray, px: int) -> np.ndarray:
    if px <= 0 or mask_u8.max() == 0:
        return mask_u8
    img = Image.fromarray(mask_u8, mode="L")
    return np.asarray(img.filter(ImageFilter.MaxFilter(size=_odd_kernel(px))), dtype=np.uint8)


def build_lower_protect_mask(
    seg_map: np.ndarray,
    *,
    dilate_px: int = _PROTECT_DILATE_PX,
) -> np.ndarray:
    """pants | skirt | legs koruma maskesi (uint8 0/255)."""
    protect = np.isin(seg_map, list(LIP_LOWER_PROTECT_IDS)).astype(np.uint8) * 255
    return _dilate_binary(protect, dilate_px)


def subtract_protect(mask_u8: np.ndarray, protect_u8: np.ndarray) -> np.ndarray:
    """mask &= ~protect"""
    out = mask_u8.copy()
    out[protect_u8 > 0] = 0
    return out


def apply_semantic_upper_guard(
    upper_raw_u8: np.ndarray,
    seg_map: np.ndarray,
    *,
    seam_dilate_px: int = _SEAM_DILATE_PX,
    protect_dilate_px: int = _PROTECT_DILATE_PX,
) -> tuple[np.ndarray, np.ndarray]:
    """Semantik upper mask: (raw & ~protect) → dilate → & ~protect.

    Yatay cut_y kullanilmaz; sinir SCHP pantolon/etek/bacak etiketleridir.
    Seam dilate, kemer hizasindaki gomlek artiklarini inpaint alanina alir.
    Returns:
        (upper_mask uint8, protect_mask uint8)
    """
    if upper_raw_u8.shape != seg_map.shape:
        raise ValueError(f"shape uyusmazligi: upper={upper_raw_u8.shape} seg={seg_map.shape}")

    protect = build_lower_protect_mask(seg_map, dilate_px=protect_dilate_px)
    upper = subtract_protect(upper_raw_u8, protect)
    if seam_dilate_px > 0:
        upper = _dilate_binary(upper, seam_dilate_px)
    upper = subtract_protect(upper, protect)
    return upper, protect


def apply_protect_to_mask(mask: Image.Image, protect: Image.Image | None) -> Image.Image:
    """Pose refine sonrasi pantolon/bacak tasmasini semantik protect ile temizle."""
    if protect is None:
        return mask
    arr = np.asarray(mask, dtype=np.uint8)
    prot = np.asarray(protect.convert("L"), dtype=np.uint8)
    if arr.shape != prot.shape:
        prot_img = protect.convert("L").resize(mask.size, Image.NEAREST)
        prot = np.asarray(prot_img, dtype=np.uint8)
    return Image.fromarray(subtract_protect(arr, prot), mode="L")


def build_torso_mask(person: Image.Image) -> Image.Image:
    """Geometrik ust-govde maskesi (SCHP fallback)."""
    w, h = person.size
    mask = Image.new("L", (w, h), 0)
    draw = ImageDraw.Draw(mask)
    left = int(w * 0.18)
    right = int(w * 0.82)
    top = int(h * 0.18)
    bottom = int(h * 0.72)
    draw.ellipse([left, top, right, bottom], fill=255)
    return mask.filter(ImageFilter.GaussianBlur(radius=max(3, w // 80)))


def parsing_to_agnostic_mask(
    seg_map: np.ndarray,
    *,
    cloth_type: ClothType = "upper",
    dilate_px: int = 8,
) -> tuple[Image.Image, Image.Image | None]:
    """LIP etiket haritasindan CatVTON agnostic mask (beyaz = degistirilecek bolge).

    Returns:
        (mask L-mode, protect L-mode or None) — protect sadece upper icin.
    """
    if seg_map.ndim != 2:
        raise ValueError(f"seg_map 2D olmali, geldi: {seg_map.shape}")

    ids = LIP_MASK_IDS.get(cloth_type, LIP_MASK_IDS["upper"])
    binary = np.isin(seg_map, list(ids)).astype(np.uint8) * 255
    protect_img: Image.Image | None = None

    if cloth_type == "upper":
        binary, protect_u8 = apply_semantic_upper_guard(binary, seg_map)
        protect_img = Image.fromarray(protect_u8, mode="L")

    # Genel kenar yumusatma (kol/gomlek gecisleri) — sonra protect tekrar
    if dilate_px > 0:
        binary = _dilate_binary(binary, dilate_px)
        if protect_img is not None:
            binary = subtract_protect(binary, np.asarray(protect_img, dtype=np.uint8))

    mask = Image.fromarray(binary, mode="L")
    blur = max(3, min(seg_map.shape[0], seg_map.shape[1]) // 100)
    if blur % 2 == 0:
        blur += 1
    blurred = np.asarray(mask.filter(ImageFilter.GaussianBlur(radius=blur)), dtype=np.uint8)
    if protect_img is not None:
        prot = np.asarray(protect_img, dtype=np.uint8)
        blurred = subtract_protect(blurred, prot)
        # Soft bleed: protect uzerinde / esikte kalan dusuk degerleri sifirla
        blurred[prot > 0] = 0
    return Image.fromarray(blurred, mode="L"), protect_img


def build_agnostic_mask(
    person: Image.Image,
    *,
    cloth_type: ClothType | None = None,
    force_torso: bool = False,
) -> tuple[Image.Image, str, Image.Image | None]:
    """Agnostic mask + kaynak + opsiyonel protect (pose sonrasi semantik guard).

    Returns:
        (mask L-mode, source, protect L-mode or None)
    """
    ctype: ClothType = (cloth_type or settings.schp_cloth_type or "upper")  # type: ignore[assignment]
    if ctype not in LIP_MASK_IDS:
        ctype = "upper"

    if force_torso or not settings.schp_enabled:
        logger.info("Agnostic mask: torso fallback (schp_enabled=%s)", settings.schp_enabled)
        return build_torso_mask(person), "torso-fallback", None

    try:
        from app.services.catvton.schp_parser import get_schp_parser

        parser = get_schp_parser()
        if not parser.ensure_loaded():
            logger.warning("SCHP kullanilamiyor → torso fallback")
            return build_torso_mask(person), "torso-fallback", None

        seg = parser.parse(person)
        mask, protect = parsing_to_agnostic_mask(
            seg,
            cloth_type=ctype,
            dilate_px=settings.schp_dilate_px,
        )
        if mask.getextrema()[1] == 0:
            logger.warning("SCHP maskesi bos → torso fallback")
            return build_torso_mask(person), "torso-fallback", None

        logger.info(
            "Agnostic mask: SCHP cloth_type=%s size=%s semantic_protect=%s",
            ctype,
            mask.size,
            protect is not None,
        )
        return mask, "schp", protect
    except Exception as exc:  # noqa: BLE001
        logger.warning("SCHP maske hatasi (%s) → torso fallback", exc)
        return build_torso_mask(person), "torso-fallback", None
