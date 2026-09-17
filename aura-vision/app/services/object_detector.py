"""YOLOv8 tabanli nesne tespit servisi.

Model agirligi ilk kullanimda `settings.model_dir` icine otomatik indirilir;
manuel indirme gerekmez. Yukleme thread-safe ve tek seferliktir (lazy singleton),
boylece her istek modeli yeniden diske gitmeden kullanir.
"""

import logging
import os
import threading
from pathlib import Path
from typing import Any, List, Optional

from PIL import Image

from app.core.config import settings
from app.core.device import resolve_device
from app.core.exceptions import ModelUnavailableError
from app.schemas.vision import BoundingBox, DetectedItem

logger = logging.getLogger(__name__)

# Geriye donuk uyumluluk: bu hata eskiden burada tanimliydi.
__all__ = ["ObjectDetector", "ModelUnavailableError", "object_detector"]


class ObjectDetector:
    """YOLOv8 modelini sarmalar ve tespitleri DTO'ya cevirir."""

    def __init__(self) -> None:
        self._model: Optional[Any] = None
        self._lock = threading.Lock()

    @property
    def is_loaded(self) -> bool:
        return self._model is not None

    @property
    def model_name(self) -> str:
        return settings.yolo_model_name

    @property
    def device(self) -> str:
        """Modelin gercekten calistigi cihaz (MPS varsa otomatik secilir)."""
        return resolve_device(settings.yolo_device, purpose="YOLO")

    def load(self) -> Any:
        """Modeli yukler. Agirlik diskte yoksa ultralytics otomatik indirir."""
        if self._model is not None:
            return self._model

        with self._lock:
            # Baska bir thread bekleme sirasinda yuklemis olabilir.
            if self._model is not None:
                return self._model

            weights_path = settings.yolo_weights_path
            weights_path.parent.mkdir(parents=True, exist_ok=True)

            # Ultralytics kendi ayar dosyalarini da proje icinde tutsun.
            # Dizin onceden yaratilmazsa ultralytics "not writable" deyip /tmp'ye duser.
            config_dir = settings.model_dir / ".ultralytics"
            config_dir.mkdir(parents=True, exist_ok=True)
            os.environ.setdefault("YOLO_CONFIG_DIR", str(config_dir))

            try:
                # torch importu agirdir; sadece gercekten ihtiyac duyulunca yapiliyor.
                from ultralytics import YOLO

                if not weights_path.exists():
                    logger.info("Model agirligi bulunamadi, indiriliyor: %s", weights_path)
                    self._download_weights(weights_path)

                model = YOLO(str(weights_path))
                model.to(self.device)
            except ModelUnavailableError:
                raise
            except Exception as exc:  # indirme/CUDA/bozuk dosya gibi tum hatalar
                raise ModelUnavailableError(
                    "YOLO modeli yuklenemedi: {0}".format(exc)
                ) from exc

            self._model = model
            logger.info("YOLO modeli hazir: %s (device=%s)", weights_path.name, self.device)
            return self._model

    @staticmethod
    def _download_weights(weights_path: Path) -> None:
        """Agirligi ultralytics release deposundan indirip hedef yola tasir."""
        from ultralytics.utils.downloads import attempt_download_asset

        downloaded = Path(str(attempt_download_asset(weights_path.name)))
        if not downloaded.exists():
            raise ModelUnavailableError(
                "Model agirligi indirilemedi: {0}".format(weights_path.name)
            )
        if downloaded.resolve() != weights_path.resolve():
            downloaded.replace(weights_path)

    def detect(self, image: Image.Image) -> List[DetectedItem]:
        """Verilen gorselde nesne tespiti yapar, guven skoruna gore siralar."""
        model = self.load()

        # Ultralytics numpy'a cevirirken 3 kanal bekler; RGBA/gri gorseller normalize edilir.
        rgb_image = image if image.mode == "RGB" else image.convert("RGB")

        results = model.predict(
            source=rgb_image,
            conf=settings.yolo_confidence,
            iou=settings.yolo_iou,
            max_det=settings.yolo_max_detections,
            device=self.device,
            verbose=False,
        )

        detections: List[DetectedItem] = []
        for result in results:
            class_names = result.names or {}
            for box in result.boxes:
                class_id = int(box.cls.item())
                x1, y1, x2, y2 = (round(float(value), 2) for value in box.xyxy[0].tolist())
                detections.append(
                    DetectedItem(
                        label=class_names.get(class_id, "unknown"),
                        class_id=class_id,
                        confidence=round(float(box.conf.item()), 4),
                        bounding_box=BoundingBox(
                            x1=x1,
                            y1=y1,
                            x2=x2,
                            y2=y2,
                            width=round(x2 - x1, 2),
                            height=round(y2 - y1, 2),
                        ),
                    )
                )

        detections.sort(key=lambda item: item.confidence, reverse=True)
        return detections


object_detector = ObjectDetector()
