"""Küçük 4-sınıf yön CNN — silüet thumbnail (tek kanal).

Sınıflar = uygulanacak kardinal düzeltme: 0 / 90 / 180 / 270 (CCW).
Girdi: 1×H×W binary/grayscale mask. Doku/renk yok.
"""

from __future__ import annotations

import torch
import torch.nn as nn

ROTNET_CLASSES = (0, 90, 180, 270)
CLASS_TO_EDGE = {0: "top", 90: "right", 180: "bottom", 270: "left"}
EDGE_TO_CLASS = {v: k for k, v in CLASS_TO_EDGE.items()}


class RotNetClassifier(nn.Module):
    """3–4 conv bloğu + GAP + 4-logit. CPU'da dakikalar mertebesinde eğitilir."""

    def __init__(self, n_classes: int = 4) -> None:
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(1, 16, kernel_size=3, padding=1),
            nn.BatchNorm2d(16),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2),
            nn.Conv2d(16, 32, kernel_size=3, padding=1),
            nn.BatchNorm2d(32),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2),
            nn.Conv2d(32, 64, kernel_size=3, padding=1),
            nn.BatchNorm2d(64),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2),
            nn.Conv2d(64, 128, kernel_size=3, padding=1),
            nn.BatchNorm2d(128),
            nn.ReLU(inplace=True),
            nn.AdaptiveAvgPool2d(1),
        )
        self.classifier = nn.Linear(128, n_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        feats = self.features(x)
        return self.classifier(feats.flatten(1))


def preprocess_mask(alpha, size: int = 64):
    """PIL L/RGBA veya ndarray → (1, 1, size, size) float32 [0,1]."""
    from PIL import Image
    import numpy as np

    if isinstance(alpha, Image.Image):
        if alpha.mode == "RGBA":
            mask = alpha.split()[-1]
        else:
            mask = alpha.convert("L")
        mask = mask.resize((size, size), Image.Resampling.NEAREST)
        arr = np.asarray(mask, dtype=np.float32) / 255.0
    else:
        arr = np.asarray(alpha)
        if arr.ndim == 3:
            arr = arr[..., -1]
        img = Image.fromarray(
            (arr > 127).astype("uint8") * 255 if arr.dtype != np.uint8 else arr,
            mode="L",
        )
        img = img.resize((size, size), Image.Resampling.NEAREST)
        arr = np.asarray(img, dtype=np.float32) / 255.0
    arr = (arr > 0.5).astype("float32")
    return arr[None, None, ...]
