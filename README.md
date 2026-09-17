# Aura

Modular monorepo for asynchronous virtual garment try-on. A Spring Boot API gateway orchestrates JWT-authenticated clients, wardrobe and styling domains, and long-running diffusion jobs. Python workers run CatVTON conditional inpainting, garment normalization, and pose/segmentation pipelines. Redis-backed Celery queues keep HTTP request paths non-blocking. MinIO (S3-compatible) owns the asset lifecycle for wardrobe, avatars, and try-on outputs.

**Performance posture**

- **MPS (Metal Performance Shaders)** — CatVTON inference on Apple Silicon with VAE slicing, attention slicing, and unified-memory watermark controls to keep high-resolution passes within device limits.
- **Decoupled async queues** — VTON jobs are enqueued to Celery (`aura-vton`); the backend returns a job id and clients poll status instead of holding an HTTP worker for multi-minute diffusion.
- **S3-compatible object storage** — large images move via MinIO buckets and presigned upload URLs; API responses carry durable object URLs rather than multi-megabyte Base64 payloads.

---

## Architecture

```
┌──────────────┐     JWT + REST      ┌─────────────────────────────┐
│  Client      │ ──────────────────► │  aura-backend (Spring Boot) │
│  Flutter     │ ◄────────────────── │  :8080  API gateway         │
│  (mobile)    │   job status / URL  │  Auth · Wardrobe · VTON     │
└──────────────┘                     └──────────────┬──────────────┘
                                                    │
                    ┌───────────────────────────────┼───────────────────────────────┐
                    │                               │                               │
                    ▼                               ▼                               ▼
         ┌──────────────────┐          ┌────────────────────┐          ┌────────────────────┐
         │  aura-vision     │          │  Redis             │          │  MinIO (S3 API)    │
         │  FastAPI :8000   │          │  broker / result   │          │  :9000 / :9001     │
         │  YOLO · SAM ·    │          │  :6379             │          │  wardrobe · vton   │
         │  normalize-garment│          └─────────┬──────────┘          │  · avatars         │
         └──────────────────┘                    │                     └─────────▲──────────┘
                                                 │ enqueue / poll                 │
                                                 ▼                                │ get/put
                                      ┌────────────────────┐                      │
                                      │  Celery worker     │                      │
                                      │  queue: aura-vton │──────────────────────┘
                                      │                    │
                                      │  aura-vton         │
                                      │  FastAPI :8001     │
                                      │  CatVTON · SCHP    │
                                      │  PyTorch MPS/CUDA  │
                                      └────────────────────┘

Data plane (PostgreSQL :5432) stores users, wardrobe metadata, favorites, and VTON job rows.
Object plane (MinIO) stores binary assets. Inference plane (Celery + PyTorch) never blocks the gateway.
```

**Try-on request path (happy path)**

1. Mobile authenticates (`/api/v1/aura/auth/*`) and uploads person/garment assets (presigned PUT or backend-mediated upload).
2. Client calls `POST /api/v1/aura/vton/request` with wardrobe item + person image reference.
3. Backend persists a `QUEUED` job, calls the VTON worker enqueue API, and returns `jobId`.
4. Celery worker loads person/garment from URL or payload, builds an SCHP-based agnostic mask, runs CatVTON on MPS/CUDA/CPU, writes the result PNG to MinIO (or local output store), and marks the job complete.
5. Client polls `GET /api/v1/aura/vton/status/{jobId}` until `COMPLETED` / `FAILED`, then loads the result via the backend-owned image URL.

---

## Monorepo modules

| Module | Role |
|--------|------|
| **`aura-backend`** | Java 21 / Spring Boot 3 API gateway. JWT auth (access + rotating refresh), wardrobe CRUD, favorites, perfume shelf, weather + outfit suggestion, Aura chat orchestration, VTON job orchestration and ownership checks, S3/MinIO storage client (presign + put). PostgreSQL via Spring Data JPA. |
| **`aura-vton`** | FastAPI control plane + Celery worker for CatVTON conditional diffusion. SCHP (LIP) semantic masks, pose guidance hooks, MPS/CUDA/CPU device selection, mock mode for CI, optional RunPod/Modal serverless entrypoint. |
| **`aura-vision`** | FastAPI vision service: garment analysis (YOLO / optional SAM & CLIP), studio `normalize-garment` (rembg/chroma cutout + 3:4 framing), wardrobe sync hooks toward the backend. |
| **`aura-mobile`** | Flutter client: auth session, wardrobe grid, VTON request/poll UX (before/after), Aura chat, favorites. Talks only to `aura-backend` over REST. |

Infrastructure defined at the repo root (`docker-compose.yml`): PostgreSQL 16, Redis 7, MinIO + bucket bootstrap (`aura-wardrobe`, `aura-vton`, `aura-avatars`).

---

## Tech stack

| Layer | Choices |
|-------|---------|
| **Languages & frameworks** | Java 21, Spring Boot 3, Spring Security, Spring Data JPA · Python 3.10+ · FastAPI · Flutter / Dart |
| **ML / inference** | PyTorch, CatVTON (Zheng-Chong), TorchVision, SCHP ONNX (LIP-20), optional rembg / YOLO / SAM / CLIP |
| **Hardware acceleration** | Apple Silicon **MPS**, NVIDIA **CUDA**, CPU fallback; `PYTORCH_ENABLE_MPS_FALLBACK`, `PYTORCH_MPS_HIGH_WATERMARK_RATIO` |
| **Distributed systems** | Celery, Redis (broker + backend), HTTP job enqueue/status between Spring and the VTON worker |
| **Storage** | PostgreSQL · MinIO (S3 API; path-style) · AWS SDK S3 client/presigner on the backend · Cloudflare R2–compatible via the same S3 settings |
| **Ops** | Docker Compose for data plane · Uvicorn for Python HTTP · Maven for the backend |

---

## Quickstart & local setup

### Prerequisites

- Docker and Docker Compose
- JDK 21+ (backend)
- Python 3.10+ with venv (vision + VTON)
- Flutter SDK (mobile)
- Optional: Hugging Face access for CatVTON / SCHP weights; Apple Silicon recommended for local MPS inference

### Environment

```bash
cp .env.example .env
# Edit secrets and ports as needed. Defaults match docker-compose and application.yml.
```

Key variables (see `.env.example` for the full set):

| Variable | Default (local) | Purpose |
|----------|-----------------|---------|
| `AURA_DB_*` | `aura` / `5432` | PostgreSQL |
| `AURA_S3_*` | MinIO on `:9000` | Object storage credentials & buckets |
| `AURA_VISION_URL` | `http://127.0.0.1:8000` | Vision base URL |
| `AURA_VTON_WORKER_URL` | `http://127.0.0.1:8001` | VTON FastAPI base URL |
| `AURA_VTON_MOCK` | `false` | Backend mock worker (tests) |
| `AURA_VTON_MOCK_MODEL` | `true` in CI | VTON worker skips real CatVTON |
| `AURA_JWT_SECRET` | dev placeholder | **Must change** outside local demos |
| `AURA_STORAGE_PROVIDER` | `s3` | `s3` or `memory` |

### Data plane (Compose)

```bash
docker compose up --build -d
docker compose ps
```

This starts PostgreSQL (`5432`), Redis (`6379`), MinIO API (`9000`), MinIO console (`9001`), and creates the three buckets with anonymous download on wardrobe/vton/avatars for local client loads.

### Application processes (host)

**Backend**

```bash
cd aura-backend
./mvnw spring-boot:run
# listens on :8080
```

**Vision**

```bash
cd aura-vision
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export PYTHONPATH=.
uvicorn main:app --host 0.0.0.0 --port 8000
```

**VTON (mock — no GPU weights required)**

```bash
cd aura-vton
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export AURA_VTON_MOCK_MODEL=true
export PYTHONPATH=.
uvicorn main:app --host 0.0.0.0 --port 8001
# second terminal:
celery -A app.celery_app.celery worker --pool=solo -l INFO -Q aura-vton
```

**VTON (real CatVTON on Apple Silicon)**

```bash
pip install -r requirements.txt -r requirements-ml.txt
export AURA_VTON_MOCK_MODEL=false
export PYTORCH_ENABLE_MPS_FALLBACK=1
export PYTORCH_MPS_HIGH_WATERMARK_RATIO=0.0
export HF_HUB_DISABLE_XET=1
export PYTHONPATH=.
celery -A app.celery_app.celery worker --pool=solo -l INFO -Q aura-vton
uvicorn main:app --port 8001
```

**Mobile**

```bash
cd aura-mobile
flutter pub get
flutter run
# Point API base URL at http://127.0.0.1:8080 (or host LAN IP for a physical device).
```

### Health checks & API inspection

```bash
# Infrastructure
curl -s http://127.0.0.1:9000/minio/health/live
redis-cli -p 6379 ping
docker compose exec postgres pg_isready -U aura -d aura

# Services
curl -s http://127.0.0.1:8000/health | python3 -m json.tool   # aura-vision
curl -s http://127.0.0.1:8001/health | python3 -m json.tool   # aura-vton

# Backend (OpenAPI / actuators if exposed; auth + domain under /api/v1)
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/api/v1/weather
# Interactive docs (Python services)
open http://127.0.0.1:8000/docs
open http://127.0.0.1:8001/docs
# MinIO console
open http://127.0.0.1:9001
```

Representative authenticated flows (after `POST /api/v1/aura/auth/register` + `login`):

- `POST /api/v1/storage/upload-url` — presigned PUT for large assets  
- `POST /api/v1/wardrobe` — wardrobe create (optional Vision normalize → MinIO)  
- `POST /api/v1/aura/vton/request` → poll `GET /api/v1/aura/vton/status/{jobId}`  
- `POST /api/v1/aura/suggest`, `POST /api/v1/aura/chat` — styling / LLM-assisted chat  

---

## Key engineering challenges solved

### 1. Asynchronous queue decoupling for long inference

Diffusion try-on routinely exceeds HTTP client and gateway timeouts. The backend records a job row and enqueues work on Redis/Celery; the FastAPI VTON process accepts enqueue/status without running UNet on the request thread. Clients poll until terminal state. This isolates cold starts, GPU contention, and OOM recovery from the Spring request pool.

### 2. Unified object storage lifecycle (presigned S3)

Wardrobe and VTON binaries are too large for sustained Base64-in-JSON traffic. The backend issues time-limited presigned upload URLs against MinIO (or R2), stores canonical object keys/URLs on entities, and passes HTTP URLs into workers. Buckets are bootstrapped by Compose (`minio-init`). Path-style addressing and a configurable public base URL keep local Docker hostnames and device-reachable URLs aligned.

### 3. MPS unified-memory management for high-resolution diffusion

On Apple Silicon, unconstrained person resolution can inflate CatVTON concat-attention memory into multi‑GiB Metal buffers and crash the process. The worker forces a bounded preprocess canvas (e.g. 768×1024), restores output scale after inference, enables VAE and attention slicing, and sets `PYTORCH_MPS_HIGH_WATERMARK_RATIO=0.0` so PyTorch can reclaim unified memory between steps. Celery `--pool=solo` avoids multi-process MPS contention on a single Mac.

---

## Repository layout

```
aura-project/
├── docker-compose.yml      # Postgres, Redis, MinIO (+ bucket init)
├── .env.example
├── aura-backend/           # Spring Boot API gateway
├── aura-vton/              # CatVTON worker (FastAPI + Celery)
├── aura-vision/            # Vision / normalize-garment
├── aura-mobile/            # Flutter client
└── docs/                   # Additional design notes
```

---

## License & contribution

Internal product monorepo. Keep service contracts (DTOs, job status enums, bucket names) versioned together when changing cross-module behavior. Prefer mock VTON (`AURA_VTON_MOCK_MODEL=true`) in CI; reserve full CatVTON + MPS/CUDA for dedicated inference hosts.
