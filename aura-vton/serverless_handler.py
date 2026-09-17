"""RunPod / Modal uyumlu serverless VTON handler.

Tek seferlik is: payload → model_loader.try_on → JSON sonuc.
Celery/Redis gerektirmez (scale-to-zero).

Yerel deneme:
  EXECUTION_MODE=serverless AURA_VTON_MOCK_MODEL=true python serverless_handler.py

RunPod:
  EXECUTION_MODE=serverless  (entrypoint) + runpod paketı (opsiyonel)

Modal:
  from serverless_handler import handle_job
"""

from __future__ import annotations

import logging
import os
import sys
import time
import uuid
from typing import Any

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")
logger = logging.getLogger("aura.vton.serverless")


def _extract_input(event: dict[str, Any] | None) -> dict[str, Any]:
    if not event:
        return {}
    if isinstance(event.get("input"), dict):
        return event["input"]
    return event


def handle_job(payload: dict[str, Any]) -> dict[str, Any]:
    """Senkron VTON — Java veya RunPod payload'u.

    Beklenen alanlar (camelCase, Java ile uyumlu):
      jobId, personImageBase64, garmentImageBase64, clothType?
    """
    from app.services.errors import VtonInferenceError
    from app.services.model_loader import catvton_loader

    job_id = str(payload.get("jobId") or payload.get("id") or uuid.uuid4())
    person = payload.get("personImageBase64") or payload.get("person_image_base64")
    garment = payload.get("garmentImageBase64") or payload.get("garment_image_base64")
    cloth_type = (payload.get("clothType") or payload.get("cloth_type") or "upper")
    if isinstance(cloth_type, str):
        cloth_type = cloth_type.strip().lower() or "upper"

    started = time.time()
    logger.info(
        "serverless job basladi jobId=%s clothType=%s person=%s garment=%s",
        job_id,
        cloth_type,
        bool(person),
        bool(garment),
    )

    try:
        catvton_loader.ensure_ready()
        result = catvton_loader.try_on(
            job_id=job_id,
            person_image_base64=person,
            garment_image_base64=garment,
            cloth_type=cloth_type,
        )
        elapsed_ms = int((time.time() - started) * 1000)
        return {
            "ok": True,
            "jobId": job_id,
            "workerJobId": job_id,
            "status": "COMPLETED",
            "resultImageUri": result.get("resultImageUri"),
            "resultImageBase64": result.get("resultImageBase64"),
            "maskSource": result.get("maskSource"),
            "poseSource": result.get("poseSource"),
            "clothType": result.get("clothType", cloth_type),
            "model": result.get("model"),
            "device": result.get("device"),
            "elapsedMs": elapsed_ms,
        }
    except VtonInferenceError as exc:
        logger.error("serverless kontrollu hata [%s]: %s", exc.code, exc.message)
        return {
            "ok": False,
            "jobId": job_id,
            "status": "FAILED",
            "errorMessage": f"[{exc.code}] {exc.message}",
        }
    except Exception as exc:  # noqa: BLE001
        logger.exception("serverless beklenmeyen hata jobId=%s", job_id)
        return {
            "ok": False,
            "jobId": job_id,
            "status": "FAILED",
            "errorMessage": str(exc),
        }


def runpod_handler(event: dict[str, Any]) -> dict[str, Any]:
    """RunPod Serverless entry — event['input'] → handle_job."""
    return handle_job(_extract_input(event))


def _tiny_png_b64() -> str:
    # 1x1 PNG
    return (
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    )


def _local_demo() -> int:
    """Mock model ile tek is — container / CI dogrulama."""
    os.environ.setdefault("AURA_VTON_MOCK_MODEL", "true")
    payload = {
        "jobId": "local-demo",
        "personImageBase64": _tiny_png_b64(),
        "garmentImageBase64": _tiny_png_b64(),
        "clothType": "upper",
    }
    out = handle_job(payload)
    logger.info("local demo sonuc: ok=%s status=%s", out.get("ok"), out.get("status"))
    return 0 if out.get("ok") else 1


def main() -> int:
    # RunPod runtime varsa SDK ile baglan
    try:
        import runpod  # type: ignore

        logger.info("RunPod SDK bulundu — start(runpod_handler)")
        runpod.serverless.start({"handler": runpod_handler})
        return 0
    except ImportError:
        logger.info("runpod paketi yok — yerel demo / stdin JSON modu")

    # PIPELINE: tek satir JSON stdin (Modal/custom)
    if not sys.stdin.isatty():
        import json

        raw = sys.stdin.read().strip()
        if raw:
            event = json.loads(raw)
            result = runpod_handler(event if isinstance(event, dict) else {})
            print(json.dumps(result))
            return 0 if result.get("ok") else 1

    return _local_demo()


if __name__ == "__main__":
    raise SystemExit(main())
