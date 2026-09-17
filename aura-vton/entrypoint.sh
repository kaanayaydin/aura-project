#!/usr/bin/env bash
# Aura VTON container girisi.
# EXECUTION_MODE:
#   api        — FastAPI (uvicorn) — yerel / uzun yasayan worker
#   celery     — Celery worker (+ opsiyonel API yaninda)
#   serverless — RunPod/Modal tek-is handler (scale-to-zero)
#   prewarm    — sadece cache isitma, sonra cikis
set -euo pipefail

MODE="${EXECUTION_MODE:-api}"
PORT="${AURA_VTON_PORT:-8001}"
CACHE_DIR="${AURA_VTON_CACHE_DIR:-${HOME}/.cache/aura-vton}"
mkdir -p "${CACHE_DIR}" "${AURA_VTON_OUTPUT_DIR:-${CACHE_DIR}/outputs}"

echo "[aura-vton] EXECUTION_MODE=${MODE} mock=${AURA_VTON_MOCK_MODEL:-true} cache=${CACHE_DIR}"

if [[ "${AURA_VTON_PREWARM:-false}" == "true" ]]; then
  echo "[aura-vton] prewarm basliyor..."
  python /app/scripts/prewarm_cache.py || echo "[aura-vton] prewarm uyarisi (devam)"
fi

case "${MODE}" in
  prewarm)
    python /app/scripts/prewarm_cache.py
    exit 0
    ;;
  api)
    exec uvicorn main:app --host 0.0.0.0 --port "${PORT}"
    ;;
  celery)
    # Redis URL zorunlu; mock ortamda bile Celery broker bekler
    exec celery -A app.celery_app.celery worker \
      --loglevel="${CELERY_LOGLEVEL:-INFO}" \
      --concurrency="${CELERY_CONCURRENCY:-1}" \
      -Q "${AURA_VTON_CELERY_QUEUE:-aura-vton}"
    ;;
  api+celery|both)
    celery -A app.celery_app.celery worker \
      --loglevel="${CELERY_LOGLEVEL:-INFO}" \
      --concurrency="${CELERY_CONCURRENCY:-1}" \
      -Q "${AURA_VTON_CELERY_QUEUE:-aura-vton}" &
    exec uvicorn main:app --host 0.0.0.0 --port "${PORT}"
    ;;
  serverless)
    exec python /app/serverless_handler.py
    ;;
  *)
    echo "[aura-vton] Bilinmeyen EXECUTION_MODE=${MODE} (api|celery|serverless|prewarm)"
    exit 1
    ;;
esac
