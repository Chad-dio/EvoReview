# EvoReview

Self-Evolving Git Code Review Agent

Git 自进化代码评审 Agent

## 1. Project Introduction

EvoReview is a self-evolving Git code review agent. It will review pull requests, collect developer feedback, accumulate project knowledge, and improve review quality over time.

This repository is a monorepo. Java Backend is the main business system. Python LLM Service is the AI inference service. Vue Frontend is the operator UI.

## 2. Current Stage

**Phase 1 - Git Integration**

The skeleton can receive a GitHub pull request, list changed files, and post a placeholder comment. There is still no LLM review, rule engine, or evolution.

Current capability:

- Frontend displays a landing page and shows Backend / LLM / GitHub App status.
- Backend provides `GET /api/health`, `GET /api/stack`, and `POST /api/github/webhook`.
- LLM Service provides `GET /health`.

## 3. Architecture

Current runtime:

```text
Vue Frontend
     ↓
Spring Boot Backend
     ↓
FastAPI LLM Service
```

Future flow:

```text
Git
 ↓
Context
 ↓
Review
 ↓
Feedback
 ↓
Evolution
```

Future architecture principle:

```text
Frontend
    ↓
Java Backend
    ↓ HTTP
Python LLM Service
    ↓
LLM
```

Java is the main business controller. Python is the AI inference service. Python should not become the system of record for Git, webhooks, database, or workflow.

## 4. Directory Structure

```text
EvoReview/
├── frontend/        Vue 3 + TypeScript + Vite
├── backend/         Spring Boot 3 health API
├── llm-service/     FastAPI health API
├── docs/            Architecture, development, roadmap
├── scripts/         Future helper scripts
├── .gitignore
├── .editorconfig
└── README.md
```

## 5. Tech Stack

Currently used:

- Vue 3
- TypeScript
- Vite
- Java
- Spring Boot
- Python
- FastAPI
- Git
- GitHub App (webhook)

### Possible Future Components

Not introduced yet:

- PostgreSQL / MySQL
- Redis
- Kafka
- LangChain / LangGraph
- Vector databases
- Authentication / RBAC

## 6. Local Development

See [docs/development.md](docs/development.md) and [docs/github-app.md](docs/github-app.md) for details.

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Backend (Windows):

```bash
cd backend
mvnw.cmd spring-boot:run
```

Backend (Linux/macOS):

```bash
cd backend
./mvnw spring-boot:run
```

LLM Service:

```bash
cd llm-service
python -m venv .venv
```

Activate the virtual environment, then:

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Default ports:

- Frontend: 5173
- Backend: 8080
- LLM Service: 8000

## 7. Roadmap

See [docs/roadmap.md](docs/roadmap.md).

The next planned stage is Phase 2 - Context Builder.
