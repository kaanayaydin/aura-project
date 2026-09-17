"""Model agirlik / ONNX on-isinma (pre-warm).

Yerel gelistirmede varsayilan KAPALI (maliyet / indirme yok).
Cloud image build veya cold-start oncesi:

  AURA_VTON_PREWARM=true python scripts/prewarm_cache.py
  AURA_VTON_PREWARM_CATVTON=true  # buyuk CatVTON + SD inpaint (~GB)

SCHP ONNX (~65MB) guvenli; CatVTON opsiyonel.
"""

from __future__ import annotations

import logging
import os
import sys
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
logger = logging.getLogger("aura.vton.prewarm")


def _env_bool(name: str, default: bool = False) -> bool:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def prewarm_schp(cache_dir: Path) -> bool:
    """SCHP INT8 ONNX → cache_dir/schp/."""
    try:
        from huggingface_hub import hf_hub_download
    except ImportError:
        logger.warning("huggingface_hub yok — SCHP prewarm atlandi")
        return False

    model_id = os.environ.get("AURA_VTON_SCHP_MODEL_ID", "pirocheto/schp-lip-20")
    onnx_file = os.environ.get(
        "AURA_VTON_SCHP_ONNX_FILE",
        "onnx/schp-lip-20-int8-static.onnx",
    )
    schp_dir = cache_dir / "schp"
    schp_dir.mkdir(parents=True, exist_ok=True)
    logger.info("SCHP indiriliyor: %s / %s → %s", model_id, onnx_file, schp_dir)
    path = hf_hub_download(
        repo_id=model_id,
        filename=onnx_file,
        cache_dir=str(schp_dir / "hub"),
    )
    logger.info("SCHP hazir: %s", path)
    return True


def prewarm_catvton(cache_dir: Path) -> bool:
    """CatVTON attn + base inpaint snapshot (buyuk; yalnizca acikca istenirse)."""
    try:
        from huggingface_hub import snapshot_download
    except ImportError:
        logger.warning("huggingface_hub yok — CatVTON prewarm atlandi")
        return False

    hub = cache_dir / "hub"
    hub.mkdir(parents=True, exist_ok=True)
    base_id = os.environ.get(
        "AURA_VTON_BASE_MODEL_ID",
        "booksforcharlie/stable-diffusion-inpainting",
    )
    attn_id = os.environ.get("AURA_VTON_ATTN_MODEL_ID", "zhengchong/CatVTON")

    logger.info("CatVTON base snapshot: %s", base_id)
    snapshot_download(repo_id=base_id, cache_dir=str(hub))
    logger.info("CatVTON attn snapshot: %s", attn_id)
    snapshot_download(repo_id=attn_id, cache_dir=str(hub))

    try:
        snapshot_download(repo_id="stabilityai/sd-vae-ft-mse", cache_dir=str(hub))
    except Exception as exc:  # noqa: BLE001
        logger.warning("VAE snapshot atlandi: %s", exc)

    logger.info("CatVTON cache hazir: %s", hub)
    return True


def main() -> int:
    if not _env_bool("AURA_VTON_PREWARM", default=True):
        # Script dogrudan cagrildiginda varsayilan acik; entrypoint env ile kontrol eder
        logger.info("AURA_VTON_PREWARM=false — cikiliyor")
        return 0

    cache = Path(
        os.environ.get(
            "AURA_VTON_CACHE_DIR",
            str(Path.home() / ".cache" / "aura-vton"),
        )
    ).expanduser()
    cache.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("HF_HOME", str(cache / "hf"))
    os.environ.setdefault("HUGGINGFACE_HUB_CACHE", str(cache / "hf" / "hub"))

    ok_schp = False
    ok_cat = False
    try:
        ok_schp = prewarm_schp(cache)
    except Exception as exc:  # noqa: BLE001
        logger.warning("SCHP prewarm basarisiz (graceful): %s", exc)

    if _env_bool("AURA_VTON_PREWARM_CATVTON", default=False):
        try:
            ok_cat = prewarm_catvton(cache)
        except Exception as exc:  # noqa: BLE001
            logger.warning("CatVTON prewarm basarisiz (graceful): %s", exc)
    else:
        logger.info("CatVTON prewarm kapali (AURA_VTON_PREWARM_CATVTON!=true)")

    logger.info("Prewarm bitti schp=%s catvton=%s cache=%s", ok_schp, ok_cat, cache)
    return 0


if __name__ == "__main__":
    sys.exit(main())
