from __future__ import annotations


class VtonInferenceError(RuntimeError):
    """Kontrollu inference hatasi — job FAILED + anlamli mesaj."""

    def __init__(self, message: str, *, code: str = "INFERENCE_ERROR") -> None:
        super().__init__(message)
        self.code = code
        self.message = message


def classify_exception(exc: BaseException) -> VtonInferenceError:
    text = str(exc)
    lowered = text.lower()
    name = type(exc).__name__

    if isinstance(exc, MemoryError) or "out of memory" in lowered or "oom" in lowered:
        return VtonInferenceError(
            "Bellek yetersiz (OOM). Daha kucuk cozunurluk deneyin veya CPU/MPS bellegini bosaltin.",
            code="OOM",
        )
    if "invalid buffer size" in lowered:
        return VtonInferenceError(
            "Metal/MPS buffer limiti asildi (Invalid buffer size). "
            "Goruntu 768x1024'e indirgenmeli; PYTORCH_MPS_HIGH_WATERMARK_RATIO=0.0 ve attn slicing acik olmali.",
            code="OOM",
        )
    if "mps" in lowered and ("not implemented" in lowered or "fallback" in lowered):
        return VtonInferenceError(
            f"MPS desteklenmeyen operasyon: {text}. PYTORCH_ENABLE_MPS_FALLBACK=1 kontrol edin.",
            code="MPS_UNSUPPORTED",
        )
    if "cuda" in lowered and "out of memory" in lowered:
        return VtonInferenceError(
            "CUDA bellek yetersiz. num_inference_steps veya cozunurlugu dusurun.",
            code="OOM",
        )
    return VtonInferenceError(f"CatVTON inference basarisiz ({name}): {text}", code="INFERENCE_ERROR")
