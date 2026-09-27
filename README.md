# TaskFlow

Production-oriented full-stack task management platform.

[🚀 Live Demo](https://taskflow-demo-frontend-bod0.onrender.com) · [Architecture](docs/ARCHITECTURE.md) · [Documentation](docs/)

**Java 21 / Spring Boot · Angular · PostgreSQL · Docker · GitHub Actions**

Terraform · CloudFormation · AWS reference architecture

TaskFlow is a Kanban application for organizing personal work into boards, columns,
and tasks. Built as an engineering portfolio project, it combines a feature-oriented
backend and Angular frontend with authentication, database migrations, automated
testing, containers, and CI. The repository also includes infrastructure-as-code
and deployment/recovery designs, extending the work beyond CRUD endpoints.

## Live demo

**[Try TaskFlow](https://taskflow-demo-frontend-bod0.onrender.com)** — register an
account to explore the application.

The public demo is **non-production**, intentionally hosted on free Render and
Neon infrastructure. The Render Free backend may sleep after inactivity, so the
first request can take longer; no keep-alive workaround is used. Treat demo data
as disposable and do not enter sensitive or personal information.

Registration, login/logout, board/column creation, task creation/editing/deletion,
and persistence after refresh were manually validated on 2026-09-27. See the
[portfolio demo record](docs/PORTFOLIO_DEMO.md) for deployment details, evidence,
and remaining live checks.

## Product capabilities

- **Private workspaces:** registration, login, logout, and user-owned boards,
  columns, and tasks with server-side ownership checks.
- **Kanban workflow:** create and manage boards and columns; create, edit, delete,
  reorder, and move tasks between columns using drag-and-drop.
- **Task planning:** priorities, due dates, text search, combined filters, and
  sorting to focus the board view.
- **Board dashboard:** task totals, overdue counts, and priority/status distributions.

## Architecture

[View architecture diagrams](docs/DIAGRAMS.md)

The backend is a feature-oriented modular monolith with separate API, application,
domain, and persistence responsibilities. The Angular frontend uses standalone
components, signals, and feature-owned API clients. Both communicate through
same-origin `/api` routes. See [application architecture](docs/ARCHITECTURE.md).

### Portfolio demo — live, non-production

The browser loads Angular from a **Render Static Site**. A manual same-origin
`/api` rewrite forwards requests to the **Spring Boot Docker backend on Render
Free**, which connects over TLS to **PostgreSQL 17 on Neon Free**. The backend and
database are in Frankfurt. This deployment is independent of the AWS design.

### AWS reference architecture — not provisioned

The separate production-oriented reference design uses Route 53 DNS and ACM TLS,
an internet-facing ALB, an EC2 Docker application host, and private RDS PostgreSQL
17. ECR, Secrets Manager, CloudWatch, and SSM cover image delivery, secrets,
observability, and operator access.

**AWS infrastructure is intentionally not provisioned by this project.**
[Terraform](infra/terraform/) and [CloudFormation](infra/cloudformation/) contain
implemented reference definitions with documented parity and static validation;
they are not evidence of an AWS deployment. See the
[AWS architecture](docs/AWS_ARCHITECTURE.md) for boundaries and tradeoffs.

## Engineering highlights

- **Security and API contracts:** Spring Security bearer access tokens, rotating
  HttpOnly refresh cookies (Secure on HTTPS deployments), CSRF protection, and
  ownership enforcement. Bean Validation and RFC 9457 Problem Detail responses
  provide consistent error handling; SpringDoc supplies OpenAPI/Swagger documentation.
- **Persistence and correctness:** UUID domain identities, PostgreSQL constraints,
  Flyway migrations, and Hibernate schema validation. Unit/MVC tests and real
  PostgreSQL Testcontainers integration tests exercise behavior and isolation.
- **Frontend quality:** Angular component, routing, and HTTP tests with Vitest;
  formatting, linting, coverage gates, and a production build are part of validation.
- **Delivery and operations:** multi-stage, non-root Docker images; Compose for
  local development; request-correlated logging; and documented deployment,
  recovery, and operational runbooks for the AWS reference design.

## Technology stack

| Area | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA, Maven |
| Frontend | Angular, TypeScript, signals, Angular CDK |
| Database | PostgreSQL 17 |
| Migrations | Flyway; Hibernate schema validation |
| Testing and analysis | JUnit, Mockito, Testcontainers, Vitest, JaCoCo, SpotBugs/FindSecBugs |
| Containers | Docker, Docker Compose, Nginx |
| CI/CD | GitHub Actions quality/build gates; manual demo deployment |
| Infrastructure as Code | Terraform and CloudFormation reference implementations |
| Cloud reference | AWS ALB, EC2, RDS, ECR, Secrets Manager, CloudWatch, SSM |
| Demo hosting | Render Static Site + Render Free Web Service + Neon Free |

## Run locally

Clone the repository and open its root directory. Install Docker Desktop or
Docker Engine with Compose v2 supporting `--wait`, plus Bash and `openssl`
(WSL/Git Bash on Windows).

```bash
./scripts/setup-local-env.sh
docker compose up -d --wait
```

Open **http://localhost:8080**. Setup generates a private, ignored `.env` and
preserves it on subsequent runs. Flyway applies migrations automatically;
PostgreSQL data persists in a Docker volume. After source changes, rebuild with
`docker compose up -d --build --wait`.

`docker compose down` stops the stack and preserves data.
**`docker compose down -v` deletes the database volume and all local data.**
See [local development](docs/LOCAL_DEVELOPMENT.md) for configuration and troubleshooting.

## Quality and CI/CD

[GitHub Actions](.github/workflows/ci.yml) validates the backend with Maven tests,
coverage checks, and SpotBugs/FindSecBugs; validates frontend formatting, lint,
tests, coverage, and the production build; then builds and inspects both Docker
images. The required `ci-gate` aggregates those results. Repository rules protect
`main` with pull requests and an up-to-date passing gate.

CI does not publish images or deploy infrastructure. Demo deployments are manual,
with auto-deploy off; their temporary feature-branch source must return to `main`
after MacroStep 10 merges. See [CI/CD](docs/CI_CD.md) and
[repository governance](docs/REPOSITORY_GOVERNANCE.md).

## Repository structure

```text
backend/     Spring Boot API, migrations, and tests
frontend/    Angular application and tests
infra/       Terraform and CloudFormation reference infrastructure
ops/         Production-reference configuration and operational contracts
scripts/     Development helpers and offline contract validation
.github/     CI workflow and pull request template
docs/        Architecture, decisions, runbooks, and roadmap
```

## Documentation

| Start here | Purpose |
| --- | --- |
| [Application architecture](docs/ARCHITECTURE.md) | Design boundaries and implementation decisions |
| [Portfolio demo](docs/PORTFOLIO_DEMO.md) | Live deployment, smoke evidence, and free-tier limitations |
| [Local development](docs/LOCAL_DEVELOPMENT.md) | Setup, migrations, logs, and troubleshooting |
| [AWS reference architecture](docs/AWS_ARCHITECTURE.md) | Non-provisioned cloud design and IaC scope |
| [CI/CD](docs/CI_CD.md) | Quality gates and delivery-design boundaries |
| [Roadmap](docs/ROADMAP.md) | Completed milestones and remaining work |
