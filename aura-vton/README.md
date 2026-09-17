# Aura VTON Worker

FastAPI + Celery + (opsiyonel) RunPod/Modal serverless.

## Yerel (mock, maliyet yok)

```bash
pip install -r requirements.txt
export AURA_VTON_MOCK_MODEL=true
uvicorn main:app --port 8001
# baska terminal:
celery -A app.celery_app.celery worker -Q aura-vton -l INFO
```

## Docker (yerel slim)

```bash
docker build --build-arg BASE_IMAGE=python:3.11-slim-bookworm --build-arg INSTALL_ML=false -t aura-vton:local .
docker run --rm -p 8001:8001 -e EXECUTION_MODE=api -e AURA_VTON_MOCK_MODEL=true aura-vton:local
```

## Serverless (RunPod / Modal)

```bash
EXECUTION_MODE=serverless AURA_VTON_MOCK_MODEL=true python serverless_handler.py
# veya container:
docker run --rm -e EXECUTION_MODE=serverless -e AURA_VTON_MOCK_MODEL=true aura-vton:local
```

## GPU image + prewarm

```bash
docker build --build-arg INSTALL_ML=true --build-arg PREWARM_ON_BUILD=true -t aura-vton:gpu .
# runtime cold-start:
docker run --gpus all -e EXECUTION_MODE=serverless -e AURA_VTON_MOCK_MODEL=false \
  -e AURA_VTON_PREWARM=true -e AURA_VTON_PREWARM_CATVTON=true aura-vton:gpu
```

## Ortam degiskenleri

| Degisken | Aciklama |
|----------|----------|
| `EXECUTION_MODE` | `api` \| `celery` \| `serverless` \| `prewarm` |
| `AURA_VTON_MOCK_MODEL` | `true` CI/yerel |
| `AURA_VTON_PREWARM` | SCHP ONNX cache isit |
| `AURA_VTON_PREWARM_CATVTON` | buyuk CatVTON snapshot |
| `AURA_VTON_WORKER_URL` | Java tarafinda worker URL |
