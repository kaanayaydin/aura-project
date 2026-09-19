#!/usr/bin/env python3
"""Val→train 1-NN piksel sızıntı ölçümü.

Her val maskesi 64×64 binary'ye indirilir; train'deki en yakın örneğe
L1/n farkı hesaplanır. Birebir kopya: min_diff == 0.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SIZE = 64


def _load_split(split_dir: Path) -> list[tuple[str, np.ndarray]]:
    items = []
    for path in sorted(split_dir.rglob("*.png")):
        arr = np.asarray(
            Image.open(path).convert("L").resize((SIZE, SIZE), Image.Resampling.NEAREST),
            dtype=np.float32,
        )
        arr = (arr > 127).astype(np.float32)
        items.append((str(path.relative_to(split_dir)), arr))
    return items


def main() -> int:
    data = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "data" / "rotnet_training"
    train = _load_split(data / "train")
    val = _load_split(data / "val")
    if not train or not val:
        print("train/val boş", file=sys.stderr)
        return 1
    train_stack = np.stack([a for _, a in train], axis=0)
    diffs = []
    exact = 0
    for name, v in val:
        d = np.abs(train_stack - v).mean(axis=(1, 2))
        mn = float(d.min())
        diffs.append(mn)
        if mn == 0.0:
            exact += 1
    diffs_sorted = sorted(diffs)
    mid = len(diffs_sorted) // 2
    if len(diffs_sorted) % 2:
        median = diffs_sorted[mid]
    else:
        median = 0.5 * (diffs_sorted[mid - 1] + diffs_sorted[mid])
    report = {
        "train_n": len(train),
        "val_n": len(val),
        "exact_match_count": exact,
        "exact_match_rate": round(exact / len(val), 4),
        "min_diff": round(min(diffs), 6),
        "median_diff": round(median, 6),
        "mean_diff": round(float(np.mean(diffs)), 6),
        "max_diff": round(max(diffs), 6),
    }
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
