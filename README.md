# TaskFlow

TaskFlow is a production-quality full-stack application for managing work and tasks.

## Technology stack

- Backend: Java 21, Spring Boot, Maven
- Frontend: Angular
- Database: PostgreSQL
- Local infrastructure: Docker and Docker Compose

## Repository structure

```text
backend/     Spring Boot application
frontend/    Angular application
infra/       Docker and future infrastructure definitions
docs/        Architecture, roadmap, and decisions
scripts/     Development and operational automation
```

## Local development with Docker

Clone the repository and open its root directory. Install Docker Desktop or
Docker Engine with a current Compose v2+ plugin supporting `--wait`, plus Bash
and `openssl` for the one-time setup (WSL/Git Bash on Windows).

```bash
./scripts/setup-local-env.sh
docker compose up -d --wait
```

Open http://localhost:8080. The setup creates an ignored, private `.env` with
random credentials; running it again preserves the existing file. Future starts
need only `docker compose up -d --wait`. First startup builds missing images;
after source changes use `docker compose up -d --build --wait`.

Use `docker compose ps` for health, `docker compose logs -f` for logs, and
`docker compose down` to stop while preserving database data. Foreground
`docker compose up` is also supported.

**Destructive database reset:** `docker compose down -v` deletes the local
PostgreSQL volume and all its data. Start again with `docker compose up -d --wait`.
Flyway applies migrations automatically during backend startup; no manual schema
setup is needed.

See [Local development](docs/LOCAL_DEVELOPMENT.md) for environment configuration,
port conflicts, service logs, migrations and reset details. See
[the architecture overview](docs/ARCHITECTURE.md) and [the roadmap](docs/ROADMAP.md)
for project direction.
