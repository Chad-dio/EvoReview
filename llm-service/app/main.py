from fastapi import FastAPI

from app.api.health import router as health_router

app = FastAPI(title="EvoReview LLM Service")
app.include_router(health_router)
