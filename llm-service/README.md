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

Phase 3 thin slice: health check plus a single review endpoint.

```http
GET /health
POST /review
```

`POST /review` receives one serialized review slice from the Java backend
(`contextId`, `sliceId`, `repo`, `prNumber`, `items[]` with line-number-annotated
diff hunks and supporting context), calls one OpenAI-compatible chat model, and
returns structured findings:

```json
{
  "findings": [
    {
      "path": "src/A.java",
      "line": 12,
      "severity": "critical|warning|suggestion",
      "title": "short label",
      "message": "what is wrong and why",
      "suggestion": "concrete fix",
      "citedItemIds": ["DIFF#..."]
    }
  ],
  "model": "deepseek-chat",
  "usage": { "promptTokens": 0, "completionTokens": 0 },
  "droppedFindings": 0
}
```

Behavior notes:

- `503` when `EVOREVIEW_LLM_API_KEY` is not configured; the Java side degrades to a
  context-only comment.
- `502` when the upstream provider errors.
- Findings that fail validation (missing path/line/title/message) are dropped and
  counted in `droppedFindings`; unknown severities are clamped to `warning`.

Configuration is via environment variables (see `.env.example`):

- `EVOREVIEW_LLM_API_KEY` (required)
- `EVOREVIEW_LLM_BASE_URL` (default `https://api.openai.com/v1`)
- `EVOREVIEW_LLM_MODEL` (default `gpt-4o-mini`)

## Tests

```bash
.venv\Scripts\python.exe -m pytest tests -q   # Windows
.venv/bin/python -m pytest tests -q           # Linux/macOS
```

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
