"""Garment Studio Normalizer — dekupaj + stüdyo framing.

Kaynak onceligi (varsayilan):
1) rembg (onnxruntime varsa; kontrast boost + refine)
2) Anlamli alfa (SAM cutout) — ayni refine
3) Kose chroma + agresif refine
Ham gorseli (carsaf) oldugu gibi BIRAKMAZ.
"""

from __future__ import annotations

import base64
import io
import logging
from dataclasses import dataclass
from typing import Optional

import numpy as np
from PIL import Image

from app.core.config import settings
from app.services.garment_studio import (
    boost_contrast,
    chroma_cutout,
    compose_studio,
    has_meaningful_alpha,
    is_low_confidence_mask,
    parse_hex_color,
    refine_garment_alpha,
)

logger = logging.getLogger("aura.vision.normalize")

_PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


@dataclass(frozen=True)
class NormalizeResult:
    image: Image.Image
    png_bytes: bytes
    width: int
    height: int
    cutout_source: str  # alpha | rembg | chroma
    aspect: str


def _onnxruntime_available() -> bool:
    try:
        import onnxruntime  # noqa: F401

        return True
    except Exception:
        return False


def _as_rgba_image(out: object) -> Image.Image:
    """rembg ciktisini guvenli PIL RGBA'ya cevir (bytes | ndarray | Image)."""
    if isinstance(out, Image.Image):
        return out.convert("RGBA")
    if isinstance(out, (bytes, bytearray)):
        return Image.open(io.BytesIO(out)).convert("RGBA")
    if isinstance(out, np.ndarray):
        arr = out
        if arr.dtype != np.uint8:
            arr = np.clip(arr, 0, 255).astype(np.uint8)
        if arr.ndim == 2:
            rgb = Image.fromarray(arr, mode="L").convert("RGB")
            return rgb.convert("RGBA")
        if arr.ndim == 3 and arr.shape[2] == 4:
            return Image.fromarray(arr, mode="RGBA")
        if arr.ndim == 3 and arr.shape[2] == 3:
            return Image.fromarray(arr, mode="RGB").convert("RGBA")
        raise ValueError(f"Desteklenmeyen ndarray shape: {arr.shape}")
    raise ValueError(f"Desteklenmeyen rembg cikti tipi: {type(out)}")


class GarmentNormalizer:
    def normalize(
        self,
        raw_bytes: bytes,
        *,
        aspect: Optional[str] = None,
        background_hex: Optional[str] = None,
        drop_shadow: Optional[bool] = None,
        long_side: Optional[int] = None,
        force_rembg: Optional[bool] = None,
    ) -> NormalizeResult:
        if not raw_bytes:
            raise ValueError("Bos gorsel")

        try:
            image = Image.open(io.BytesIO(raw_bytes))
            image.load()
        except Exception as exc:  # noqa: BLE001
            raise ValueError(f"Gorsel okunamadi: {exc}") from exc

        prefer_rembg = settings.studio_prefer_rembg if force_rembg is None else bool(force_rembg)
        try:
            cutout, source = self._cutout(image, prefer_rembg=prefer_rembg)
        except Exception:
            logger.exception("Cutout basarisiz — chroma fallback")
            cutout = chroma_cutout(image.convert("RGB"))
            source = "chroma"

        aspect_key = (aspect or settings.studio_aspect or "3:4").strip()
        if aspect_key not in ("3:4", "1:1"):
            aspect_key = "3:4"
        bg = parse_hex_color(background_hex or settings.studio_background_hex)
        shadow = settings.studio_drop_shadow if drop_shadow is None else bool(drop_shadow)
        side = long_side or settings.studio_long_side

        framed = compose_studio(
            cutout,
            aspect=aspect_key,  # type: ignore[arg-type]
            long_side=side,
            background=bg,
            margin_ratio=settings.studio_margin_ratio,
            drop_shadow=shadow,
        )
        if framed.mode != "RGB":
            framed = framed.convert("RGB")

        buf = io.BytesIO()
        framed.save(buf, format="PNG", optimize=True)
        png = buf.getvalue()
        if not png.startswith(_PNG_MAGIC) or len(png) < 64:
            raise ValueError(f"Normalize PNG gecersiz (bytes={len(png)})")

        try:
            check = Image.open(io.BytesIO(png))
            check.load()
            if check.size != framed.size:
                raise ValueError("PNG boyut uyusmazligi")
        except Exception as exc:  # noqa: BLE001
            raise ValueError(f"Normalize PNG okunamadi: {exc}") from exc

        logger.info(
            "Normalize OK source=%s size=%sx%s png_bytes=%s",
            source,
            framed.size[0],
            framed.size[1],
            len(png),
        )
        return NormalizeResult(
            image=framed,
            png_bytes=png,
            width=framed.size[0],
            height=framed.size[1],
            cutout_source=source,
            aspect=aspect_key,
        )

    def normalize_to_base64(self, raw_bytes: bytes, **kwargs) -> tuple[str, NormalizeResult]:
        result = self.normalize(raw_bytes, **kwargs)
        return base64.b64encode(result.png_bytes).decode("ascii"), result

    def _cutout(self, image: Image.Image, *, prefer_rembg: bool) -> tuple[Image.Image, str]:
        rgb = image.convert("RGB")
        rgba = image.convert("RGBA") if image.mode in ("RGBA", "LA", "PA") else None

        if prefer_rembg and settings.studio_rembg_enabled:
            rembg_out = self._try_rembg(rgb)
            if rembg_out is not None:
                fixed = refine_garment_alpha(rembg_out)
                logger.info("Normalize cutout: rembg (refine)")
                return fixed, "rembg"

        if rgba is not None and has_meaningful_alpha(rgba):
            fixed = refine_garment_alpha(rgba)
            logger.info("Normalize cutout: existing alpha (refine)")
            return fixed, "alpha"

        if settings.studio_rembg_enabled and not prefer_rembg:
            rembg_out = self._try_rembg(rgb)
            if rembg_out is not None:
                fixed = refine_garment_alpha(rembg_out)
                logger.info("Normalize cutout: rembg (refine)")
                return fixed, "rembg"

        logger.info("Normalize cutout: chroma + aggressive refine")
        return chroma_cutout(rgb), "chroma"

    @staticmethod
    def _try_rembg(rgb: Image.Image) -> Image.Image | None:
        if not settings.studio_rembg_enabled:
            return None
        # rembg onnxruntime yokken SystemExit / hard fail uretebilir — once kontrol
        if not _onnxruntime_available():
            logger.warning(
                "rembg atlandi: onnxruntime yok — pip install 'rembg[cpu]' "
                "(aksi halde rembg 500/exit uretebilir)"
            )
            return None
        try:
            from rembg import remove  # type: ignore
        except BaseException as exc:  # noqa: BLE001 — SystemExit dahil
            if isinstance(exc, KeyboardInterrupt):
                raise
            logger.warning("rembg import basarisiz: %s", exc)
            return None
        try:
            boosted = boost_contrast(rgb.convert("RGB"), factor=1.75)
            out = remove(boosted)
            rgba = _as_rgba_image(out)
            # Boyut: rembg cikti (W,H) PIL; numpy alpha (H,W)
            if rgba.size != rgb.size:
                logger.warning(
                    "rembg boyut farki rgb=%s rembg=%s — resize",
                    rgb.size,
                    rgba.size,
                )
                rgba = rgba.resize(rgb.size, Image.Resampling.LANCZOS)
            arr = np.asarray(rgba.split()[-1], dtype=np.uint8)
            if arr.ndim != 2 or arr.shape != (rgb.size[1], rgb.size[0]):
                logger.warning("rembg alpha shape beklenmeyen: %s", getattr(arr, "shape", None))
                return None
            if is_low_confidence_mask(arr) or not has_meaningful_alpha(rgba):
                logger.warning(
                    "rembg zayif/anlamsiz alfa — refine'a birakiliyor (mean_opaque=%.2f)",
                    float((arr > 127).mean()),
                )
            return rgba
        except BaseException as exc:  # noqa: BLE001
            if isinstance(exc, KeyboardInterrupt):
                raise
            logger.warning("rembg basarisiz (chroma'ya dusulecek): %s", exc)
            return None


garment_normalizer = GarmentNormalizer()
