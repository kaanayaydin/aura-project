"""Gorsel analiz boru hatti.

Akis:
    1. metadata      -> Pillow ile teknik bilgiler
    2. detection     -> YOLOv8 ile nesne tespiti
    3. segmentation  -> SAM ile piksel kesimi (saydam cutout)
    4. classification-> CLIP ile anlamsal etiketleme (kategori)

Segmentasyon veya siniflandirma asamasi coktugunde boru hatti durmaz; o asama
atlanir ve hangi asamalarin calistigi `stages_completed` ile bildirilir.
"""

import logging
from io import BytesIO
from typing import Callable, List, Optional, Tuple

from PIL import Image, UnidentifiedImageError

from app.core.config import settings
from app.core.exceptions import ModelUnavailableError
from app.schemas.vision import (
    ImageAnalysisResult,
    ImageMetadata,
    SegmentationInfo,
)
from app.services.object_detector import ObjectDetector, object_detector
from app.services.segmenter import SegmentResult, Segmenter, segmenter
from app.services.style_classifier import StyleClassifier, style_classifier
from app.services.wardrobe_sync import (
    SyncEntry,
    WardrobeSyncClient,
    encode_cutout,
    wardrobe_sync_client,
)

logger = logging.getLogger(__name__)

STAGE_METADATA = "metadata"
STAGE_DETECTION = "detection"
STAGE_SEGMENTATION = "segmentation"
STAGE_CLASSIFICATION = "classification"
STAGE_BACKEND_SYNC = "backend_sync"
STAGE_BACKEND_SYNC_SCHEDULED = "backend_sync_scheduled"

# Yazma isini arka plana almak icin kullanilan zamanlayici imzasi:
# schedule(fn, *args) -> None  (FastAPI'de BackgroundTasks.add_task)
Scheduler = Callable[..., None]


class InvalidImageError(ValueError):
    """Gonderilen byte dizisi gecerli bir gorsel degil."""


class UnsupportedImageFormatError(ValueError):
    """Gorsel okunabildi ancak formati desteklenen listede yok."""


class ImageAnalyzer:
    def __init__(
        self,
        detector: Optional[ObjectDetector] = None,
        image_segmenter: Optional[Segmenter] = None,
        classifier: Optional[StyleClassifier] = None,
        sync_client: Optional[WardrobeSyncClient] = None,
    ) -> None:
        self._detector = detector or object_detector
        self._segmenter = image_segmenter or segmenter
        self._classifier = classifier or style_classifier
        self._sync_client = sync_client or wardrobe_sync_client

    def analyze(
        self,
        raw_bytes: bytes,
        file_name: str,
        content_type: Optional[str] = None,
        schedule: Optional[Scheduler] = None,
        bearer_token: Optional[str] = None,
    ) -> ImageAnalysisResult:
        """Ham byte'lardan metadata, tespit, kesim ve kategori uretir.

        `schedule` verilirse dolap yazma isi ona devredilir (FastAPI'de
        `BackgroundTasks.add_task`) ve cevap veritabani yazmasini beklemez.
        Verilmezse yazma istek icinde senkron yapilir.

        `bearer_token`: istemcinin JWT'si — sync aciksa demo token yerine bu kullanilir.
        """
        try:
            with Image.open(BytesIO(raw_bytes)) as image:
                image_format = (image.format or "UNKNOWN").upper()
                if image_format not in settings.supported_formats:
                    raise UnsupportedImageFormatError(
                        "Desteklenmeyen gorsel formati: {0}".format(image_format)
                    )

                metadata = self._build_metadata(
                    image=image,
                    image_format=image_format,
                    file_name=file_name,
                    content_type=content_type,
                    size_bytes=len(raw_bytes),
                )
                stages = [STAGE_METADATA]

                detected_items = self._detector.detect(image)
                stages.append(STAGE_DETECTION)

                cutouts = self._run_segmentation(image, detected_items, stages)
                detected_items = self._run_classification(detected_items, cutouts, stages)
                synced, queued = self._run_backend_sync(
                    detected_items,
                    cutouts,
                    stages,
                    schedule,
                    bearer_token=bearer_token,
                )
        except UnidentifiedImageError as exc:
            raise InvalidImageError("Dosya gecerli bir gorsel olarak okunamadi.") from exc

        return ImageAnalysisResult(
            metadata=metadata,
            detected_items=detected_items,
            pipeline_stage=stages[-1],
            stages_completed=stages,
            wardrobe_synced=synced,
            wardrobe_sync_queued=queued,
            detection_model=self._detector.model_name,
            segmentation_model=(
                self._segmenter.model_name if STAGE_SEGMENTATION in stages else None
            ),
            classification_model=(
                self._classifier.model_name if STAGE_CLASSIFICATION in stages else None
            ),
        )

    def _run_segmentation(
        self,
        image: Image.Image,
        detected_items: List,
        stages: List[str],
    ) -> List[Optional[SegmentResult]]:
        """SAM ile kesim yapar.

        SAM kapali veya yuklenemiyorsa duz bounding box kirpmasina duser; bu
        durumda `segmentation` asamasi tamamlanmis sayilmaz ama CLIP yine de
        siniflandirabilecegi bir gorsel alir.
        """
        if not detected_items:
            return []

        boxes = [
            (
                item.bounding_box.x1,
                item.bounding_box.y1,
                item.bounding_box.x2,
                item.bounding_box.y2,
            )
            for item in detected_items
        ]

        if not settings.segmentation_enabled:
            return [self._segmenter.bbox_crop(image, box) for box in boxes]

        try:
            results = list(self._segmenter.segment(image, boxes))
        except ModelUnavailableError as exc:
            logger.warning("Segmentasyon asamasi atlandi, bbox kirpmasi kullanilacak: %s", exc)
            return [self._segmenter.bbox_crop(image, box) for box in boxes]

        # Tum sonuclar yedek kirpmaya dustuyse asama gercekten calismis sayilmaz.
        if any(result.source == "sam" for result in results):
            stages.append(STAGE_SEGMENTATION)
        return results

    def _run_classification(
        self,
        detected_items: List,
        cutouts: List[Optional[SegmentResult]],
        stages: List[str],
    ) -> List:
        """Kesimleri CLIP'e verip her nesneye kategori yazar."""
        if not detected_items:
            return detected_items

        if not settings.classification_enabled:
            # Siniflandirma kapali olsa da kesim bilgisi cevaba yazilmali.
            return self._attach_segmentation(detected_items, cutouts)

        # Kesim yoksa (segmentasyon kapali/hatali) CLIP'e bounding box kirpmasi gider.
        indexed_images: List[Tuple[int, Image.Image]] = []
        for index, cutout in enumerate(cutouts):
            if cutout is not None:
                indexed_images.append((index, cutout.cutout))

        if not indexed_images:
            return self._attach_segmentation(detected_items, cutouts)

        try:
            predictions = self._classifier.classify([image for _, image in indexed_images])
        except ModelUnavailableError as exc:
            logger.warning("Siniflandirma asamasi atlandi: %s", exc)
            return self._attach_segmentation(detected_items, cutouts)

        stages.append(STAGE_CLASSIFICATION)

        categories = {}
        for (index, _), prediction in zip(indexed_images, predictions):
            categories[index] = prediction

        enriched = []
        for index, item in enumerate(detected_items):
            prediction = categories.get(index)
            cutout = cutouts[index] if index < len(cutouts) else None
            enriched.append(
                item.model_copy(
                    update={
                        "segmentation": self._segmentation_info(cutout),
                        "category": prediction.label if prediction else None,
                        "category_confidence": prediction.confidence if prediction else None,
                        "cutout_image_base64": self._cutout_b64(cutout),
                    }
                )
            )
        return enriched

    def _run_backend_sync(
        self,
        detected_items: List,
        cutouts: List[Optional[SegmentResult]],
        stages: List[str],
        schedule: Optional[Scheduler] = None,
        bearer_token: Optional[str] = None,
    ) -> Tuple[Optional[int], Optional[int]]:
        """Kategorilenen kesimleri Java backend'indeki dolaba yazar.

        Opsiyoneldir ve varsayilan olarak kapalidir. Backend erisilemez olsa
        bile analiz cevabi etkilenmez. JWT yoksa demo token ile yazmaz
        (yanlis kullaniciya kayit birikmesin); kesimler yine cevaba konur.
        """
        if not self._sync_client.enabled:
            return None, None

        if not bearer_token:
            logger.warning(
                "Backend sync ATLANDI: Authorization Bearer yok. "
                "Kesimler (cutout_image_base64) istemciye donduruldu; "
                "dolaba yazmayi mobil JWT ile yapmali. "
                "(Eski /auth/token demo yolu artik kullanilmiyor.)"
            )
            return None, None

        entries: List[SyncEntry] = []
        for index, item in enumerate(detected_items):
            cutout = cutouts[index] if index < len(cutouts) else None
            if cutout is None or not item.category:
                continue
            confidence = item.category_confidence
            if confidence is not None and confidence < settings.backend_min_confidence:
                continue
            entries.append(
                SyncEntry(
                    category=item.category,
                    category_confidence=confidence,
                    image_base64=encode_cutout(cutout.cutout),
                )
            )

        if not entries:
            return 0, 0

        if schedule is not None and settings.backend_sync_background:
            schedule(self._sync_client.sync, entries, bearer_token)
            stages.append(STAGE_BACKEND_SYNC_SCHEDULED)
            return None, len(entries)

        outcome = self._sync_client.sync(entries, bearer_token=bearer_token)
        if outcome.succeeded > 0:
            stages.append(STAGE_BACKEND_SYNC)
        return outcome.succeeded, 0

    def _attach_segmentation(
        self, detected_items: List, cutouts: List[Optional[SegmentResult]]
    ) -> List:
        """Siniflandirma yapilamadiginda sadece kesim bilgisini isler."""
        return [
            item.model_copy(
                update={
                    "segmentation": self._segmentation_info(
                        cutouts[index] if index < len(cutouts) else None
                    ),
                    "cutout_image_base64": self._cutout_b64(
                        cutouts[index] if index < len(cutouts) else None
                    ),
                }
            )
            for index, item in enumerate(detected_items)
        ]

    @staticmethod
    def _cutout_b64(cutout: Optional[SegmentResult]) -> Optional[str]:
        if cutout is None:
            return None
        return encode_cutout(cutout.cutout)

    @staticmethod
    def _segmentation_info(cutout: Optional[SegmentResult]) -> Optional[SegmentationInfo]:
        if cutout is None:
            return None
        return SegmentationInfo(
            source=cutout.source,
            mask_area_px=cutout.mask_area_px,
            box_coverage=cutout.box_coverage,
        )

    def _build_metadata(
        self,
        image: Image.Image,
        image_format: str,
        file_name: str,
        content_type: Optional[str],
        size_bytes: int,
    ) -> ImageMetadata:
        width, height = image.size
        return ImageMetadata(
            file_name=file_name,
            content_type=content_type,
            image_format=image_format,
            color_mode=image.mode,
            width=width,
            height=height,
            megapixels=round((width * height) / 1_000_000, 2),
            aspect_ratio=round(width / height, 3) if height else 0.0,
            orientation=self._resolve_orientation(width, height),
            size_bytes=size_bytes,
            size_kb=round(size_bytes / 1024, 2),
            dpi=self._extract_dpi(image),
            has_alpha=self._has_alpha(image),
        )

    @staticmethod
    def _resolve_orientation(width: int, height: int) -> str:
        if width == height:
            return "square"
        return "landscape" if width > height else "portrait"

    @staticmethod
    def _has_alpha(image: Image.Image) -> bool:
        return image.mode in ("RGBA", "LA", "PA") or "transparency" in image.info

    @staticmethod
    def _extract_dpi(image: Image.Image) -> Optional[Tuple[float, float]]:
        raw_dpi = image.info.get("dpi")
        if not raw_dpi or len(raw_dpi) < 2:
            return None
        try:
            # Pillow bazi formatlarda IFDRational dondurur, float'a normalize ediyoruz.
            return (float(raw_dpi[0]), float(raw_dpi[1]))
        except (TypeError, ValueError, ZeroDivisionError):
            return None


image_analyzer = ImageAnalyzer()
