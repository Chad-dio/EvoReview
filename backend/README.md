# Backend

Spring Boot backend for EvoReview.

## Responsibility

Future system of record and workflow controller:

- GitHub / GitLab integration
- Webhook
- Repository
- Pull Request
- Git Diff
- Context Builder
- Review workflow
- Feedback
- Rule
- Knowledge
- Evolution
- Replay
- Database
- REST API

## Current Status

Health API, stack probe, and GitHub webhook.

```http
GET /api/health
GET /api/stack
POST /api/github/webhook
```

`/api/health` reports this process only. `/api/stack` also calls LLM `/health` and reports whether GitHub App credentials are present.

`/api/github/webhook` verifies the GitHub signature, reads pull request events, loads changed files, and posts a placeholder comment.

See [../docs/github-app.md](../docs/github-app.md).

No database. No LLM review yet.

## Tech Stack

- Java 17+
- Spring Boot 3
- Spring Web
- GitHub API (App JWT + installation token)
- Maven Wrapper

## Start

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

Default URL: `http://localhost:8080`

Requires JDK 17 or newer. Spring Boot 3 cannot compile or run on JDK 8.

## Next

Context Builder, after webhook + diff + placeholder comment work on a real PR.
