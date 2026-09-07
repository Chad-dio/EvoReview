# LLM Service

FastAPI service for EvoReview AI inference.

## Responsibility

Future AI inference service:

- LLM calls
- Reviewer reasoning
- Judge reasoning
- Reflection
- Rule candidate generation
- Prompt candidate generation

This service is not the business controller. Java Backend owns Git, workflow, and persistence.

## Current Status

Health API only.

```http
GET /health
```

```json
{
  "service": "evoreview-llm-service",
  "status": "UP"
}
```

No LLM API call, prompt, or agent logic.

## Tech Stack

- Python 3.9+ (Phase 0 was verified on Python 3.9)
- FastAPI
- Uvicorn

## Start

Windows:

```bash
cd llm-service
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Linux/macOS:

```bash
cd llm-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Default URL: `http://localhost:8000`

## Next

Reviewer inference after the backend can supply review context.
