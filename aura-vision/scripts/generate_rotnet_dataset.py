#!/usr/bin/env python3
"""Self-supervised RotNet veri seti: doğru yönlü alpha mask → 0/90/180/270.

Kaynak: yalnızca yaka=üst maskeler. Golden-set (synthetic_uneck / crewneck
dahil) EĞİTİME GİRMEZ — train-on-test sızıntısı. Yatay flip YOK.

Bölme KAYNAK bazında: bir kaynağın tüm augment/rotasyon türevleri aynı
split'te kalır. Örnek-seviyesinde rastgele split YOK.

Sınıf = uygulanması gereken kardinal düzeltme (CCW):
  kaynak dik + PIL ROTATE_90 (CCW) → yaka solda → class 270
  kaynak dik + PIL ROTATE_270      → yaka sağda → class 90
  kaynak dik + PIL ROTATE_180      → yaka altta → class 180
  kaynak dik                       → class 0
"""

from __future__ import annotations

import argparse
import json
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

# PIL Transpose: ROTATE_90 = 90° CCW
_CORRUPT_TO_LABEL = {
    None: 0,
    Image.Transpose.ROTATE_90: 270,
    Image.Transpose.ROTATE_180: 180,
    Image.Transpose.ROTATE_270: 90,
}


def _as_alpha(img: Image.Image) -> Image.Image:
    if img.mode == "RGBA":
        return img.split()[-1]
    return img.convert("L")


def _make_synthetic_shirt(
    *,
    body_w: int,
    body_h: int,
    sleeve_w: int,
    sleeve_h: int,
    neck_r: int,
    neck_kind: str,
    seed: int,
) -> Image.Image:
    rng = random.Random(seed)
    canvas_w = body_w + sleeve_w * 2 + 16
    canvas_h = body_h + 40
    img = Image.new("L", (canvas_w, canvas_h), 0)
    px = img.load()
    x0 = (canvas_w - body_w) // 2
    y0 = 28
    for y in range(y0, y0 + body_h):
        for x in range(x0, x0 + body_w):
            px[x, y] = 255
    arm_y0 = y0 + 4
    for y in range(arm_y0, arm_y0 + sleeve_h):
        for x in range(x0 - sleeve_w, x0 + body_w + sleeve_w):
            if x < x0 or x >= x0 + body_w:
                px[x, y] = 255
    cx = canvas_w // 2
    cy = y0
    if neck_kind == "u":
        for y in range(cy - 4, cy + neck_r + 4):
            for x in range(cx - neck_r - 2, cx + neck_r + 3):
                if (x - cx) ** 2 + (y - cy) ** 2 <= neck_r * neck_r and y >= cy - 2:
                    px[x, y] = 0
    elif neck_kind == "crew":
        for y in range(cy - 6, cy + neck_r):
            for x in range(cx - neck_r - 2, cx + neck_r + 3):
                if (x - cx) ** 2 + (y - cy) ** 2 <= neck_r * neck_r and y < cy + 3:
                    px[x, y] = 0
    else:  # sığ V
        half = max(6, neck_r)
        depth = max(8, neck_r + rng.randint(0, 6))
        for y in range(cy, cy + depth):
            t = (y - cy) / max(depth, 1)
            half_w = int(half * (1.0 - t))
            for x in range(cx - half_w, cx + half_w + 1):
                px[x, y] = 0
    return img


def _builtin_upright_masks() -> list[tuple[str, Image.Image]]:
    """Eğitim kaynakları — golden-set ile örtüşmez."""
    specs = [
        ("syn_u_a", 84, 150, 28, 26, 22, "u"),
        ("syn_crew_a", 80, 140, 24, 22, 12, "crew"),
        ("syn_u_b", 90, 160, 32, 28, 18, "u"),
        ("syn_crew_b", 76, 132, 22, 20, 10, "crew"),
        ("syn_v_a", 88, 148, 26, 24, 16, "v"),
        ("syn_u_c", 82, 144, 30, 22, 20, "u"),
        ("syn_crew_c", 70, 128, 20, 18, 9, "crew"),
        ("syn_u_d", 94, 168, 34, 30, 24, "u"),
        ("syn_v_b", 78, 136, 22, 18, 14, "v"),
        ("syn_u_e", 86, 154, 27, 25, 19, "u"),
        ("syn_crew_d", 72, 124, 18, 16, 8, "crew"),
        ("syn_v_c", 92, 162, 31, 27, 17, "v"),
    ]
    keys = ("body_w", "body_h", "sleeve_w", "sleeve_h", "neck_r", "neck_kind")
    out: list[tuple[str, Image.Image]] = []
    for i, row in enumerate(specs):
        name = row[0]
        spec = row[1:]
        out.append((name, _make_synthetic_shirt(**dict(zip(keys, spec)), seed=100 + i)))
    return out


def _load_source_masks(
    source_dir: Path | None, *, include_golden: bool
) -> list[tuple[str, Image.Image]]:
    """Varsayılan: yalnızca builtin sentetik. Golden-set train-on-test için kapalı."""
    masks: list[tuple[str, Image.Image]] = []
    if include_golden:
        raise RuntimeError(
            "Golden-set eğitim kaynağı olarak kullanılamaz (train-on-test). "
            "--include-golden kaldırıldı; hold-out golden'da kalsın."
        )
    if source_dir and source_dir.is_dir():
        for path in sorted(source_dir.glob("*")):
            if path.suffix.lower() not in {".png", ".jpg", ".jpeg", ".webp"}:
                continue
            masks.append((path.stem, _as_alpha(Image.open(path))))
    masks.extend(_builtin_upright_masks())
    return masks


def _augment(mask: Image.Image, rng: random.Random) -> Image.Image:
    """Ölçek / kaydırma / gürültü. Yatay flip YOK."""
    w, h = mask.size
    scale = rng.uniform(0.88, 1.12)
    nw, nh = max(8, int(w * scale)), max(8, int(h * scale))
    out = mask.resize((nw, nh), Image.Resampling.NEAREST)
    canvas = Image.new("L", (w, h), 0)
    dx = rng.randint(-int(w * 0.08), int(w * 0.08))
    dy = rng.randint(-int(h * 0.08), int(h * 0.08))
    x = (w - nw) // 2 + dx
    y = (h - nh) // 2 + dy
    canvas.paste(out, (x, y))
    if rng.random() < 0.35:
        canvas = canvas.filter(ImageFilter.GaussianBlur(radius=rng.uniform(0.2, 0.8)))
    arr = np.asarray(canvas, dtype=np.float32)
    if rng.random() < 0.5:
        noise = rng.gauss(0.0, 12.0)
        arr = np.clip(
            arr
            + np.random.default_rng(rng.randint(0, 10**6)).normal(0, 10, arr.shape)
            + noise,
            0,
            255,
        )
    arr = (arr > 127).astype(np.uint8) * 255
    return Image.fromarray(arr, mode="L")


def _assign_source_splits(
    source_ids: list[str], val_ratio: float, seed: int
) -> dict[str, str]:
    """Kaynak bazında train/val. Türevler ebeveynin split'ini izler."""
    rng = random.Random(seed)
    ids = list(source_ids)
    rng.shuffle(ids)
    n = len(ids)
    n_val = max(1, int(round(n * val_ratio))) if n >= 2 else 0
    if n_val >= n:
        n_val = max(1, n - 1)
    val_ids = set(ids[:n_val])
    return {sid: ("val" if sid in val_ids else "train") for sid in source_ids}


def _write_source_split(
    sources: list[tuple[str, Image.Image]],
    out_dir: Path,
    *,
    aug_per_source: int,
    val_ratio: float,
    seed: int,
) -> dict[str, object]:
    rng = random.Random(seed)
    source_ids = [s[0] for s in sources]
    split_of = _assign_source_splits(source_ids, val_ratio, seed)
    counts = {"train": 0, "val": 0}
    per_source: dict[str, dict[str, int]] = {}

    for src_id, src in sources:
        split = split_of[src_id]
        n_written = 0
        for aug_i in range(max(1, aug_per_source)):
            aug = _augment(src, rng)
            for corrupt, label in _CORRUPT_TO_LABEL.items():
                img = aug if corrupt is None else aug.transpose(corrupt)
                dest = out_dir / split / str(label)
                dest.mkdir(parents=True, exist_ok=True)
                fname = f"{src_id}_a{aug_i:02d}_{label}.png"
                img.save(dest / fname)
                counts[split] += 1
                n_written += 1
        per_source[src_id] = {"split": split, "n_derivatives": n_written}

    train_sources = [s for s, sp in split_of.items() if sp == "train"]
    val_sources = [s for s, sp in split_of.items() if sp == "val"]
    return {
        "counts": counts,
        "source_split": split_of,
        "train_sources": train_sources,
        "val_sources": val_sources,
        "per_source": per_source,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="RotNet 4-sınıf mask veri seti")
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=None,
        help="Ek doğru-yönlü (yaka=üst) alpha mask klasörü (golden-set DEĞİL)",
    )
    parser.add_argument("--out-dir", type=Path, default=ROOT / "data" / "rotnet_training")
    parser.add_argument("--aug-per-source", type=int, default=10)
    parser.add_argument("--val-ratio", type=float, default=0.20)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    np.random.seed(args.seed)
    sources = _load_source_masks(args.source_dir, include_golden=False)
    if not sources:
        print("Kaynak mask yok", file=sys.stderr)
        return 1

    if args.out_dir.exists():
        for old in args.out_dir.rglob("*.png"):
            old.unlink()

    info = _write_source_split(
        sources,
        args.out_dir,
        aug_per_source=args.aug_per_source,
        val_ratio=args.val_ratio,
        seed=args.seed,
    )
    counts = info["counts"]
    meta = {
        "n_source_masks": len(sources),
        "train_sources": info["train_sources"],
        "val_sources": info["val_sources"],
        "source_split": info["source_split"],
        "per_source": info["per_source"],
        "train": counts["train"],
        "val": counts["val"],
        "classes": [0, 90, 180, 270],
        "horizontal_flip": False,
        "split_unit": "source_mask",
        "golden_set_in_training": False,
        "note": (
            "Split kaynak bazında. Golden-set (synthetic_uneck/crewneck) eğitimde yok. "
            "Val model seçimi içindir; gerçek genelleme golden-set hold-out'tur."
        ),
    }
    (args.out_dir / "manifest.json").write_text(
        json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(json.dumps(meta, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
