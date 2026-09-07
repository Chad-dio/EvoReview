# Frontend

Vue 3 frontend for EvoReview.

## Responsibility

Future UI for:

- Dashboard
- Repositories
- Pull Requests
- Review Findings
- Rules
- Evolution
- Replay

## Current Status

Landing page only. No dashboard, routing, login, or review screens.

The page calls `/api/stack` through the Vite proxy (`/api` → `http://localhost:8080`).

## Tech Stack

- Vue 3
- TypeScript
- Vite 4 (compatible with the current Node.js 16 environment)

## Start

```bash
cd frontend
npm install
npm run dev
```

Default URL: `http://localhost:5173`

## Next

Add application shell and repository views after Git Integration exists.
