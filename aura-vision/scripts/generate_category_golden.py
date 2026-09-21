"""Kategori (YOLO-boş fallback) golden PNG üreticisi — bir kez çalıştır, beklenenleri ölç."""

from __future__ import annotations

import io
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "tests" / "golden_set" / "category" / "images"
W, H = 256, 336


def _save(name: str, img: Image.Image) -> Path:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / name
    img.save(path, format="PNG")
    return path


def perspective_shadow_tee() -> Image.Image:
    """Trapez tişört + yer gölgesi; masa rengi zemin (YOLO kıyafet sınıfı yok)."""
    img = Image.new("RGB", (W, H), (196, 178, 152))
    # yaprak/gölge lekeleri
    d = ImageDraw.Draw(img)
    d.ellipse((20, 40, 110, 160), fill=(168, 148, 118))
    d.ellipse((140, 200, 240, 310), fill=(175, 155, 125))
    shadow = Image.new("L", (W, H), 0)
    sd = ImageDraw.Draw(shadow)
    sd.polygon([(78, 118), (198, 128), (188, 268), (62, 248)], fill=90)
    shadow = shadow.filter(ImageFilter.GaussianBlur(8))
    img.paste(tuple(int(c * 0.72) for c in (196, 178, 152)), mask=shadow)
    body = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    bd.polygon([(88, 70), (168, 78), (186, 250), (62, 238)], fill=(42, 98, 186, 255))
    bd.polygon([(70, 88), (40, 150), (72, 158), (92, 110)], fill=(42, 98, 186, 255))
    bd.polygon([(164, 92), (214, 148), (186, 160), (156, 112)], fill=(42, 98, 186, 255))
    # yaka
    bd.ellipse((108, 62, 148, 102), fill=(196, 178, 152, 255))
    img.paste(body, mask=body.split()[-1])
    return img


def borderline_fg() -> Image.Image:
    """Merkezde küçük gömlek; chroma sonrası mean_opaque ~ eşik (0.06)."""
    img = Image.new("RGB", (W, H), (232, 226, 214))
    d = ImageDraw.Draw(img)
    # ~9% kutu; refine biraz yer, 0.06'nın biraz üstünde kalması hedeflenir
    cx, cy = W // 2, H // 2
    bw, bh = 72, 96
    x0, y0 = cx - bw // 2, cy - bh // 2
    d.rectangle((x0, y0, x0 + bw, y0 + bh), fill=(36, 92, 178))
    d.rectangle((x0 - 10, y0 + 8, x0, y0 + 40), fill=(36, 92, 178))
    d.rectangle((x0 + bw, y0 + 8, x0 + bw + 10, y0 + 40), fill=(36, 92, 178))
    d.ellipse((cx - 12, y0 - 4, cx + 12, y0 + 18), fill=(232, 226, 214))
    return img


def blank_scene() -> Image.Image:
    """Kıyafetsiz sahne: seyrek alfa (RGB-only duvar chroma elips → dress FP).

    Refine sonrası mean_opaque ~0.038 < 0.06 → cutout_failed.
    """
    img = Image.new("RGBA", (W, H), (214, 208, 198, 0))
    d = ImageDraw.Draw(img)
    side = 68
    x0, y0 = (W - side) // 2, (H - side) // 2
    d.rectangle((x0, y0, x0 + side, y0 + side), fill=(186, 178, 166, 255))
    return img


def pants() -> Image.Image:
    img = Image.new("RGB", (W, H), (240, 236, 228))
    d = ImageDraw.Draw(img)
    # bel
    d.rectangle((78, 70, 178, 100), fill=(28, 42, 78))
    # bacaklar
    d.rectangle((82, 98, 124, 280), fill=(32, 48, 88))
    d.rectangle((132, 98, 174, 280), fill=(32, 48, 88))
    d.ellipse((82, 262, 124, 292), fill=(32, 48, 88))
    d.ellipse((132, 262, 174, 292), fill=(32, 48, 88))
    return img


def empty_wall_flat(size=(320, 420)) -> Image.Image:
    return Image.new("RGB", size, (214, 206, 196))


def empty_wall_noise(size=(320, 420)) -> Image.Image:
    w, h = size
    rng = np.random.default_rng(21)
    base = np.full((h, w, 3), (201, 196, 188), dtype=np.int16)
    noise = rng.integers(-14, 15, size=base.shape)
    img = Image.fromarray(np.clip(base + noise, 0, 255).astype(np.uint8), "RGB")
    return img.filter(ImageFilter.GaussianBlur(0.6))


def empty_wood_floor(size=(320, 420)) -> Image.Image:
    w, h = size
    wood = np.zeros((h, w, 3), dtype=np.uint8)
    rng = np.random.default_rng(9)
    for y in range(h):
        t = (y % 28) / 28.0
        plank = 0.85 + 0.15 * np.sin(y / 5.5)
        r = int(np.clip(118 + 40 * t * plank, 0, 255))
        g = int(np.clip(78 + 28 * t * plank, 0, 255))
        b = int(np.clip(42 + 16 * t * plank, 0, 255))
        wood[y, :, :] = (r, g, b)
        if y % 28 == 0:
            wood[y, :, :] = np.clip(wood[y, :, :].astype(int) - 25, 0, 255)
    wood = np.clip(wood.astype(np.int16) + rng.integers(-6, 7, size=wood.shape), 0, 255).astype(
        np.uint8
    )
    return Image.fromarray(wood, "RGB")


def empty_gradient(size=(320, 420)) -> Image.Image:
    w, h = size
    grad = np.zeros((h, w, 3), dtype=np.uint8)
    for y in range(h):
        t = y / max(h - 1, 1)
        grad[y, :, :] = (int(188 + 40 * t), int(184 + 28 * t), int(176 + 18 * t))
    return Image.fromarray(grad, "RGB")


def _mask_stats(cut: Image.Image | None) -> dict:
    from app.services.garment_studio import is_low_confidence_mask, mask_opaque_stats
    from app.services.image_analyzer import ImageAnalyzer

    if cut is None:
        return {"cutout": None}
    if cut.mode != "RGBA":
        return {"mode": cut.mode, "reason": ImageAnalyzer._empty_or_unusable_mask_reason(cut)}
    alpha = np.asarray(cut.split()[-1], dtype=np.uint8)
    opaque = int((alpha > 127).sum())
    return {
        "opaque_px": opaque,
        "mean_opaque": float((alpha > 127).mean()),
        "stats": mask_opaque_stats(alpha),
        "low_conf": is_low_confidence_mask(alpha),
        "empty_reason": ImageAnalyzer._empty_or_unusable_mask_reason(cut),
    }


class _EmptyYolo:
    model_name = "mock-yolo"

    def detect(self, image):
        return []


def probe(path: Path) -> dict:
    from app.services.image_analyzer import ImageAnalyzer
    from app.services.style_classifier import style_classifier

    analyzer = ImageAnalyzer(detector=_EmptyYolo(), classifier=style_classifier)
    raw = path.read_bytes()
    result = analyzer.analyze(raw, path.name, debug=False, job_id=path.stem[:12])
    cat = result.detected_items[0].category if result.detected_items else None
    conf = result.detected_items[0].category_confidence if result.detected_items else None
    cut = None
    if result.detected_items and result.detected_items[0].cutout_image_base64:
        import base64

        cut = Image.open(io.BytesIO(base64.b64decode(result.detected_items[0].cutout_image_base64)))
    else:
        from app.services.garment_normalizer import garment_normalizer

        img = Image.open(io.BytesIO(raw))
        cut, src = garment_normalizer._cutout(img, prefer_rembg=True)
        _ = src
    return {
        "file": path.name,
        "category": cat,
        "category_confidence": conf,
        "rejected_reason": result.rejected_reason,
        "stages": result.stages_completed,
        "mask": _mask_stats(cut),
    }


def main() -> None:
    import os

    os.environ.setdefault("AURA_STUDIO_REMBG", "false")
    os.environ.setdefault("AURA_STUDIO_PREFER_REMBG", "false")
    files = {
        "yolo_empty_perspective_shadow_tee.png": perspective_shadow_tee(),
        "yolo_empty_borderline_fg.png": borderline_fg(),
        "yolo_empty_blank_scene.png": blank_scene(),
        "yolo_empty_pants.png": pants(),
        "empty_wall_flat.png": empty_wall_flat(),
        "empty_wall_noise.png": empty_wall_noise(),
        "empty_wood_floor.png": empty_wood_floor(),
        "empty_gradient.png": empty_gradient(),
    }
    paths = [_save(name, img) for name, img in files.items()]
    extra = ROOT / "tests" / "golden_set" / "orientation" / "images" / "askisiz_perspektif_golge.png"
    report = [probe(p) for p in paths]
    if extra.is_file():
        report.append(probe(extra))
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
