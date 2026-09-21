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
import uuid
from dataclasses import dataclass
from typing import Optional

import numpy as np
from PIL import Image

from app.core.build_info import git_commit_short
from app.core.config import settings
from app.services.garment_polish import polish_studio_cutout
from app.services.garment_studio import (
    boost_contrast,
    chroma_cutout,
    compose_studio,
    has_meaningful_alpha,
    is_low_confidence_mask,
    parse_hex_color,
    refine_garment_alpha,
    unusable_mask_reason,
)


class UnusableCutoutError(ValueError):
    """Cutout bos/carsaf — HTTP 422 rejected_reason."""

    def __init__(self, rejected_reason: str, user_message: str):
        self.rejected_reason = rejected_reason
        self.user_message = user_message
        super().__init__(rejected_reason)

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
    job_id: str = ""
    commit: str = ""
    debug_dir: Optional[str] = None
    result_filename: str = ""
    low_confidence: bool = False
    rotation_suggested: str = "top"
    rotation_deg_applied: int = 0
    rotation_method: str = "none"
    requires_confirmation: bool = False
    cutout_rgba: Optional[Image.Image] = None
    deskew_step_executed: bool = False
    deskew_input_angle_estimated: float = 0.0
    deskew_skip_reason: Optional[str] = None
    ensemble_confidence: str = ""


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
        debug: bool = False,
        job_id: Optional[str] = None,
        skip_orientation: bool = False,
    ) -> NormalizeResult:
        """Dekupaj + (opsiyonel polish) + 3:4 framing.

        ``skip_orientation=True`` (onaylı yükleme / alreadyNormalized):
        - rembg hiç denenmez (``prefer_rembg=False`` yetmez; ``_cutout`` 3. dalı
          ``enabled and not prefer`` ile yine rembg çalıştırırdı).
        - polish zinciri tamamen atlanır: askı temizliği, deskew, kardinal
          rotasyon/ensemble **ve** catalog press. Yalnız ``compose_studio``
          (3:4 crop/letterbox) uygulanır.
        - Anlamlı alfa varsa o korunur (yeniden kesilmez). Alfasız RGB
          girdide ``chroma_cutout`` silüeti yeniden keser — rembg atlanır,
          chroma atlanmaz. Pixel-idempotent değil (önceki ölçüm: chroma
          vs. kaynak alfa IoU ~0.93–0.97).
        """
        if not raw_bytes:
            raise ValueError("Bos gorsel")

        try:
            image = Image.open(io.BytesIO(raw_bytes))
            image.load()
        except Exception as exc:  # noqa: BLE001
            raise ValueError(f"Gorsel okunamadi: {exc}") from exc

        job = (job_id or uuid.uuid4().hex[:12]).strip() or uuid.uuid4().hex[:12]
        commit = git_commit_short()
        polish_trace: dict = {}

        # skip_orientation: rembg TAMAMEN kapalı. prefer=False 3. dalı
        # (studio_rembg_enabled and not prefer) kapatmazdı; üretim
        # AURA_STUDIO_REMBG=true iken onaylı PNG yine rembg'e gidiyordu.
        if skip_orientation:
            prefer_rembg = False
            allow_rembg = force_rembg is True
        else:
            prefer_rembg = (
                settings.studio_prefer_rembg if force_rembg is None else bool(force_rembg)
            )
            allow_rembg = True
        try:
            cutout, source = self._cutout(
                image, prefer_rembg=prefer_rembg, allow_rembg=allow_rembg
            )
        except Exception:
            logger.exception("Cutout basarisiz — chroma fallback")
            cutout = chroma_cutout(image.convert("RGB"))
            source = "chroma"

        if skip_orientation:
            logger.info(
                "skip_orientation: rembg/hanger/deskew/cardinal/press atlandi — yalniz 3:4 framing"
            )
            polish_trace["rotation_method"] = "skipped_already_normalized"
            polish_trace["rotation_deg_applied"] = 0
            polish_trace["requires_confirmation"] = False
            polish_trace["deskew_step_executed"] = False
            polish_trace["deskew_skip_reason"] = "skip_orientation"
        elif settings.studio_polish_enabled:
            try:
                cutout = polish_studio_cutout(
                    cutout,
                    remove_hanger=settings.studio_remove_hanger,
                    deskew=settings.studio_deskew,
                    press=settings.studio_catalog_press,
                    trace=polish_trace,
                )
                logger.info(
                    "Studio polish OK (hanger=%s deskew=%s press=%s)",
                    settings.studio_remove_hanger,
                    settings.studio_deskew,
                    settings.studio_catalog_press,
                )
            except Exception:
                logger.exception("Studio polish basarisiz — ham cutout ile framing")

        reject = unusable_mask_reason(cutout)
        if reject:
            logger.info("Normalize reddedildi: %s (bos/carsaf tuval yazilmayacak)", reject)
            msg = (
                "Arka planı ayırt edemedik, lütfen daha sade bir zeminde çekin"
            )
            raise UnusableCutoutError(reject, msg)

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
        cutout_keep = cutout.copy() if isinstance(cutout, Image.Image) else None

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

        result_filename = "result_{0}_{1}.png".format(job, commit)
        debug_dir: Optional[str] = None
        if debug:
            try:
                from app.services.orientation_debug import (
                    OrientationTrace,
                    render_candidates_overlay,
                    write_orientation_debug,
                )

                mask_deskewed = polish_trace.get("mask_deskewed")
                scores = polish_trace.get("scores") or {}
                candidates = None
                if mask_deskewed is not None:
                    candidates = render_candidates_overlay(mask_deskewed, scores)
                trace = OrientationTrace(
                    job_id=job,
                    mask_raw=polish_trace.get("mask_raw"),
                    mask_deskewed=mask_deskewed,
                    candidates=candidates,
                    mask_rotated=polish_trace.get("mask_rotated"),
                    framed=framed,
                    scores=scores,
                    best_edge=str(polish_trace.get("best_edge") or "top"),
                    deskew_angle_applied=float(
                        polish_trace.get("deskew_angle_applied") or 0.0
                    ),
                    deskew_step_executed=bool(
                        polish_trace.get("deskew_step_executed")
                    ),
                    deskew_input_angle_estimated=polish_trace.get(
                        "deskew_input_angle_estimated"
                    ),
                    deskew_skip_reason=polish_trace.get("deskew_skip_reason"),
                    deskew_min_abs_deg=polish_trace.get("deskew_min_abs_deg"),
                    rotation_deg_applied=int(
                        polish_trace.get("rotation_deg_applied") or 0
                    ),
                    rotation_method=str(
                        polish_trace.get("rotation_method") or "none"
                    ),
                    cutout_source=source,
                    low_confidence=bool(
                        polish_trace.get("low_confidence")
                        or (scores or {}).get("low_confidence")
                    ),
                    rotation_suggested=str(
                        polish_trace.get("rotation_suggested")
                        or polish_trace.get("best_edge")
                        or "top"
                    ),
                    requires_confirmation=bool(
                        polish_trace.get("requires_confirmation")
                        or polish_trace.get("low_confidence")
                    ),
                    ensemble_confidence=str(
                        polish_trace.get("ensemble_confidence") or ""
                    ),
                )
                debug_dir = str(write_orientation_debug(trace))
            except Exception:
                logger.exception("Orientation debug yazilamadi")

        logger.info(
            "Normalize OK source=%s size=%sx%s png_bytes=%s job=%s commit=%s debug=%s",
            source,
            framed.size[0],
            framed.size[1],
            len(png),
            job,
            commit,
            debug_dir,
        )
        return NormalizeResult(
            image=framed,
            png_bytes=png,
            width=framed.size[0],
            height=framed.size[1],
            cutout_source=source,
            aspect=aspect_key,
            job_id=job,
            commit=commit,
            debug_dir=debug_dir,
            result_filename=result_filename,
            low_confidence=bool(polish_trace.get("low_confidence")),
            rotation_suggested=str(
                polish_trace.get("rotation_suggested")
                or polish_trace.get("best_edge")
                or "top"
            ),
            rotation_deg_applied=int(polish_trace.get("rotation_deg_applied") or 0),
            rotation_method=str(polish_trace.get("rotation_method") or "none"),
            requires_confirmation=bool(polish_trace.get("requires_confirmation")),
            ensemble_confidence=str(polish_trace.get("ensemble_confidence") or ""),
            cutout_rgba=cutout_keep,
            deskew_step_executed=bool(polish_trace.get("deskew_step_executed")),
            deskew_input_angle_estimated=float(
                polish_trace.get("deskew_input_angle_estimated") or 0.0
            ),
            deskew_skip_reason=polish_trace.get("deskew_skip_reason"),
        )

    def normalize_to_base64(self, raw_bytes: bytes, **kwargs) -> tuple[str, NormalizeResult]:
        result = self.normalize(raw_bytes, **kwargs)
        return base64.b64encode(result.png_bytes).decode("ascii"), result

    def _cutout(
        self,
        image: Image.Image,
        *,
        prefer_rembg: bool,
        allow_rembg: bool = True,
    ) -> tuple[Image.Image, str]:
        """Cutout kaynak sırası.

        1) rembg (allow + prefer + enabled)
        2) anlamlı alfa
        3) rembg fallback (allow + enabled + not prefer) — skip_orientation
           bunu da kapatır; aksi halde üretim rembg açıkken yine çalışırdı
        4) chroma
        """
        rgb = image.convert("RGB")
        rgba = image.convert("RGBA") if image.mode in ("RGBA", "LA", "PA") else None

        if allow_rembg and prefer_rembg and settings.studio_rembg_enabled:
            rembg_out = self._try_rembg(rgb)
            if rembg_out is not None:
                fixed = refine_garment_alpha(rembg_out)
                logger.info("Normalize cutout: rembg (refine)")
                return fixed, "rembg"

        if rgba is not None and has_meaningful_alpha(rgba):
            fixed = refine_garment_alpha(rgba)
            logger.info("Normalize cutout: existing alpha (refine)")
            return fixed, "alpha"

        if allow_rembg and settings.studio_rembg_enabled and not prefer_rembg:
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
