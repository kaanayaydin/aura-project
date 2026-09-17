from __future__ import annotations

from typing import Optional, Union

from pydantic import BaseModel, Field


class EnqueueRequest(BaseModel):
    jobId: Union[int, str] = Field(..., description="Aura (Java) job kimligi")
    userId: Optional[int] = None
    wardrobeItemId: Optional[int] = None
    personImageBase64: Optional[str] = None
    garmentImageBase64: Optional[str] = None
    personImageUrl: Optional[str] = None
    garmentImageUrl: Optional[str] = None
    clothType: Optional[str] = Field(
        default="upper",
        description="SCHP agnostic bolge: upper | lower | overall",
    )


class EnqueueResponse(BaseModel):
    jobId: str
    workerJobId: str
    celeryTaskId: str
    status: str


class StatusResponse(BaseModel):
    jobId: str
    workerJobId: Optional[str] = None
    celeryTaskId: Optional[str] = None
    status: str
    resultImageUri: Optional[str] = None
    resultImageBase64: Optional[str] = None
    errorMessage: Optional[str] = None
