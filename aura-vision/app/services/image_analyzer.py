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
import uuid
from io import BytesIO
from typing import Callable, List, Optional, Tuple

from PIL import Image, UnidentifiedImageError

from app.core.config import settings
from app.core.exceptions import ModelUnavailableError
from app.schemas.vision import (
    BoundingBox,
    DetectedItem,
    ImageAnalysisResult,
    ImageMetadata,
    SegmentationInfo,
)
from app.services.object_detector import ObjectDetector, object_detector
from app.services.segmenter import SegmentResult, Segmenter, segmenter
from app.services.style_classifier import ClassificationResult, StyleClassifier, style_classifier
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
STAGE_FULLFRAME_FALLBACK = "fullframe_fallback"

USER_MSG_BELOW_THRESHOLD = (
    "Bu fotoğrafta kıyafeti net olarak tanıyamadık, lütfen daha düz bir açıdan, gölgesiz çekin"
)
USER_MSG_CUTOUT_FAILED = (
    "Arka planı ayırt edemedik, lütfen daha sade bir zeminde çekin"
)
USER_MSG_NO_DETECTION = USER_MSG_BELOW_THRESHOLD

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
        debug: bool = False,
        job_id: Optional[str] = None,
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

                job = (job_id or uuid.uuid4().hex[:12]).strip()
                rejected_reason: Optional[str] = None
                rejected_early: Optional[str] = None
                detected_items = self._detector.detect(image)
                stages.append(STAGE_DETECTION)
                yolo_count = len(detected_items)
                fallback_used = False
                cutout_source = "none"
                clip_row: Optional[ClassificationResult] = None
                fallback_cut: Optional[Image.Image] = None
                cutouts: List[Optional[SegmentResult]] = []

                if not detected_items:
                    logger.warning(
                        "YOLO 0 tespit — tam kare garment fallback (COCO kiyafet sinifi yok)"
                    )
                    fb_item, fb_cut, fb_src, fb_reason, clip_row = self._fullframe_garment_fallback(
                        image
                    )
                    fallback_used = True
                    cutout_source = fb_src
                    fallback_cut = fb_cut
                    if fb_item is not None:
                        detected_items = [fb_item]
                        cutouts = [
                            SegmentResult(
                                cutout=fb_cut,
                                mask_area_px=self._opaque_px(fb_cut),
                                box_coverage=1.0,
                                source=fb_src,
                            )
                        ]
                        stages.append(STAGE_FULLFRAME_FALLBACK)
                        stages.append(STAGE_CLASSIFICATION)
                    else:
                        cutouts = []
                        rejected_early = fb_reason or "no_detection"
                else:
                    cutouts = self._run_segmentation(image, detected_items, stages)
                    detected_items = self._run_classification(detected_items, cutouts, stages)
                    if cutouts and cutouts[0] is not None:
                        cutout_source = cutouts[0].source
                        fallback_cut = cutouts[0].cutout
                    if detected_items:
                        # Ilk oge icin detayli skor (debug)
                        if fallback_cut is not None:
                            detailed = self._classifier.classify_detailed([fallback_cut])
                            clip_row = detailed[0] if detailed else None

                if not any(item.category for item in detected_items):
                    if rejected_early:
                        rejected_reason = rejected_early
                    elif clip_row and clip_row.rejected_reason:
                        rejected_reason = clip_row.rejected_reason
                    elif fallback_used and fallback_cut is None:
                        rejected_reason = "cutout_failed"
                    elif yolo_count == 0:
                        rejected_reason = "no_detection"
                    else:
                        rejected_reason = "below_threshold"

                if debug:
                    from app.services.category_debug import write_category_debug

                    pred = clip_row.prediction if clip_row else None
                    write_category_debug(
                        job_id=job,
                        cutout=fallback_cut,
                        all_scores=(clip_row.all_scores if clip_row else {}),
                        chosen_category=pred.label if pred else None,
                        confidence=pred.confidence if pred else None,
                        threshold=settings.backend_min_confidence,
                        rejected_reason=rejected_reason,
                        yolo_count=yolo_count,
                        fallback_used=fallback_used,
                        cutout_source=cutout_source,
                    )

                synced, queued = self._run_backend_sync(
                    detected_items,
                    cutouts,
                    stages,
                    schedule,
                    bearer_token=bearer_token,
                )
        except UnidentifiedImageError as exc:
            raise InvalidImageError("Dosya gecerli bir gorsel olarak okunamadi.") from exc

        categorized = any(item.category for item in detected_items)
        user_message = None
        if not categorized and rejected_reason:
            user_message = {
                "below_threshold": USER_MSG_BELOW_THRESHOLD,
                "cutout_failed": USER_MSG_CUTOUT_FAILED,
                "no_detection": USER_MSG_NO_DETECTION,
                "empty_image": USER_MSG_CUTOUT_FAILED,
                "empty_mask": USER_MSG_CUTOUT_FAILED,
            }.get(rejected_reason, USER_MSG_BELOW_THRESHOLD)

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
            job_id=job,
            rejected_reason=rejected_reason if not categorized else None,
            user_message=user_message,
            category_debug_dir=str(settings.studio_debug_dir) + "/" + job if debug else None,
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

    @staticmethod
    def _empty_or_unusable_mask_reason(cut: Image.Image) -> Optional[str]:
        """Bos / carsaf maske — CLIP'e gonderme, dolaba yazma."""
        from app.services.garment_studio import unusable_mask_reason

        return unusable_mask_reason(cut)

    @staticmethod
    def _opaque_px(cut: Image.Image) -> int:
        import numpy as np

        alpha = cut.split()[-1] if cut.mode == "RGBA" else None
        if alpha is None:
            return cut.size[0] * cut.size[1]
        return int((np.asarray(alpha) > 127).sum())

    def _fullframe_garment_fallback(
        self, image: Image.Image
    ) -> Tuple[
        Optional[DetectedItem],
        Optional[Image.Image],
        str,
        Optional[str],
        Optional[ClassificationResult],
    ]:
        """YOLO bosken rembg/chroma + CLIP — dolaba yazilacak tek parca.

        COCO'da tişört yok; masa/gölge sahnelerinde tespit 0 kalabiliyor.
        """
        from app.services.garment_normalizer import garment_normalizer
        from app.services.garment_studio import has_meaningful_alpha

        try:
            cut, src = garment_normalizer._cutout(image, prefer_rembg=True)
        except Exception:
            logger.exception("Full-frame cutout basarisiz")
            return None, None, "none", "cutout_failed", None

        if cut is None:
            return None, None, src, "cutout_failed", None
        empty_reason = self._empty_or_unusable_mask_reason(cut)
        if empty_reason:
            logger.warning("Full-frame fallback maske reddedildi: %s src=%s", empty_reason, src)
            return None, cut, src, empty_reason, None
        if not has_meaningful_alpha(cut):
            return None, cut, src, "cutout_failed", None

        detailed = self._classifier.classify_detailed([cut])
        row = detailed[0] if detailed else ClassificationResult(None, {}, "empty_image")
        pred = row.prediction
        if pred is None:
            return None, cut, src, row.rejected_reason or "below_threshold", row
        if pred.confidence < settings.backend_min_confidence:
            row = ClassificationResult(pred, row.all_scores, "below_threshold")
            return None, cut, src, "below_threshold", row

        w, h = image.size
        item = DetectedItem(
            label="full_frame",
            class_id=-1,
            confidence=0.0,
            bounding_box=BoundingBox(
                x1=0, y1=0, x2=float(w), y2=float(h), width=float(w), height=float(h)
            ),
            category=pred.label,
            category_confidence=pred.confidence,
            cutout_image_base64=encode_cutout(cut),
            segmentation=SegmentationInfo(
                source=src,
                mask_area_px=self._opaque_px(cut),
                box_coverage=1.0,
            ),
        )
        return item, cut, src, None, row

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
