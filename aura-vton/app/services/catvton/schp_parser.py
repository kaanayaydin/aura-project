"""SCHP (Self-Correction Human Parsing) — ONNX INT8 runtime.

Model: Hugging Face `pirocheto/schp-lip-20` (LIP, 20 sinif).
Cache: ~/.cache/aura-vton/schp/
Inferans tercihen CPU (INT8); CatVTON GPU/MPS bellegini paylasmaz.
"""

from __future__ import annotations

import logging
import threading
from pathlib import Path

import numpy as np
from PIL import Image

from app.config import settings

logger = logging.getLogger("aura.vton.schp")

# LIP etiketleri (pirocheto/schp-lip-20)
LIP_ID2LABEL = {
    0: "Background",
    1: "Hat",
    2: "Hair",
    3: "Glove",
    4: "Sunglasses",
    5: "Upper-clothes",
    6: "Dress",
    7: "Coat",
    8: "Socks",
    9: "Pants",
    10: "Jumpsuits",
    11: "Scarf",
    12: "Skirt",
    13: "Face",
    14: "Left-arm",
    15: "Right-arm",
    16: "Left-leg",
    17: "Right-leg",
    18: "Left-shoe",
    19: "Right-shoe",
}

IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)
SCHP_INPUT_SIZE = 473


class SchpOnnxParser:
    """HF ONNX INT8 SCHP — lazy load + parse."""

    def __init__(
        self,
        model_id: str | None = None,
        onnx_file: str | None = None,
        cache_dir: str | None = None,
    ) -> None:
        self.model_id = model_id or settings.schp_model_id
        self.onnx_file = onnx_file or settings.schp_onnx_file
        self.cache_dir = Path(cache_dir or settings.cache_dir).expanduser() / "schp"
        self._session = None
        self._input_name: str | None = None
        self._output_name: str | None = None
        self._lock = threading.Lock()
        self._load_error: str | None = None

    @property
    def available(self) -> bool:
        return self._session is not None

    @property
    def load_error(self) -> str | None:
        return self._load_error

    def ensure_loaded(self) -> bool:
        with self._lock:
            if self._session is not None:
                return True
            if self._load_error is not None and not settings.schp_retry_on_fail:
                return False
            try:
                self._session = self._create_session()
                self._load_error = None
                logger.info(
                    "SCHP ONNX hazir: model=%s file=%s",
                    self.model_id,
                    self.onnx_file,
                )
                return True
            except Exception as exc:  # noqa: BLE001
                self._load_error = str(exc)
                logger.warning("SCHP yuklenemedi, torso fallback kullanilacak: %s", exc)
                return False

    def _create_session(self):
        try:
            import onnxruntime as ort
            from huggingface_hub import hf_hub_download
        except ImportError as exc:
            raise RuntimeError(
                "SCHP icin onnxruntime + huggingface_hub gerekli "
                "(pip install -r requirements-ml.txt)"
            ) from exc

        self.cache_dir.mkdir(parents=True, exist_ok=True)
        model_path = hf_hub_download(
            repo_id=self.model_id,
            filename=self.onnx_file,
            cache_dir=str(self.cache_dir / "hub"),
        )

        opts = ort.SessionOptions()
        opts.intra_op_num_threads = max(1, settings.schp_num_threads)
        opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL

        providers = self._select_providers()
        session = ort.InferenceSession(model_path, sess_options=opts, providers=providers)
        self._input_name = session.get_inputs()[0].name
        # logits tercih; yoksa ilk cikti
        out_names = [o.name for o in session.get_outputs()]
        self._output_name = "logits" if "logits" in out_names else out_names[0]
        logger.info("SCHP providers=%s input=%s output=%s", providers, self._input_name, self._output_name)
        return session

    def _select_providers(self) -> list[str]:
        """CPU INT8 birincil; CoreML varsa denenebilir (MPS GPU'yu bos birakir)."""
        try:
            import onnxruntime as ort

            available = set(ort.get_available_providers())
        except ImportError:
            return ["CPUExecutionProvider"]

        providers: list[str] = []
        preferred = (settings.schp_onnx_provider or "auto").strip().lower()
        if preferred == "coreml" and "CoreMLExecutionProvider" in available:
            providers.append("CoreMLExecutionProvider")
        elif preferred == "auto" and "CoreMLExecutionProvider" in available:
            # Apple Silicon: CoreML opsiyonel — INT8 model CPU'da daha stabil
            pass
        providers.append("CPUExecutionProvider")
        return providers

    def parse(self, person: Image.Image) -> np.ndarray:
        """RGB kisi → (H, W) int64 LIP etiket haritasi (orijinal cozunurluk)."""
        if not self.ensure_loaded():
            raise RuntimeError(self._load_error or "SCHP session yok")

        assert self._session is not None
        orig_w, orig_h = person.size
        pixel_values = preprocess_schp(person)  # (1, 3, 473, 473)
        outputs = self._session.run(
            [self._output_name],
            {self._input_name: pixel_values},
        )
        logits = outputs[0]
        # (1, C, h, w) veya (1, h, w)
        if logits.ndim == 4:
            seg_small = logits.argmax(axis=1).squeeze(0).astype(np.int64)
        else:
            seg_small = logits.squeeze().astype(np.int64)

        seg = Image.fromarray(seg_small.astype(np.uint8), mode="L").resize(
            (orig_w, orig_h),
            resample=Image.NEAREST,
        )
        return np.array(seg, dtype=np.int64)


def preprocess_schp(image: Image.Image) -> np.ndarray:
    """SCHP ONNX girisi: NCHW float32, ImageNet normalize, 473×473."""
    rgb = image.convert("RGB").resize((SCHP_INPUT_SIZE, SCHP_INPUT_SIZE), Image.BILINEAR)
    arr = np.asarray(rgb, dtype=np.float32) / 255.0
    arr = (arr - IMAGENET_MEAN) / IMAGENET_STD
    arr = arr.transpose(2, 0, 1)[None, ...]  # 1,3,H,W
    return np.ascontiguousarray(arr, dtype=np.float32)


_schp_singleton: SchpOnnxParser | None = None
_schp_lock = threading.Lock()


def get_schp_parser() -> SchpOnnxParser:
    global _schp_singleton
    with _schp_lock:
        if _schp_singleton is None:
            _schp_singleton = SchpOnnxParser()
        return _schp_singleton
