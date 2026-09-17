from __future__ import annotations

import logging
import os

logger = logging.getLogger("aura.vton.device")


def configure_mps_fallback() -> None:
    """Apple Silicon: CPU fallback + Metal bellek watermark."""
    os.environ.setdefault("PYTORCH_ENABLE_MPS_FALLBACK", "1")
    # 0.0 → tahsis ust sinirini gevsetir (Invalid buffer size / OOM azaltir)
    try:
        from app.config import settings

        ratio = str(settings.mps_high_watermark_ratio or "0.0")
    except Exception:  # noqa: BLE001
        ratio = "0.0"
    os.environ.setdefault("PYTORCH_MPS_HIGH_WATERMARK_RATIO", ratio)


def resolve_torch_device(preferred: str = "auto") -> str:
    """auto | mps | cuda | cpu — mevcut donanima gore secim."""
    configure_mps_fallback()
    try:
        import torch
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("torch yuklu degil; pip install -r requirements-ml.txt") from exc

    choice = (preferred or "auto").strip().lower()
    if choice == "cpu":
        return "cpu"
    if choice == "mps":
        if torch.backends.mps.is_available():
            return "mps"
        logger.warning("MPS istenmis ama kullanilamiyor; CPU'ya dusuluyor")
        return "cpu"
    if choice == "cuda":
        if torch.cuda.is_available():
            return "cuda"
        logger.warning("CUDA istenmis ama kullanilamiyor; CPU'ya dusuluyor")
        return "cpu"

    # auto
    if torch.backends.mps.is_available():
        logger.info("Torch cihaz: MPS (Apple Silicon)")
        return "mps"
    if torch.cuda.is_available():
        logger.info("Torch cihaz: CUDA")
        return "cuda"
    logger.info("Torch cihaz: CPU")
    return "cpu"


def resolve_weight_dtype(device: str, precision: str = "auto"):
    import torch

    precision = (precision or "auto").strip().lower()
    if precision == "fp32":
        return torch.float32
    if precision == "fp16":
        return torch.float16
    if precision == "bf16":
        return torch.bfloat16
    # auto: MPS/CUDA → fp16, CPU → fp32
    if device in {"mps", "cuda"}:
        return torch.float16
    return torch.float32
