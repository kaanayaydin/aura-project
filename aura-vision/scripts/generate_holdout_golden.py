#!/usr/bin/env python3
"""Hold-out golden görselleri — eğitim builtin spec'leriyle örtüşmez."""

from pathlib import Path
from PIL import Image
import shutil

OUT = Path(__file__).resolve().parents[1] / "tests" / "golden_set" / "orientation" / "images"


def _blank(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def hoodie():
    """Kapüşonlu sweatshirt: eğitim T-shirt'lerinden farklı silüet."""
    w, h = 200, 260
    img = _blank(w, h)
    px = img.load()
    # gövde
    for y in range(90, 240):
        for x in range(55, 145):
            px[x, y] = (40, 80, 140, 255)
    # uzun kollar
    for y in range(95, 210):
        for x in range(18, 55):
            px[x, y] = (40, 80, 140, 255)
        for x in range(145, 182):
            px[x, y] = (40, 80, 140, 255)
    # kapüşon (üstte yuvarlak)
    cx, cy, r = w // 2, 78, 28
    for y in range(cy - r, cy + 8):
        for x in range(cx - r, cx + r + 1):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                px[x, y] = (40, 80, 140, 255)
    # yaka deliği
    for y in range(70, 100):
        for x in range(cx - 12, cx + 13):
            if (x - cx) ** 2 + ((y - 88) ** 2) <= 80:
                px[x, y] = (0, 0, 0, 0)
    return img


def longsleeve():
    w, h = 210, 250
    img = _blank(w, h)
    px = img.load()
    for y in range(60, 230):
        for x in range(70, 140):
            px[x, y] = (160, 50, 50, 255)
    for y in range(62, 200):
        for x in range(12, 70):
            px[x, y] = (160, 50, 50, 255)
        for x in range(140, 198):
            px[x, y] = (160, 50, 50, 255)
    cx = w // 2
    for y in range(48, 78):
        for x in range(cx - 16, cx + 17):
            if abs(x - cx) < 16 - (y - 48) * 0.4:
                px[x, y] = (0, 0, 0, 0)
    return img


def aline_dress():
    w, h = 180, 280
    img = _blank(w, h)
    px = img.load()
    # A-line, kol yok
    for y in range(70, 260):
        t = (y - 70) / 190
        half = int(28 + t * 50)
        cx = w // 2
        for x in range(cx - half, cx + half):
            px[x, y] = (90, 40, 90, 255)
    # askı
    for y in range(48, 72):
        for x in range(62, 78):
            px[x, y] = (90, 40, 90, 255)
        for x in range(102, 118):
            px[x, y] = (90, 40, 90, 255)
    return img


def polo():
    w, h = 190, 240
    img = _blank(w, h)
    px = img.load()
    for y in range(70, 220):
        for x in range(50, 140):
            px[x, y] = (30, 110, 70, 255)
    for y in range(55, 85):
        for x in range(22, 168):
            px[x, y] = (30, 110, 70, 255)
    # polo V
    cx = w // 2
    for y in range(52, 95):
        t = (y - 52) / 43
        half = int(14 * (1 - t))
        for x in range(cx - half, cx + half + 1):
            px[x, y] = (0, 0, 0, 0)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    hoodie().save(OUT / "holdout_hoodie.png")
    longsleeve().save(OUT / "holdout_longsleeve.png")
    aline_dress().save(OUT / "holdout_aline_dress.png")
    polo().save(OUT / "holdout_polo.png")
    src = Path(__file__).resolve().parents[1] / "tests" / "fixtures" / "category" / "perspective_leaf_shadow.png"
    shutil.copy(src, OUT / "holdout_real_perspective.png")
    print("wrote hold-outs to", OUT)


if __name__ == "__main__":
    main()
