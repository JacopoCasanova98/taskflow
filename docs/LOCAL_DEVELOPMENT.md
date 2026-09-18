# Local development

Run lifecycle commands from the repository root with Docker running. Prerequisites
are Docker Desktop or Engine, a current Compose v2+ plugin supporting `--wait`,
Git, Bash and `openssl`. Windows developers can use WSL or Git Bash.
Java/Node are supplied by the Docker build stages.

## Setup and startup

```bash
./scripts/setup-local-env.sh
docker compose up -d --wait
docker compose ps
```

Open http://localhost:8080. Expect frontend, backend and postgres to be healthy.
Compose builds missing application images; an existing image is not automatically
rebuilt when source changes. Use this after changing source or migrations:

```bash
docker compose up -d --build --wait
```

Normal subsequent startup is `docker compose up -d --wait`. For attached logs,
use `docker compose up`; Ctrl+C stops the attached stack. Native Compose owns
the lifecycle; the setup helper only creates configuration.

## Environment

The setup script locates the repository from its own path, generates a random
DB password and a Base64 JWT key decoding to exactly 32 bytes, and creates root
`.env` with mode 0600. It does not print secrets or overwrite an existing `.env`.
The generated variables are `TASKFLOW_DB_PASSWORD` and
`TASKFLOW_JWT_SECRET_BASE64`. No manual shell export is needed: Compose reads
`.env` automatically. Shell exports override `.env`, so remove stale overrides
if troubleshooting unexpected configuration. Validate without revealing values:

```bash
docker compose config --quiet
```

Do not commit/share `.env` or run secret-bearing config output in shared logs.
The file is already ignored. There is no copyable `.env.example`; generation is
the recommended setup. Docker runtime environment values remain inspectable by
local Docker users. This is a local setup, not production secret management.

If port 8080 is occupied, add `TASKFLOW_HTTP_PORT=8081` to `.env`, run
`docker compose up -d --wait`, then open http://localhost:8081. Do not edit Compose
for machine-specific ports. Only frontend binds host loopback; HTTP APIs use
`http://localhost:<port>/api/...`. Backend and PostgreSQL have no host ports.
Local HTTP uses `TASKFLOW_COOKIE_SECURE=false`; deployed HTTPS requires Secure
cookies. Swagger UI/OpenAPI paths are not proxied by the current frontend;
they are not available through the host frontend URL. Backend API documentation
remains available internally; no additional proxy or port is introduced here.

## Shutdown and deliberate reset

```bash
docker compose down
```

Normal shutdown preserves PostgreSQL data and `.env`. Start again normally.

**Destructive: the following deletes the project's PostgreSQL volume and all
local users, Boards and Tasks.** Keep `.env` to reuse its credentials:

```bash
docker compose down -v
docker compose up -d --wait
```

Only for a completely new database AND new credentials, deliberately use:

```bash
docker compose down -v
rm .env
./scripts/setup-local-env.sh
docker compose up -d --wait
```

The volume and `.env` normally stay paired. Changing `POSTGRES_PASSWORD` through
configuration does not rotate the password inside an initialized database.
Regenerating credentials against the old volume can therefore break login from
the backend; this full reset is not a routine rotation workflow.

## Logs and troubleshooting

```bash
docker compose logs -f
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
docker compose logs --tail=100 backend
```

Start diagnosis with `docker compose ps`. If backend is unhealthy, check
PostgreSQL health and `docker compose logs postgres`, then backend logs. If
frontend is unhealthy, check backend health and frontend logs. A missing required
variable means setup/configuration needs attention; the helper preserves existing
files, including incomplete ones, so inspect variable names locally without
sharing values. Database password mismatch often indicates a changed `.env`
paired with an older volume. Restore matching credentials or deliberately reset
disposable data. Dependency restarts can cause brief downtime; this is not HA.

## Database and migrations

Flyway runs automatically when backend starts, using
`backend/src/main/resources/db/migration/`. Hibernate then validates the schema.
Do not manually apply schema SQL during normal startup. Inspect current history:

```bash
docker compose exec postgres \
  psql -U taskflow -d taskflow \
  -c 'SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;'
```

For an interactive database shell:

```bash
docker compose exec postgres psql -U taskflow -d taskflow
```

Never edit an already-applied migration to change schema. Add the next forward
migration following `V<number>__meaningful_description.sql` (currently the next
would be V7), then rebuild/start with `docker compose up -d --build --wait`.
Flyway applies it at backend startup. No migration sidecar or host DB port is
needed. A local persistent volume is not a backup.
