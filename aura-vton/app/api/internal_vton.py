from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException

from app.schemas import EnqueueRequest, EnqueueResponse, StatusResponse
from app.services.job_store import STATUS_QUEUED, job_store
from app.tasks import run_try_on

logger = logging.getLogger("aura.vton.api")

router = APIRouter(prefix="/internal/vton", tags=["vton-internal"])


@router.post("/enqueue", response_model=EnqueueResponse)
def enqueue(body: EnqueueRequest) -> EnqueueResponse:
    job_id = str(body.jobId)

    async_result = run_try_on.delay(
        job_id,
        user_id=body.userId,
        wardrobe_item_id=body.wardrobeItemId,
        person_image_base64=body.personImageBase64,
        garment_image_base64=body.garmentImageBase64,
        person_image_url=body.personImageUrl,
        garment_image_url=body.garmentImageUrl,
        cloth_type=(body.clothType or "upper").strip().lower(),
    )

    job_store.put(
        job_id,
        {
            "jobId": job_id,
            "workerJobId": job_id,
            "celeryTaskId": async_result.id,
            "status": STATUS_QUEUED,
            "userId": body.userId,
            "wardrobeItemId": body.wardrobeItemId,
            "clothType": (body.clothType or "upper"),
            "resultImageUri": None,
            "errorMessage": None,
        },
    )

    logger.info(
        "VTON enqueue: jobId=%s celeryTaskId=%s itemId=%s clothType=%s",
        job_id,
        async_result.id,
        body.wardrobeItemId,
        body.clothType or "upper",
    )

    return EnqueueResponse(
        jobId=job_id,
        workerJobId=job_id,
        celeryTaskId=async_result.id,
        status=STATUS_QUEUED,
    )


@router.get("/status/{job_id}", response_model=StatusResponse)
def status(job_id: str) -> StatusResponse:
    payload = job_store.get(str(job_id))
    if payload is None:
        raise HTTPException(status_code=404, detail=f"VTON isi bulunamadi: {job_id}")

    return StatusResponse(
        jobId=str(payload.get("jobId", job_id)),
        workerJobId=payload.get("workerJobId"),
        celeryTaskId=payload.get("celeryTaskId"),
        status=str(payload.get("status", STATUS_QUEUED)),
        resultImageUri=payload.get("resultImageUri"),
        resultImageBase64=payload.get("resultImageBase64"),
        errorMessage=payload.get("errorMessage"),
    )
