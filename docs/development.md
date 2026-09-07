# Local Development

Phase 0 services can be started independently.

Default ports:

- Frontend: `5173`
- Backend: `8080`
- LLM Service: `8000`

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

Vite proxies `/api` to `http://localhost:8080`, so the landing page can read Backend and LLM status without calling the Python service directly.

## Backend

Requires JDK 17 or newer. Spring Boot 3 cannot compile or run on JDK 8.

Windows:

```bash
cd backend
mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
cd backend
./mvnw spring-boot:run
```

Health check: `http://localhost:8080/api/health`

Stack check (Backend + LLM + GitHub config): `http://localhost:8080/api/stack`

GitHub webhook (local via smee): `POST http://localhost:8080/api/github/webhook`

Setup: [github-app.md](github-app.md)

## LLM Service

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

Health check: `http://localhost:8000/health`

On this machine, use `python` if `python3` is not available.
