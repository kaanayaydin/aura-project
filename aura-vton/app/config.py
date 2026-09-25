from __future__ import annotations

from pathlib import Path

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AURA_VTON_", env_file=".env", extra="ignore")

    port: int = 8001
    redis_url: str = "redis://127.0.0.1:6379/0"
    celery_queue: str = "aura-vton"

    # true: mock PNG (CI/test). false: gercek CatVTON (HF agirliklari).
    mock_model: bool = True
    mock_delay_seconds: float = 0.0

    # ~/.cache/aura-vton
    cache_dir: str = str(Path.home() / ".cache" / "aura-vton")
    output_dir: str = str(Path.home() / ".cache" / "aura-vton" / "outputs")
    public_base_url: str = "http://127.0.0.1:8001"

    # auto | mps | cuda | cpu
    device: str = "auto"
    # auto | fp16 | fp32 | bf16
    precision: str = "auto"

    base_model_id: str = "booksforcharlie/stable-diffusion-inpainting"
    attn_model_id: str = "zhengchong/CatVTON"
    attn_version: str = "mix"

    width: int = 768
    height: int = 1024
    # Self-attn query dilim boyutu (0=kapali). MPS OOM / Invalid buffer size icin 512.
    attn_slice_size: int = 512
    # Kumas dokusu (logo / dikis / kivrim) icin daha fazla denoising adimi
    num_inference_steps: int = 40
    # Dusuk-orta CFG: giysi detayini korur (CatVTON paper ~2.5; doku icin 2.0)
    guidance_scale: float = 2.0
    seed: int = 42
    # Metal bellek ust siniri; 0.0 = PyTorch watermark gevset
    mps_high_watermark_ratio: str = "0.0"

    # SCHP AutoMasker (pirocheto/schp-lip-20 ONNX INT8)
    schp_enabled: bool = True
    schp_model_id: str = "pirocheto/schp-lip-20"
    schp_onnx_file: str = "onnx/schp-lip-20-int8-static.onnx"
    schp_cloth_type: str = "upper"  # upper | lower | overall
    schp_dilate_px: int = 9
    schp_num_threads: int = 4
    # auto | cpu | coreml
    schp_onnx_provider: str = "auto"
    schp_retry_on_fail: bool = False

    # Pose / DensePose-benzeri guiding (SCHP ustune)
    pose_enabled: bool = True
    pose_refine_mask: bool = True
    pose_hint_alpha: float = 0.28  # soft-structure gucu (0=kapali)
    pose_min_score: float = 0.35
    # true: HF ONNX indir (DWPose/RTMPose); false: SCHP-pseudo
    pose_onnx_enabled: bool = False
    pose_model_id: str = "fashn-ai/DWPose"
    pose_onnx_file: str = "dw-ll_ucoco_384.onnx"
    pose_num_threads: int = 2
    pose_retry_on_fail: bool = False

    # api | celery | serverless — container entrypoint ile hizali
    execution_mode: str = "api"
    # true: baslangicta SCHP (+ops. CatVTON) cache isit
    prewarm: bool = False
    prewarm_catvton: bool = False

    # Object storage (MinIO / R2). Java ile ayni AURA_S3_* ; worker override AURA_VTON_S3_*.
    s3_enabled: bool = False
    s3_endpoint: str = Field(
        default="http://127.0.0.1:9000",
        validation_alias=AliasChoices("AURA_S3_ENDPOINT", "AURA_VTON_S3_ENDPOINT"),
    )
    s3_public_base_url: str = Field(
        default="http://127.0.0.1:9000",
        validation_alias=AliasChoices("AURA_S3_PUBLIC_BASE_URL", "AURA_VTON_S3_PUBLIC_BASE_URL"),
    )
    s3_region: str = "us-east-1"
    s3_access_key: str = "aura_minio"
    s3_secret_key: str = "aura_minio_secret"
    s3_path_style: bool = True
    s3_vton_bucket: str = "aura-vton"
    s3_wardrobe_bucket: str = "aura-wardrobe"
    s3_avatars_bucket: str = "aura-avatars"
    wardrobe_public_host: str = Field(
        default="",
        validation_alias=AliasChoices("AURA_S3_WARDROBE_PUBLIC_HOST", "AURA_VTON_WARDROBE_PUBLIC_HOST"),
    )
    vton_public_host: str = Field(
        default="",
        validation_alias=AliasChoices("AURA_S3_VTON_PUBLIC_HOST", "AURA_VTON_VTON_PUBLIC_HOST"),
    )
    avatars_public_host: str = Field(
        default="",
        validation_alias=AliasChoices("AURA_S3_AVATARS_PUBLIC_HOST", "AURA_VTON_AVATARS_PUBLIC_HOST"),
    )
    # Java StorageUrlGuard.MAX_DOWNLOAD_BYTES ile senkron.
    max_download_bytes: int = 20 * 1024 * 1024
    # Java StorageUrlGuard.PIN_TTL ile senkron (CDN A kaydi / TOCTOU dengesi).
    pin_ttl_seconds: float = 300.0
    pin_observe_seconds: float = 300.0
    pin_observe_samples: int = 2


settings = Settings()
