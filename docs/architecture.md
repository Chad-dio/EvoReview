# Architecture

Current Stage: **Project Skeleton Initialization**

## Future Flow

```text
GitHub / GitLab
       ↓
Java Backend
       ↓
Context Builder
       ↓
Python LLM Service
       ↓
Review
       ↓
Feedback
       ↓
Evolution
```

## Service Boundaries

- **Frontend**: operator UI. Talks to Java Backend.
- **Java Backend**: main business system. Owns Git integration, workflow, persistence, and REST APIs.
- **Python LLM Service**: AI inference only. Called by Java Backend over HTTP.

Java is the controller. Python is not the system of record.

## Current Runtime

Phase 0 is a **monorepo**. Locally it already runs as three processes:

```text
Browser
   ↓  http://localhost:5173
Vue Frontend
   ↓  Vite proxy /api → :8080
Java Backend
   ↓  HTTP
Python LLM Service :8000
```

| Service | Stack | Port | Endpoint |
| --- | --- | --- | --- |
| Frontend | Vue 3 + Vite | 5173 | landing page, reads `/api/stack` |
| Backend | Spring Boot 3 | 8080 | `GET /api/health`, `GET /api/stack` |
| LLM Service | FastAPI | 8000 | `GET /health` |

`GET /api/stack` is a smoke check: Backend reports itself UP and probes LLM `/health`.

No database, message queue, Docker image, or real LLM provider is wired yet.

## Monorepo now, containers later

Keep one Git repository. Do not split into three repos just to deploy.

Later Docker/Compose (or Kubernetes) can start the same three processes as three containers on one network. Only the LLM base URL changes, for example:

```text
Local : http://localhost:8000
Docker: http://llm-service:8000
```

That is `EVOREVIEW_LLM_BASE_URL`. Frontend still talks only to Backend; Backend still talks only to LLM Service.
