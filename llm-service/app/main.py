from fastapi import FastAPI

from app.api.health import router as health_router
from app.api.review import router as review_router

app = FastAPI(title="EvoReview LLM Service")
app.include_router(health_router)
app.include_router(review_router)
