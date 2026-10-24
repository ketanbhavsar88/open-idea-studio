# Local development

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| Docker + Compose | 24+ / v2+ | Runs PostgreSQL, and Maven if you have no JDK |
| Node.js | 22+ (tested on 24) | Frontend |
| JDK | 25 (optional) | `scripts/api.sh` falls back to a `maven:3.9.11-eclipse-temurin-25` container |

## 1. Configuration

```bash
cp infra/.env.example infra/.env   # set POSTGRES_PASSWORD and GEMINI_API_KEY (see ai-providers.md)
```

`infra/.env` is read by Docker Compose **and** by the API (via `spring.config.import`), so there is one place to configure everything. Real environment variables override it. It is gitignored; never commit it.

> If you change `POSTGRES_PASSWORD` after the database volume was first created, Postgres keeps the old password. Either run `ALTER ROLE` inside the container or remove the volume (`docker compose down -v`, which **deletes all local project data**).

No API key yet? Set `AI_PROVIDER=fake` to click through discovery with canned replies.

## 2. Start the database

```bash
cd infra && docker compose up -d
```

Flyway creates the schema when the API starts. The init script in `infra/postgres/init` only enables extensions.

## 3. Start the API (port 8080)

```bash
scripts/api.sh spring-boot:run
curl localhost:8080/actuator/health   # {"status":"UP",...}
```

## 4. Start the web app (port 5173)

```bash
cd apps/web
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` and `/actuator` to `http://localhost:8080` (override with `API_URL`), so the browser never talks to the API or any model provider directly.

## Tests

```bash
scripts/api.sh verify          # API unit + integration tests (Testcontainers, needs Docker)
cd apps/web && npm test        # frontend tests (Vitest + Testing Library)
cd apps/web && npm run lint    # oxlint
cd apps/web && npm run build   # type check + production build
```

Integration tests start their own throwaway `postgres:17-alpine` container; they never touch the `infra/` database.
