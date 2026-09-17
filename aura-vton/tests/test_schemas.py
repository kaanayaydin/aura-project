"""Enqueue sema — clothType varsayilan ve normalizasyon."""

from app.schemas import EnqueueRequest


def test_enqueue_default_cloth_type_is_upper():
    req = EnqueueRequest(jobId=1)
    assert req.clothType == "upper"


def test_enqueue_accepts_lower_and_overall():
    assert EnqueueRequest(jobId=2, clothType="lower").clothType == "lower"
    assert EnqueueRequest(jobId=3, clothType="overall").clothType == "overall"
