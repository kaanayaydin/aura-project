"""CatVTON inference oncesi zorunlu 768x1024 hizalama.

Kisi + giysi + maske ayni boyutta olmali; aksi halde Metal/MPS
attention tamponu (concat latent) onlarca GB talep edebilir.
"""

from __future__ import annotations

from typing import Tuple

from PIL import Image

from app.config import settings
from app.services.catvton.image_ops import resize_and_crop, resize_and_padding

# CatVTON mix-48k resmi referans
CATVTON_WIDTH = 768
CATVTON_HEIGHT = 1024


def target_size() -> Tuple[int, int]:
    """AURA_VTON_WIDTH/HEIGHT — varsayilan 768x1024; ust sinir referans."""
    w = max(64, int(settings.width or CATVTON_WIDTH))
    h = max(64, int(settings.height or CATVTON_HEIGHT))
    # 8'e bolunebilir (VAE)
    w = w - (w % 8)
    h = h - (h % 8)
    return w, h


def prepare_person(person: Image.Image, size: Tuple[int, int] | None = None) -> tuple[Image.Image, Tuple[int, int]]:
    """Person → LANCZOS crop/resize. Donus: (hazir, orijinal_wh)."""
    original = person.size  # (w, h)
    target = size or target_size()
    aligned = resize_and_crop(person.convert("RGB"), target)
    assert aligned.size == target, f"person size {aligned.size} != {target}"
    return aligned, original


def prepare_garment(garment: Image.Image, size: Tuple[int, int] | None = None) -> Image.Image:
    """Garment → LANCZOS pad/resize (CatVTON condition)."""
    target = size or target_size()
    aligned = resize_and_padding(garment.convert("RGB"), target)
    assert aligned.size == target, f"garment size {aligned.size} != {target}"
    return aligned


def prepare_mask(mask: Image.Image, size: Tuple[int, int] | None = None) -> Image.Image:
    """Mask → ayni geometri, NEAREST (etiket korunur)."""
    target = size or target_size()
    # Once crop geometrisini person ile ayni tut (LANCZOS yerine bicubic crop + NEAREST)
    w, h = mask.size
    tw, th = target
    if w / h < tw / th:
        new_w, new_h = w, w * th // tw
    else:
        new_h, new_w = h, h * tw // w
    cropped = mask.convert("L").crop(
        ((w - new_w) // 2, (h - new_h) // 2, (w + new_w) // 2, (h + new_h) // 2)
    )
    aligned = cropped.resize(target, Image.NEAREST)
    assert aligned.size == target, f"mask size {aligned.size} != {target}"
    return aligned


def ensure_triplet(
    person: Image.Image,
    garment: Image.Image,
    mask: Image.Image,
    size: Tuple[int, int] | None = None,
) -> tuple[Image.Image, Image.Image, Image.Image]:
    """Uc girdi ayni (W,H) — son guvenlik agi."""
    target = size or target_size()
    if person.size != target:
        person, _ = prepare_person(person, target)
    if garment.size != target:
        garment = prepare_garment(garment, target)
    if mask.size != target:
        mask = prepare_mask(mask, target)
    if not (person.size == garment.size == mask.size == target):
        raise ValueError(
            f"CatVTON boyut uyumsuz: person={person.size} garment={garment.size} "
            f"mask={mask.size} target={target}"
        )
    return person, garment, mask


def restore_original_size(result: Image.Image, original_wh: Tuple[int, int]) -> Image.Image:
    """Inference cikisini kullanici orijinal en-boyuna dondur."""
    if not original_wh or result.size == original_wh:
        return result
    return result.resize(original_wh, Image.LANCZOS)
