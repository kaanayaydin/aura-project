"""RotNet ONNX inference — geometrik skoru değiştirmez, 4-sınıf softmax üretir."""

from __future__ import annotations

import logging
import threading
from pathlib import Path
from typing import Any, Optional

import numpy as np
from PIL import Image

from app.core.config import settings

ROTNET_CLASSES = (0, 90, 180, 270)
CLASS_TO_EDGE = {0: "top", 90: "right", 180: "bottom", 270: "left"}


def preprocess_mask(alpha, size: int = 64) -> np.ndarray:
    """Alpha → (1, 1, size, size) float32 binary. Torch gerekmez."""
    if isinstance(alpha, Image.Image):
        mask = alpha.split()[-1] if alpha.mode == "RGBA" else alpha.convert("L")
        mask = mask.resize((size, size), Image.Resampling.NEAREST)
        arr = np.asarray(mask, dtype=np.float32) / 255.0
    else:
        arr = np.asarray(alpha)
        if arr.ndim == 3:
            arr = arr[..., -1]
        img = Image.fromarray(
            ((arr > 127).astype(np.uint8) * 255) if arr.dtype != np.uint8 else arr.astype(np.uint8),
            mode="L",
        )
        img = img.resize((size, size), Image.Resampling.NEAREST)
        arr = np.asarray(img, dtype=np.float32) / 255.0
    arr = (arr > 0.5).astype(np.float32)
    return arr[None, None, ...]

logger = logging.getLogger("aura.vision.rotnet")

_session_lock = threading.Lock()
_session = None
_load_error: Optional[str] = None


def _onnx_path() -> Path:
    return Path(settings.rotnet_onnx_path)


def rotnet_available() -> bool:
    if not bool(settings.rotnet_enabled):
        return False
    return _onnx_path().is_file()


def _get_session():
    global _session, _load_error
    if not rotnet_available():
        return None
    with _session_lock:
        if _session is not None:
            return _session
        if _load_error is not None:
            return None
        try:
            import onnxruntime as ort

            opts = ort.SessionOptions()
            opts.intra_op_num_threads = 1
            _session = ort.InferenceSession(
                str(_onnx_path()),
                sess_options=opts,
                providers=["CPUExecutionProvider"],
            )
            logger.info("RotNet ONNX hazir: %s", _onnx_path())
            return _session
        except Exception as exc:  # noqa: BLE001
            _load_error = str(exc)
            logger.warning("RotNet yuklenemedi: %s", exc)
            return None


def _softmax(logits: np.ndarray) -> np.ndarray:
    x = logits.astype(np.float64)
    x = x - np.max(x)
    e = np.exp(x)
    return (e / np.clip(e.sum(), 1e-12, None)).astype(np.float32)


def infer_rotnet(alpha: np.ndarray | Image.Image) -> Optional[dict[str, Any]]:
    """Alpha mask → {predicted_class, predicted_edge, confidence, probs}.

    Model yoksa None (ensemble geometrik-only'ye düşer).
    """
    session = _get_session()
    if session is None:
        return None
    size = int(settings.rotnet_input_size)
    inp = preprocess_mask(alpha, size=size)
    input_name = session.get_inputs()[0].name
    logits = session.run(None, {input_name: inp})[0]
    vec = np.asarray(logits[0], dtype=np.float32)
    probs = _softmax(vec)
    idx = int(np.argmax(probs))
    predicted = int(ROTNET_CLASSES[idx])
    conf = float(probs[idx])
    payload = {
        "predicted_class": predicted,
        "predicted_edge": CLASS_TO_EDGE[predicted],
        "confidence": round(conf, 4),
        "probs": {str(c): round(float(probs[i]), 4) for i, c in enumerate(ROTNET_CLASSES)},
        "available": True,
    }
    logger.info("RotNet: class=%s edge=%s conf=%.3f", predicted, payload["predicted_edge"], conf)
    return payload


def apply_orientation_ensemble(geometric: dict[str, Any], alpha: np.ndarray) -> dict[str, Any]:
    """Geometrik skora RotNet ekler; depth/symmetry/centrality ağırlıklarını değiştirmez.

    Karar:
      aynı kenar ve geometri high → high (iki bağımsız güçlü sinyal)
      aynı kenar ve geometri low → medium (eşleşme tesadüfi olabilir)
      geometric low ve rotnet.confidence > 0.8 → medium + rotnet kenarı
      aksi halde → low (insan-in-the-loop)
    """
    scores = dict(geometric)
    geo_edge = str(scores.get("best") or "top")
    geo_low = bool(scores.get("low_confidence"))
    geo_conf = "low" if geo_low else "high"
    rotnet = infer_rotnet(alpha)
    scores["geometric"] = {
        "chosen_edge": geo_edge,
        "confidence": geo_conf,
        "low_confidence": geo_low,
        "score_gap": scores.get("score_gap"),
        "max_depth_across_edges": scores.get("max_depth_across_edges"),
        "decision_note": scores.get("decision_note"),
    }

    if rotnet is None:
        scores["rotnet"] = {"available": False}
        scores["ensemble"] = {
            "final_confidence": geo_conf,
            "final_edge": geo_edge,
            "reason": "rotnet_unavailable",
        }
        scores["ensemble_confidence"] = geo_conf
        scores["requires_confirmation"] = geo_conf != "high"
        return scores

    scores["rotnet"] = rotnet
    rot_edge = str(rotnet["predicted_edge"])
    rot_conf = float(rotnet["confidence"])
    min_conf = float(settings.rotnet_min_confidence)

    if geo_edge == rot_edge:
        if geo_conf == "high":
            final_conf, final_edge, reason = "high", geo_edge, "agreement"
        else:
            final_conf, final_edge, reason = "medium", geo_edge, "agreement_geometry_low"
    elif geo_conf == "low" and rot_conf > min_conf:
        final_conf, final_edge, reason = "medium", rot_edge, "rotnet_override_low_geometry"
    else:
        final_conf, final_edge, reason = "low", geo_edge, "disagreement"

    scores["ensemble"] = {
        "final_confidence": final_conf,
        "final_edge": final_edge,
        "reason": reason,
        "rotnet_confidence": rot_conf,
        "geometric_confidence": geo_conf,
    }
    scores["ensemble_confidence"] = final_conf
    # Pipeline: medium/high uygula; low güvenlik ağı
    scores["best"] = final_edge
    scores["low_confidence"] = final_conf == "low"
    # Otomatik uygulama yalnızca iki bağımsız sinyal de güçlüyse
    scores["requires_confirmation"] = not (final_conf == "high" and geo_conf == "high")
    return scores
