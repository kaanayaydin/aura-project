#!/usr/bin/env python3
"""RotNet eğitimi: cross-entropy + Adam + early stopping. ONNX export."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import torch  # noqa: E402
import torch.nn as nn  # noqa: E402
from torch.utils.data import DataLoader, Dataset  # noqa: E402

from app.models.rotnet_classifier import ROTNET_CLASSES, RotNetClassifier  # noqa: E402

CLASS_TO_IDX = {c: i for i, c in enumerate(ROTNET_CLASSES)}


class MaskFolderDataset(Dataset):
    def __init__(self, root: Path, size: int) -> None:
        self.size = size
        self.items: list[tuple[Path, int]] = []
        for label in ROTNET_CLASSES:
            folder = root / str(label)
            if not folder.is_dir():
                continue
            idx = CLASS_TO_IDX[label]
            for path in sorted(folder.glob("*.png")):
                self.items.append((path, idx))
        if not self.items:
            raise FileNotFoundError(f"Veri yok: {root}")

    def __len__(self) -> int:
        return len(self.items)

    def __getitem__(self, i: int):
        path, idx = self.items[i]
        img = Image.open(path).convert("L").resize((self.size, self.size), Image.Resampling.NEAREST)
        arr = (np.asarray(img, dtype=np.float32) / 255.0 > 0.5).astype(np.float32)
        tensor = torch.from_numpy(arr[None, ...])
        return tensor, idx


@torch.no_grad()
def _eval(model: nn.Module, loader: DataLoader, device: torch.device) -> tuple[float, np.ndarray, float]:
    model.eval()
    crit = nn.CrossEntropyLoss()
    total_loss = 0.0
    n = 0
    correct = 0
    cm = np.zeros((4, 4), dtype=np.int64)
    for x, y in loader:
        x, y = x.to(device), y.to(device)
        logits = model(x)
        loss = crit(logits, y)
        pred = logits.argmax(dim=1)
        total_loss += float(loss.item()) * x.size(0)
        correct += int((pred == y).sum().item())
        n += x.size(0)
        for t, p in zip(y.cpu().numpy(), pred.cpu().numpy()):
            cm[int(t), int(p)] += 1
    return total_loss / max(n, 1), cm, correct / max(n, 1)


def _per_class(cm: np.ndarray) -> dict[str, float]:
    out = {}
    for i, label in enumerate(ROTNET_CLASSES):
        row = cm[i]
        denom = int(row.sum())
        out[str(label)] = float(row[i] / denom) if denom else 0.0
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", type=Path, default=ROOT / "data" / "rotnet_training")
    parser.add_argument("--img-size", type=int, default=64)
    parser.add_argument("--epochs", type=int, default=25)
    parser.add_argument("--patience", type=int, default=6)
    parser.add_argument("--batch", type=int, default=32)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument(
        "--out",
        type=Path,
        default=ROOT / "app" / "models" / "weights" / "rotnet_v1.onnx",
    )
    args = parser.parse_args()

    device = torch.device("cpu")
    train_ds = MaskFolderDataset(args.data_dir / "train", args.img_size)
    val_ds = MaskFolderDataset(args.data_dir / "val", args.img_size)
    train_loader = DataLoader(train_ds, batch_size=args.batch, shuffle=True, num_workers=0)
    val_loader = DataLoader(val_ds, batch_size=args.batch, shuffle=False, num_workers=0)

    model = RotNetClassifier().to(device)
    opt = torch.optim.Adam(model.parameters(), lr=args.lr)
    crit = nn.CrossEntropyLoss()

    best_loss = float("inf")
    best_state = None
    stale = 0
    t0 = time.time()
    history = []
    for epoch in range(1, args.epochs + 1):
        model.train()
        running = 0.0
        n = 0
        for x, y in train_loader:
            x, y = x.to(device), y.to(device)
            opt.zero_grad(set_to_none=True)
            logits = model(x)
            loss = crit(logits, y)
            loss.backward()
            opt.step()
            running += float(loss.item()) * x.size(0)
            n += x.size(0)
        train_loss = running / max(n, 1)
        val_loss, cm, val_acc = _eval(model, val_loader, device)
        history.append(
            {"epoch": epoch, "train_loss": train_loss, "val_loss": val_loss, "val_acc": val_acc}
        )
        print(
            f"epoch {epoch:02d} train={train_loss:.4f} val={val_loss:.4f} acc={val_acc:.3f}"
        )
        # val, model seçiminde kullanılıyor (early-stop / best checkpoint).
        # Bu yüzden gerçek genelleme testi golden-set'in TAMAMEN AYRI,
        # eğitime hiç girmemiş kısmıdır (hold-out; generate script golden'ı
        # kaynağa almaz). Val düşüşü beklenen ve istenen bir işarettir.
        if val_loss + 1e-5 < best_loss:
            best_loss = val_loss
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}
            stale = 0
        else:
            stale += 1
            if stale >= args.patience:
                print(f"early stopping @ epoch {epoch}")
                break

    if best_state is not None:
        model.load_state_dict(best_state)
    val_loss, cm, val_acc = _eval(model, val_loader, device)
    per_class = _per_class(cm)
    swap_90_270 = int(cm[1, 3] + cm[3, 1])
    report = {
        "val_loss": round(val_loss, 4),
        "val_acc": round(val_acc, 4),
        "per_class_accuracy": per_class,
        "confusion_matrix": {
            "labels": list(ROTNET_CLASSES),
            "rows_true_cols_pred": cm.tolist(),
        },
        "confusion_90_vs_270_count": swap_90_270,
        "train_n": len(train_ds),
        "val_n": len(val_ds),
        "img_size": args.img_size,
        "seconds": round(time.time() - t0, 1),
        "history": history,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    report_path = args.out.with_suffix(".metrics.json")
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "history"}, indent=2))

    model.eval()
    dummy = torch.zeros(1, 1, args.img_size, args.img_size, dtype=torch.float32)
    export_kw = dict(
        input_names=["mask"],
        output_names=["logits"],
        opset_version=17,
        dynamic_axes={"mask": {0: "batch"}, "logits": {0: "batch"}},
    )
    try:
        torch.onnx.export(model, dummy, str(args.out), dynamo=False, **export_kw)
    except TypeError:
        torch.onnx.export(model, dummy, str(args.out), **export_kw)
    print(f"ONNX yazildi: {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
