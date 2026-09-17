from __future__ import annotations

import logging
import threading
from pathlib import Path
from typing import Optional

from PIL import Image, ImageDraw

from app.config import settings
from app.services.errors import VtonInferenceError, classify_exception
from app.services.image_codec import decode_base64_image
from app.services.output_store import output_store

logger = logging.getLogger("aura.vton.model")


def _decode_image(raw: str | None, *, label: str = "image") -> Image.Image | None:
    """Person/garment base64 → RGB (data-URI + padding dayanikli)."""
    return decode_base64_image(raw, label=label)


class CatVtonModelLoader:
    """CatVTON loader — mock veya gercek Hugging Face agirliklari.

    Gercek mod:
      - Agirliklar `~/.cache/aura-vton/` (HF snapshot)
      - Cihaz: MPS → CUDA → CPU (`AURA_VTON_DEVICE`)
      - Cikti: disk PNG + optimize Base64 + HTTP URI
    """

    def __init__(self) -> None:
        self._ready = False
        self._lock = threading.Lock()
        self._pipeline = None
        self._device: str | None = None
        self._mode = "mock"

    def ensure_ready(self) -> None:
        with self._lock:
            if self._ready:
                return
            if settings.mock_model:
                self._mode = "mock"
                self._ready = True
                logger.info("CatVTON loader hazir (mock=true, v0.16.5)")
                return

            self._load_real_pipeline()
            self._mode = "catvton"
            self._ready = True
            logger.info(
                "CatVTON loader hazir (real, device=%s, cache=%s)",
                self._device,
                settings.cache_dir,
            )

    def _load_real_pipeline(self) -> None:
        try:
            import torch  # noqa: F401
            from app.services.catvton.pipeline import CatVTONPipeline
            from app.services.device import resolve_torch_device, resolve_weight_dtype
        except ImportError as exc:
            raise RuntimeError(
                "Gercek CatVTON icin ML bagimliliklari gerekli: "
                "pip install -r requirements-ml.txt"
            ) from exc

        cache = str(Path(settings.cache_dir).expanduser())
        Path(cache).mkdir(parents=True, exist_ok=True)

        device = resolve_torch_device(settings.device)
        dtype = resolve_weight_dtype(device, settings.precision)
        self._device = device

        # HF cache'i Aura dizinine yonlendir
        import os

        os.environ.setdefault("HF_HOME", cache)
        os.environ.setdefault("HUGGINGFACE_HUB_CACHE", str(Path(cache) / "hub"))

        self._pipeline = CatVTONPipeline(
            base_ckpt=settings.base_model_id,
            attn_ckpt=settings.attn_model_id,
            attn_ckpt_version=settings.attn_version,
            weight_dtype=dtype,
            device=device,
            skip_safety_check=True,
            use_tf32=device.startswith("cuda"),
            cache_dir=cache,
        )

    def is_ready(self) -> bool:
        return self._ready

    @property
    def mode(self) -> str:
        return self._mode

    def try_on(
        self,
        *,
        job_id: str,
        person_image_base64: Optional[str] = None,
        garment_image_base64: Optional[str] = None,
        person_image_url: Optional[str] = None,
        garment_image_url: Optional[str] = None,
        cloth_type: str = "upper",
    ) -> dict:
        self.ensure_ready()
        if settings.mock_model or self._pipeline is None:
            return self._try_on_mock(
                job_id=job_id,
                person_image_base64=person_image_base64,
                garment_image_base64=garment_image_base64,
                cloth_type=cloth_type,
            )
        return self._try_on_real(
            job_id=job_id,
            person_image_base64=person_image_base64,
            garment_image_base64=garment_image_base64,
            person_image_url=person_image_url,
            garment_image_url=garment_image_url,
            cloth_type=cloth_type,
        )

    def _try_on_mock(
        self,
        *,
        job_id: str,
        person_image_base64: Optional[str],
        garment_image_base64: Optional[str],
        cloth_type: str = "upper",
    ) -> dict:
        image = Image.new("RGB", (384, 512), color=(30, 34, 38))
        draw = ImageDraw.Draw(image)
        draw.rectangle((32, 48, 352, 464), outline=(212, 175, 55), width=3)
        draw.text((48, 220), "Aura CatVTON", fill=(212, 175, 55))
        draw.text((48, 250), f"mock job {job_id}", fill=(232, 228, 220))
        draw.text((48, 280), f"cloth={cloth_type}", fill=(180, 180, 180))
        draw.text((48, 310), "pose=mock-skip", fill=(140, 140, 140))
        stored = output_store.save(job_id, image)
        return {
            "resultImageUri": stored.result_image_uri,
            "resultImageBase64": stored.result_image_base64,
            "filePath": stored.file_path,
            "model": "catvton-mock",
            "device": "mock",
            "clothType": cloth_type,
            "maskSource": "mock",
            "poseSource": "none",
            "personProvided": bool(person_image_base64),
            "garmentProvided": bool(garment_image_base64),
        }

    def _try_on_real(
        self,
        *,
        job_id: str,
        person_image_base64: Optional[str],
        garment_image_base64: Optional[str],
        person_image_url: Optional[str] = None,
        garment_image_url: Optional[str] = None,
        cloth_type: str = "upper",
    ) -> dict:
        import torch

        from app.services.catvton.mask import build_agnostic_mask
        from app.services.catvton.pose_estimator import estimate_pose
        from app.services.catvton.pose_guide import guide_mask_with_pose
        from app.services.catvton.preprocess import (
            ensure_triplet,
            prepare_garment,
            prepare_person,
            restore_original_size,
            target_size,
        )
        from app.services.image_fetch import resolve_image
        from app.services.object_store import upload_result_png

        person = resolve_image(
            image_url=person_image_url,
            image_base64=person_image_base64,
            label="personImage",
        )
        garment = resolve_image(
            image_url=garment_image_url,
            image_base64=garment_image_base64,
            label="garmentImage",
        )
        if person is None:
            raise VtonInferenceError(
                "personImageUrl veya personImageBase64 zorunlu (gercek CatVTON).",
                code="MISSING_PERSON",
            )
        if garment is None:
            raise VtonInferenceError(
                "garmentImageUrl veya garmentImageBase64 zorunlu (gercek CatVTON).",
                code="MISSING_GARMENT",
            )

        # KRITIK: SCHP/pose/UNet oncesi 768x1024 — aksi halde MPS ~18GiB buffer ister
        size = target_size()
        person, original_wh = prepare_person(person, size)
        garment = prepare_garment(garment, size)
        logger.info(
            "CatVTON preprocess: original=%sx%s → inference=%sx%s",
            original_wh[0],
            original_wh[1],
            size[0],
            size[1],
        )

        mask, mask_source, protect_mask = build_agnostic_mask(person, cloth_type=cloth_type)

        # Pose guiding — basarisizsa SCHP maskesi aynen kalir
        pose = estimate_pose(person)
        mask, pose_source = guide_mask_with_pose(
            mask,
            pose,
            cloth_type=cloth_type if cloth_type in ("upper", "lower", "overall") else "upper",
            protect_mask=protect_mask,
        )
        person, garment, mask = ensure_triplet(person, garment, mask, size)

        generator = None
        try:
            if self._device and self._device != "cpu":
                generator = torch.Generator(device=self._device).manual_seed(settings.seed)
            else:
                generator = torch.Generator().manual_seed(settings.seed)

            results = self._pipeline(
                image=person,
                condition_image=garment,
                mask=mask,
                num_inference_steps=settings.num_inference_steps,
                guidance_scale=settings.guidance_scale,
                height=size[1],
                width=size[0],
                generator=generator,
            )
            result_image = restore_original_size(results[0], original_wh)
            stored = output_store.save(job_id, result_image)
            uploaded = upload_result_png(job_id, result_image)
            result_uri = uploaded.object_url if uploaded else stored.result_image_uri

            # MPS/CUDA bellek temizligi
            if self._device == "mps":
                torch.mps.empty_cache()
            elif self._device == "cuda":
                torch.cuda.empty_cache()

            return {
                "resultImageUri": result_uri,
                "resultImageBase64": stored.result_image_base64,
                "filePath": stored.file_path,
                "model": settings.attn_model_id,
                "device": self._device,
                "maskSource": mask_source,
                "poseSource": pose_source,
                "clothType": cloth_type,
                "inferenceSteps": settings.num_inference_steps,
                "guidanceScale": settings.guidance_scale,
                "personProvided": True,
                "garmentProvided": True,
            }
        except VtonInferenceError:
            raise
        except Exception as exc:  # noqa: BLE001
            logger.exception("CatVTON inference hatasi jobId=%s", job_id)
            raise classify_exception(exc) from exc


catvton_loader = CatVtonModelLoader()
