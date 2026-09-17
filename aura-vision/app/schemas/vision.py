"""Vision API'sinin request/response DTO'lari."""

from typing import List, Optional, Tuple

from pydantic import BaseModel, Field


class ImageMetadata(BaseModel):
    """Yuklenen gorselin ham teknik bilgileri."""

    file_name: str = Field(..., description="Istemcinin gonderdigi dosya adi")
    content_type: Optional[str] = Field(None, description="Istekteki MIME tipi")
    image_format: str = Field(..., description="Pillow'un tespit ettigi format (JPEG, PNG...)")
    color_mode: str = Field(..., description="Renk modu (RGB, RGBA, L...)")
    width: int = Field(..., description="Genislik (piksel)")
    height: int = Field(..., description="Yukseklik (piksel)")
    megapixels: float = Field(..., description="Toplam cozunurluk (megapiksel)")
    aspect_ratio: float = Field(..., description="Genislik / yukseklik orani")
    orientation: str = Field(..., description="portrait, landscape veya square")
    size_bytes: int = Field(..., description="Dosya boyutu (byte)")
    size_kb: float = Field(..., description="Dosya boyutu (KB)")
    dpi: Optional[Tuple[float, float]] = Field(None, description="Varsa gomulu DPI bilgisi")
    has_alpha: bool = Field(..., description="Saydamlik kanali var mi")


class BoundingBox(BaseModel):
    """Tespitin piksel bazli sinir kutusu (sol-ust / sag-alt kosegen)."""

    x1: float = Field(..., description="Sol kenar (piksel)")
    y1: float = Field(..., description="Ust kenar (piksel)")
    x2: float = Field(..., description="Sag kenar (piksel)")
    y2: float = Field(..., description="Alt kenar (piksel)")
    width: float = Field(..., description="Kutu genisligi (piksel)")
    height: float = Field(..., description="Kutu yuksekligi (piksel)")


class SegmentationInfo(BaseModel):
    """SAM kesiminin ozeti."""

    source: str = Field(..., description="'sam' veya SAM basarisizsa 'bbox_crop'")
    mask_area_px: int = Field(..., description="Maskenin kapladigi piksel sayisi")
    box_coverage: float = Field(..., description="Maskenin kutu alanina orani (0-1)")


class DetectedItem(BaseModel):
    """Tespit edilen tek bir nesne ve uzerinde yapilan tum analizler."""

    label: str = Field(..., description="YOLO sinif adi (orn. person, tie, handbag)")
    class_id: int = Field(..., description="Modelin sinif indeksi")
    confidence: float = Field(..., description="YOLO guven skoru (0-1)")
    bounding_box: BoundingBox
    segmentation: Optional[SegmentationInfo] = Field(
        None, description="SAM kesim bilgisi; segmentasyon kapaliysa null"
    )
    category: Optional[str] = Field(
        None, description="CLIP'in sectigi aday etiket (orn. t-shirt, perfume bottle)"
    )
    category_confidence: Optional[float] = Field(
        None, description="CLIP top-1 guven skoru (0-1)"
    )
    cutout_image_base64: Optional[str] = Field(
        None,
        description=(
            "RGBA PNG kesim (base64). Mobil istemci bunu Bearer ile "
            "POST /api/v1/wardrobe/items'a yazar."
        ),
    )


class ImageAnalysisResult(BaseModel):
    """Servis katmaninin urettigi analiz sonucu."""

    metadata: ImageMetadata
    detected_items: List[DetectedItem] = Field(default_factory=list)
    pipeline_stage: str = Field(..., description="Boru hattinin ulastigi son asama")
    stages_completed: List[str] = Field(
        default_factory=list,
        description="Bu istekte gercekten calisan asamalar",
    )
    detection_model: Optional[str] = Field(None, description="Tespitte kullanilan model")
    segmentation_model: Optional[str] = Field(None, description="Kesimde kullanilan model")
    classification_model: Optional[str] = Field(
        None, description="Anlamsal etiketlemede kullanilan model"
    )
    wardrobe_synced: Optional[int] = Field(
        None,
        description=(
            "Istek icinde dolaba yazilan kayit sayisi. Entegrasyon kapaliysa veya "
            "yazma arka plana alindiysa null."
        ),
    )
    wardrobe_sync_queued: Optional[int] = Field(
        None,
        description="Arka plana alinan yazma isi sayisi; entegrasyon kapaliysa null",
    )


class AnalyzeResponse(BaseModel):
    """/vision/analyze cevabi."""

    status: str = Field("success", description="Islem durumu")
    message: str = Field(..., description="Insan tarafindan okunabilir ozet")
    pipeline_stage: str = Field(..., description="Boru hattinin ulastigi son asama")
    stages_completed: List[str] = Field(
        default_factory=list,
        description="Bu istekte gercekten calisan asamalar",
    )
    detection_model: Optional[str] = Field(None, description="Tespitte kullanilan model")
    segmentation_model: Optional[str] = Field(None, description="Kesimde kullanilan model")
    classification_model: Optional[str] = Field(
        None, description="Anlamsal etiketlemede kullanilan model"
    )
    wardrobe_synced: Optional[int] = Field(
        None,
        description=(
            "Istek icinde dolaba yazilan kayit sayisi. Entegrasyon kapaliysa veya "
            "yazma arka plana alindiysa null."
        ),
    )
    wardrobe_sync_queued: Optional[int] = Field(
        None,
        description="Arka plana alinan yazma isi sayisi; entegrasyon kapaliysa null",
    )
    metadata: ImageMetadata
    detected_items: List[DetectedItem] = Field(
        default_factory=list,
        description="Tespit edilen nesneler, guven skoruna gore azalan sirada",
    )


class NormalizeGarmentResponse(BaseModel):
    """POST /vision/normalize-garment cevabi — stüdyo standardi PNG."""

    status: str = Field("success")
    message: str = Field(..., description="Ozet")
    width: int
    height: int
    aspect: str = Field(..., description="3:4 veya 1:1")
    cutout_source: str = Field(..., description="alpha | rembg | chroma")
    content_type: str = Field("image/png")
    image_base64: str = Field(..., description="Normalize PNG (base64, data URI yok)")
    image_bytes: int = Field(..., description="PNG bayt uzunlugu")
