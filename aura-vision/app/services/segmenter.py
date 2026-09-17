"""SAM (Segment Anything) tabanli piksel kesim servisi.

YOLO'dan gelen bounding box'lar SAM'e "box prompt" olarak verilir; model her
kutu icin bir maske uretir. Maske alfa kanali olarak uygulanip nesne kutusuna
kirpilir, boylece arka plani saydam bir kesim (cutout) elde edilir.

Model Hugging Face uzerinden ilk kullanimda otomatik iner (`models/huggingface`).
Yukleme thread-safe ve tek seferliktir.
"""

import logging
import threading
from dataclasses import dataclass
from typing import Any, List, Optional, Sequence, Tuple

from PIL import Image

from app.core.config import settings
from app.core.device import resolve_device
from app.core.exceptions import ModelUnavailableError

logger = logging.getLogger(__name__)

BoxType = Sequence[float]


@dataclass
class SegmentResult:
    """Tek bir nesne icin kesim sonucu."""

    cutout: Image.Image  # RGBA, nesne kutusuna kirpilmis
    mask_area_px: int
    box_coverage: float  # maskenin kutu alanina orani (0-1)
    source: str  # "sam" veya "bbox_crop" (SAM basarisiz oldugunda)


class Segmenter:
    """SAM modelini sarmalar ve kutu basina saydam kesim uretir."""

    def __init__(self) -> None:
        self._model: Optional[Any] = None
        self._processor: Optional[Any] = None
        self._lock = threading.Lock()

    @property
    def is_loaded(self) -> bool:
        return self._model is not None

    @property
    def model_name(self) -> str:
        return settings.sam_model_id

    @property
    def device(self) -> str:
        """Modelin gercekten calistigi cihaz (MPS varsa otomatik secilir)."""
        return resolve_device(settings.sam_device, purpose="SAM")

    def load(self) -> Tuple[Any, Any]:
        """Model ve processor'i yukler. Agirlik yoksa HF'den otomatik iner."""
        if self._model is not None and self._processor is not None:
            return self._model, self._processor

        with self._lock:
            if self._model is not None and self._processor is not None:
                return self._model, self._processor

            cache_dir = settings.hf_cache_dir
            cache_dir.mkdir(parents=True, exist_ok=True)

            try:
                # transformers/torch importu agirdir; ihtiyac aninda yapiliyor.
                from transformers import SamModel, SamProcessor

                processor = SamProcessor.from_pretrained(
                    settings.sam_model_id, cache_dir=str(cache_dir)
                )
                model = SamModel.from_pretrained(
                    settings.sam_model_id, cache_dir=str(cache_dir)
                )
                model.to(self.device)
                model.eval()
            except Exception as exc:
                raise ModelUnavailableError(
                    "SAM modeli yuklenemedi ({0}): {1}".format(settings.sam_model_id, exc)
                ) from exc

            self._model, self._processor = model, processor
            logger.info("SAM modeli hazir: %s (device=%s)", settings.sam_model_id, self.device)
            return self._model, self._processor

    def segment(self, image: Image.Image, boxes: Sequence[BoxType]) -> List[SegmentResult]:
        """Her kutu icin saydam kesim uretir.

        SAM calismazsa (model yuklenemez, bellek yetmez vb.) boru hatti
        durmaz: her nesne icin duz bounding box kirpmasina dusulur.
        """
        if not boxes:
            return []

        rgb_image = image if image.mode == "RGB" else image.convert("RGB")

        try:
            masks = self._predict_masks(rgb_image, boxes)
        except ModelUnavailableError:
            raise
        except Exception as exc:
            logger.warning("SAM cikarimi basarisiz, bbox kirpmasina dusuluyor: %s", exc)
            return [self.bbox_crop(rgb_image, box) for box in boxes]

        results: List[SegmentResult] = []
        for index, box in enumerate(boxes):
            mask = masks[index] if index < len(masks) else None
            if mask is None or not mask.any():
                # SAM bu kutuda anlamli bir maske bulamadi.
                results.append(self.bbox_crop(rgb_image, box))
                continue
            results.append(self._build_cutout(rgb_image, box, mask))
        return results

    def _predict_masks(self, image: Image.Image, boxes: Sequence[BoxType]) -> List[Any]:
        """SAM'i tek cagrida tum kutular icin calistirir."""
        import torch

        model, processor = self.load()

        # input_boxes formati: (batch_size, box_sayisi, 4)
        input_boxes = [[[float(value) for value in box] for box in boxes]]
        inputs = processor(image, input_boxes=input_boxes, return_tensors="pt")

        # Boyut bilgileri modele girmez, sadece maskeleri geri olceklemek icin
        # kullanilir; CPU'da birakiliyor.
        original_sizes = inputs["original_sizes"]
        reshaped_sizes = inputs["reshaped_input_sizes"]

        model_inputs = {}
        for key in ("pixel_values", "input_boxes"):
            tensor = inputs[key]
            # Processor kutu koordinatlarini float64 uretir; MPS float64 desteklemez.
            if tensor.dtype == torch.float64:
                tensor = tensor.to(torch.float32)
            model_inputs[key] = tensor.to(self.device)

        with torch.no_grad():
            outputs = model(**model_inputs, multimask_output=False)

        # Not: reshaped_input_sizes zorunlu; maskeler 1024x1024'luk model uzayindan
        # orijinal gorsel boyutuna bu bilgiyle geri olceklenir.
        processed = processor.post_process_masks(
            outputs.pred_masks.cpu(),
            original_sizes,
            reshaped_sizes,
        )
        # processed[0] sekli: (box_sayisi, maske_sayisi, H, W)
        image_masks = processed[0]
        return [image_masks[index][0].numpy() for index in range(image_masks.shape[0])]

    def _build_cutout(
        self, image: Image.Image, box: BoxType, mask: Any
    ) -> SegmentResult:
        """Maskeyi alfa kanali olarak uygulayip nesne kutusuna kirpar."""
        import numpy as np

        alpha = Image.fromarray((np.asarray(mask) * 255).astype("uint8"), mode="L")
        rgba = image.convert("RGBA")
        rgba.putalpha(alpha)

        crop_box = self._padded_box(box, image.size)
        cutout = rgba.crop(crop_box)

        mask_area_px = int(np.asarray(mask).sum())
        box_area = max((crop_box[2] - crop_box[0]) * (crop_box[3] - crop_box[1]), 1)
        return SegmentResult(
            cutout=cutout,
            mask_area_px=mask_area_px,
            box_coverage=round(min(mask_area_px / box_area, 1.0), 4),
            source="sam",
        )

    def bbox_crop(self, image: Image.Image, box: BoxType) -> SegmentResult:
        """SAM devre disi/basarisiz oldugunda kullanilan yedek kirpma."""
        crop_box = self._padded_box(box, image.size)
        cutout = image.convert("RGBA").crop(crop_box)
        area = max((crop_box[2] - crop_box[0]) * (crop_box[3] - crop_box[1]), 1)
        return SegmentResult(
            cutout=cutout,
            mask_area_px=area,
            box_coverage=1.0,
            source="bbox_crop",
        )

    @staticmethod
    def _padded_box(box: BoxType, image_size: Tuple[int, int]) -> Tuple[int, int, int, int]:
        """Kutuyu paylarla genisletip gorsel sinirlarina kirpar."""
        width, height = image_size
        padding = settings.segmentation_padding
        x1, y1, x2, y2 = (float(value) for value in box)

        left = max(int(x1) - padding, 0)
        top = max(int(y1) - padding, 0)
        right = min(int(x2) + padding, width)
        bottom = min(int(y2) + padding, height)

        # Dejenere kutulari en az 1 piksel genislige zorla
        right = max(right, left + 1)
        bottom = max(bottom, top + 1)
        return left, top, right, bottom


segmenter = Segmenter()
