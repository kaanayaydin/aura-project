# Aura VTON — Cloud GPU / Serverless hazir Dockerfile
#
# Yerel mock (ucretsiz, CUDA yok):
#   docker build --build-arg BASE_IMAGE=python:3.11-slim-bookworm --build-arg INSTALL_ML=false -t aura-vton:local .
#
# GPU uretim:
#   docker build --build-arg INSTALL_ML=true -t aura-vton:gpu .
#
# EXECUTION_MODE=api|celery|serverless|prewarm

ARG BASE_IMAGE=pytorch/pytorch:2.2.2-cuda12.1-cudnn8-runtime
FROM ${BASE_IMAGE}

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    AURA_VTON_CACHE_DIR=/root/.cache/aura-vton \
    AURA_VTON_OUTPUT_DIR=/root/.cache/aura-vton/outputs \
    HF_HOME=/root/.cache/aura-vton/hf \
    HUGGINGFACE_HUB_CACHE=/root/.cache/aura-vton/hf/hub \
    EXECUTION_MODE=api \
    AURA_VTON_MOCK_MODEL=true \
    AURA_VTON_PREWARM=false \
    AURA_VTON_PORT=8001

ARG INSTALL_ML=false
ARG PREWARM_ON_BUILD=false

USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        libgl1 \
        libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/* \
    && python -m pip install --upgrade pip setuptools wheel

WORKDIR /app

COPY requirements.txt requirements-ml.txt ./
RUN pip install -r requirements.txt \
    && if [ "$INSTALL_ML" = "true" ]; then \
         pip install -r requirements-ml.txt; \
       else \
         pip install "numpy>=1.26.0"; \
       fi

COPY . .

RUN chmod +x /app/entrypoint.sh \
    && mkdir -p "${AURA_VTON_CACHE_DIR}" "${AURA_VTON_OUTPUT_DIR}" \
    && if [ "$PREWARM_ON_BUILD" = "true" ] && [ "$INSTALL_ML" = "true" ]; then \
         AURA_VTON_PREWARM=true AURA_VTON_PREWARM_CATVTON=true \
           python /app/scripts/prewarm_cache.py; \
       fi

EXPOSE 8001

# Scale-to-zero: platform idle container'i durdurur; cold-start'ta prewarm acilabilir
ENTRYPOINT ["/app/entrypoint.sh"]
CMD []
