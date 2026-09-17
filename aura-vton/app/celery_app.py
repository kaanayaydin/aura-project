from __future__ import annotations

from celery import Celery

from app.config import settings

celery = Celery(
    "aura_vton",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=["app.tasks"],
)

celery.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="UTC",
    enable_utc=True,
    task_track_started=True,
    task_default_queue=settings.celery_queue,
    result_expires=86400,
)
