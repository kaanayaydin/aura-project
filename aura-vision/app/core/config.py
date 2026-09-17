"""Servis genelindeki ayarlar. Degerler ortam degiskenlerinden okunur."""

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple

# aura-vision/ kok dizini
BASE_DIR = Path(__file__).resolve().parents[2]


def _env_int(key: str, default: int) -> int:
    """Ortam degiskenini int'e cevirir, hatali deger varsa default'a doner."""
    try:
        return int(os.getenv(key, default))
    except (TypeError, ValueError):
        return default


def _env_float(key: str, default: float) -> float:
    try:
        return float(os.getenv(key, default))
    except (TypeError, ValueError):
        return default


def _env_optional_int(key: str) -> Optional[int]:
    """Tanimlanmamis veya gecersiz degerler icin None doner."""
    raw = os.getenv(key)
    if raw is None or not raw.strip():
        return None
    try:
        return int(raw)
    except ValueError:
        return None


def _env_bool(key: str, default: bool) -> bool:
    raw = os.getenv(key)
    if raw is None:
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


@dataclass(frozen=True)
class Settings:
    project_name: str = "Aura Vision Service"
    version: str = "0.20.1"
    api_v1_prefix: str = "/api/v1"

    # Yuklenebilecek maksimum gorsel boyutu (MB)
    max_upload_size_mb: int = _env_int("AURA_MAX_UPLOAD_SIZE_MB", 15)

    # Pillow'un format isimleri bazinda kabul edilen gorsel tipleri
    supported_formats: Tuple[str, ...] = ("JPEG", "PNG", "WEBP", "BMP", "TIFF", "HEIF")

    # Flutter uygulamasi ve Spring Boot backend'i lokalde serbestce baglanabilsin
    cors_origins: Tuple[str, ...] = ("*",)

    # --- YOLO ayarlari ---
    # Model agirliklari bu dizine inecek (.gitignore'da haric tutuldu)
    model_dir: Path = Path(os.getenv("AURA_MODEL_DIR", str(BASE_DIR / "models")))
    yolo_model_name: str = os.getenv("AURA_YOLO_MODEL", "yolov8n.pt")
    yolo_confidence: float = _env_float("AURA_YOLO_CONFIDENCE", 0.35)
    yolo_iou: float = _env_float("AURA_YOLO_IOU", 0.45)
    yolo_max_detections: int = _env_int("AURA_YOLO_MAX_DETECTIONS", 20)
    # "auto" = MPS > CUDA > CPU siralamasiyla en hizli mevcut cihaz secilir.
    # Elle sabitlemek icin: "cpu" | "mps" | "cuda"
    yolo_device: str = os.getenv("AURA_YOLO_DEVICE", "auto")
    # Aciksa model uygulama acilisinda yuklenir; ilk istek beklemez.
    preload_model: bool = _env_bool("AURA_PRELOAD_MODEL", True)

    # --- SAM (segmentasyon) ayarlari ---
    sam_model_id: str = os.getenv("AURA_SAM_MODEL", "facebook/sam-vit-base")
    sam_device: str = os.getenv("AURA_SAM_DEVICE", "auto")
    segmentation_enabled: bool = _env_bool("AURA_SEGMENTATION_ENABLED", True)
    # Maskeyi kirparken kutunun cevresine eklenen pay (piksel)
    segmentation_padding: int = _env_int("AURA_SEGMENTATION_PADDING", 4)

    # --- CLIP (anlamsal etiketleme) ayarlari ---
    clip_model_id: str = os.getenv("AURA_CLIP_MODEL", "openai/clip-vit-base-patch32")
    clip_device: str = os.getenv("AURA_CLIP_DEVICE", "auto")
    classification_enabled: bool = _env_bool("AURA_CLASSIFICATION_ENABLED", True)
    # CLIP RGBA'da zorlanir; saydam alanlar bu renkle doldurulur ("white" | "black")
    clip_background: str = os.getenv("AURA_CLIP_BACKGROUND", "white")
    # Bu esigin altindaki top-1 sonuclar guvenilmez sayilip bos dondurulur
    clip_min_confidence: float = _env_float("AURA_CLIP_MIN_CONFIDENCE", 0.0)
    # Anlamsal etiketleme icin aday kategoriler
    candidate_labels: Tuple[str, ...] = (
        "t-shirt",
        "shirt",
        "pants",
        "jacket",
        "dress",
        "sneakers",
        "perfume bottle",
        "watch",
        "glasses",
    )
    # CLIP metin kulesi ciplak kelime yerine cumle kalibiyla daha isabetli calisir
    clip_prompt_template: str = os.getenv("AURA_CLIP_PROMPT", "a photo of a {label}")

    # --- Java backend entegrasyonu (opsiyonel) ---
    # Kapali oldugunda analiz akisi backend'e hic dokunmaz.
    backend_sync_enabled: bool = _env_bool("AURA_BACKEND_SYNC_ENABLED", False)
    backend_base_url: str = os.getenv("AURA_BACKEND_URL", "http://127.0.0.1:8080")
    backend_timeout_seconds: float = _env_float("AURA_BACKEND_TIMEOUT", 5.0)
    # Bos birakilirsa backend varsayilan (demo) kullaniciyi kullanir
    backend_user_id: Optional[int] = _env_optional_int("AURA_BACKEND_USER_ID")
    # Bu esigin altindaki kategoriler dolaba yazilmaz (yanlis kayit birikmesin)
    backend_min_confidence: float = _env_float("AURA_BACKEND_MIN_CONFIDENCE", 0.30)
    # Acikken yazma islemi arka plana alinir ve analiz cevabi beklemez.
    # Kapatildiginda istek icinde senkron yazilir (test/hata ayiklama icin).
    backend_sync_background: bool = _env_bool("AURA_BACKEND_SYNC_BACKGROUND", True)

    # --- Garment Studio Normalizer (v0.20.1) ---
    studio_aspect: str = os.getenv("AURA_STUDIO_ASPECT", "3:4")  # 3:4 | 1:1
    studio_long_side: int = _env_int("AURA_STUDIO_LONG_SIDE", 1024)
    studio_background_hex: str = os.getenv("AURA_STUDIO_BG", "#F8F9FA")
    studio_margin_ratio: float = _env_float("AURA_STUDIO_MARGIN", 0.08)
    studio_drop_shadow: bool = _env_bool("AURA_STUDIO_DROP_SHADOW", True)
    studio_rembg_enabled: bool = _env_bool("AURA_STUDIO_REMBG", True)
    # true: anlamli alfa olsa bile once rembg dene (katalog kalitesi)
    studio_prefer_rembg: bool = _env_bool("AURA_STUDIO_PREFER_REMBG", True)

    @property
    def max_upload_size_bytes(self) -> int:
        return self.max_upload_size_mb * 1024 * 1024

    @property
    def yolo_weights_path(self) -> Path:
        """Model agirliginin beklenen tam yolu. Dosya yoksa ilk yuklemede indirilir."""
        return self.model_dir / self.yolo_model_name

    @property
    def hf_cache_dir(self) -> Path:
        """Hugging Face model onbellegi de proje icinde tutulur."""
        return self.model_dir / "huggingface"


settings = Settings()
