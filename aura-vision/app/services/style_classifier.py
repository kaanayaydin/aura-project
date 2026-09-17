"""CLIP tabanli anlamsal etiketleme servisi.

YOLO'nun COCO sinif seti moda urunlerini icermez ("gomlek", "parfum sisesi"
gibi siniflar yok). Bu servis SAM'den cikan saydam kesimi CLIP'e verip aday
etiketler arasindan en olasi kategoriyi secer (zero-shot siniflandirma).

Model Hugging Face uzerinden ilk kullanimda otomatik iner; yukleme thread-safe
ve tek seferliktir.
"""

import logging
import threading
from dataclasses import dataclass
from typing import Any, List, Optional, Sequence, Tuple

from PIL import Image, ImageColor

from app.core.config import settings
from app.core.device import resolve_device
from app.core.exceptions import ModelUnavailableError

logger = logging.getLogger(__name__)

_FALLBACK_BACKGROUND = "white"


@dataclass
class CategoryPrediction:
    """CLIP'in en yuksek olasilikli (top-1) tahmini."""

    label: str
    confidence: float


class StyleClassifier:
    """CLIP modelini sarmalar ve kesimleri aday etiketlere gore siniflandirir."""

    def __init__(self) -> None:
        self._model: Optional[Any] = None
        self._processor: Optional[Any] = None
        self._lock = threading.Lock()

    @property
    def is_loaded(self) -> bool:
        return self._model is not None

    @property
    def model_name(self) -> str:
        return settings.clip_model_id

    @property
    def labels(self) -> Tuple[str, ...]:
        return settings.candidate_labels

    @property
    def device(self) -> str:
        """Modelin gercekten calistigi cihaz (MPS varsa otomatik secilir)."""
        return resolve_device(settings.clip_device, purpose="CLIP")

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
                from transformers import CLIPModel, CLIPProcessor

                processor = CLIPProcessor.from_pretrained(
                    settings.clip_model_id, cache_dir=str(cache_dir)
                )
                model = CLIPModel.from_pretrained(
                    settings.clip_model_id, cache_dir=str(cache_dir)
                )
                model.to(self.device)
                model.eval()
            except Exception as exc:
                raise ModelUnavailableError(
                    "CLIP modeli yuklenemedi ({0}): {1}".format(settings.clip_model_id, exc)
                ) from exc

            self._model, self._processor = model, processor
            logger.info("CLIP modeli hazir: %s (device=%s)", settings.clip_model_id, self.device)
            return self._model, self._processor

    def classify(self, images: Sequence[Image.Image]) -> List[Optional[CategoryPrediction]]:
        """Verilen kesimleri tek batch'te siniflandirir.

        Donen liste girdi sirasini korur; siniflandirilamayan ogeler icin
        `None` doner (bos kesim, esik alti guven vb.).
        """
        if not images:
            return []

        # Sifir boyutlu kesimler CLIP'i hata verdirir; onceden ayiklaniyor.
        usable: List[Tuple[int, Image.Image]] = []
        for index, image in enumerate(images):
            if image.width > 0 and image.height > 0:
                usable.append((index, self._flatten(image)))

        predictions: List[Optional[CategoryPrediction]] = [None] * len(images)
        if not usable:
            return predictions

        for index, prediction in zip(
            (item[0] for item in usable),
            self._predict([item[1] for item in usable]),
        ):
            predictions[index] = prediction
        return predictions

    def _predict(self, images: List[Image.Image]) -> List[Optional[CategoryPrediction]]:
        import torch

        model, processor = self.load()
        prompts = [
            settings.clip_prompt_template.format(label=label)
            for label in settings.candidate_labels
        ]

        inputs = processor(
            text=prompts,
            images=images,
            return_tensors="pt",
            padding=True,
        )
        # MPS float64 desteklemez; processor ciktisi float64 ise indirgeniyor.
        inputs = {
            key: (value.to(torch.float32) if value.dtype == torch.float64 else value).to(
                self.device
            )
            for key, value in inputs.items()
        }

        with torch.no_grad():
            outputs = model(**inputs)

        # logits_per_image sekli: (gorsel_sayisi, etiket_sayisi)
        probabilities = outputs.logits_per_image.softmax(dim=1)
        best_scores, best_indices = probabilities.max(dim=1)

        predictions: List[Optional[CategoryPrediction]] = []
        for score, label_index in zip(best_scores.tolist(), best_indices.tolist()):
            if score < settings.clip_min_confidence:
                predictions.append(None)
                continue
            predictions.append(
                CategoryPrediction(
                    label=settings.candidate_labels[label_index],
                    confidence=round(float(score), 4),
                )
            )
        return predictions

    @staticmethod
    def _flatten(image: Image.Image) -> Image.Image:
        """Saydam kesimi duz zeminli RGB'ye cevirir.

        CLIP alfa kanalini yok sayar; saydam bolgeler siyah lekeye donusup
        tahmini bozabilir. Bu yuzden arka plan duz renkle doldurulur.
        """
        if image.mode != "RGBA":
            return image.convert("RGB")

        try:
            background_color = ImageColor.getrgb(settings.clip_background)
        except ValueError:
            logger.warning(
                "Gecersiz CLIP arka plan rengi '%s', '%s' kullaniliyor.",
                settings.clip_background,
                _FALLBACK_BACKGROUND,
            )
            background_color = ImageColor.getrgb(_FALLBACK_BACKGROUND)

        background = Image.new("RGB", image.size, background_color)
        background.paste(image, mask=image.split()[-1])
        return background


style_classifier = StyleClassifier()
