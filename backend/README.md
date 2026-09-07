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

Health API plus a stack probe to the LLM service.

```http
GET /api/health
GET /api/stack
```

`/api/health` reports this process only. `/api/stack` also calls `GET {llm-service}/health`.

No database, security, or Git integration.

## Tech Stack

- Java 17+
- Spring Boot 3
- Spring Web
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

GitHub Integration.
