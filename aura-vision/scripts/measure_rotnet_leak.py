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


def _source_id(rel: str) -> str:
    """syn_u_c_a00_90.png → syn_u_c."""
    stem = Path(rel).name
    if "_a" in stem:
        return stem.rsplit("_a", 1)[0]
    return stem


def _class_from_rel(rel: str) -> int:
    """train/val/<class>/file.png → class int."""
    parts = Path(rel).parts
    return int(parts[0])


def _load_split(split_dir: Path) -> list[tuple[str, int, np.ndarray]]:
    items = []
    for path in sorted(split_dir.rglob("*.png")):
        rel = str(path.relative_to(split_dir))
        arr = np.asarray(
            Image.open(path).convert("L").resize((SIZE, SIZE), Image.Resampling.NEAREST),
            dtype=np.float32,
        )
        arr = (arr > 127).astype(np.float32)
        items.append((rel, _class_from_rel(rel), arr))
    return items


def main() -> int:
    data = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "data" / "rotnet_training"
    train = _load_split(data / "train")
    val = _load_split(data / "val")
    if not train or not val:
        print("train/val boş", file=sys.stderr)
        return 1
    train_stack = np.stack([a for _, _, a in train], axis=0)
    train_y = np.array([y for _, y, _ in train], dtype=np.int64)
    train_src = [_source_id(n) for n, _, _ in train]
    diffs = []
    exact = 0
    nn_correct = 0
    nn_same_source = 0
    for name, y_true, v in val:
        d = np.abs(train_stack - v).mean(axis=(1, 2))
        nn = int(np.argmin(d))
        mn = float(d[nn])
        diffs.append(mn)
        if mn == 0.0:
            exact += 1
        if int(train_y[nn]) == int(y_true):
            nn_correct += 1
        if train_src[nn] == _source_id(name):
            nn_same_source += 1
    diffs_sorted = sorted(diffs)
    mid = len(diffs_sorted) // 2
    if len(diffs_sorted) % 2:
        median = diffs_sorted[mid]
    else:
        median = 0.5 * (diffs_sorted[mid - 1] + diffs_sorted[mid])
    n_val = len(val)
    report = {
        "train_n": len(train),
        "val_n": n_val,
        "exact_match_count": exact,
        "exact_match_rate": round(exact / n_val, 4),
        "knn1_classification_accuracy": round(nn_correct / n_val, 4),
        "knn1_correct": nn_correct,
        "knn1_same_source_neighbor_count": nn_same_source,
        "knn1_same_source_rate": round(nn_same_source / n_val, 4),
        "min_diff": round(min(diffs), 6),
        "median_diff": round(median, 6),
        "mean_diff": round(float(np.mean(diffs)), 6),
        "max_diff": round(max(diffs), 6),
        "chance": 0.25,
        "note": (
            "1-NN sınıf doğruluğu: val örneğinin piksel-en-yakın train komşusunun "
            "rotasyon etiketi. Sızıntılı split'te 0.932 idi."
        ),
    }
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
