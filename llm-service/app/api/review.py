from fastapi import APIRouter, Depends, HTTPException

from app.review.client import get_client, run_review
from app.review.schemas import ReviewRequest, ReviewResponse

router = APIRouter()


@router.post("/review", response_model=ReviewResponse, response_model_by_alias=True)
def review(request: ReviewRequest, client=Depends(get_client)) -> ReviewResponse:
    try:
        return run_review(request, client)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"upstream LLM error: {exc}")
