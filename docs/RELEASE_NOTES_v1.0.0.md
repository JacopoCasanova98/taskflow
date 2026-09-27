# TaskFlow v1.0.0

Prepared release body: tagging and GitHub Release publication remain pending the
final merge and successful CI on `main`.

TaskFlow v1.0.0 marks the first complete portfolio release of the application:
a production-oriented Kanban platform with a live non-production demo and a
reviewable engineering baseline. This release is not a production deployment claim.

## Highlights

- Spring Boot backend, Angular frontend, and PostgreSQL persistence supporting
  the Board / Column / Task workflow and authentication.
- Dockerized local runtime and GitHub Actions quality/build gates.
- Terraform and CloudFormation reference infrastructure, architecture diagrams,
  and production-oriented deployment and recovery documentation.
- A working public portfolio demo on free Render and Neon infrastructure.

## Product capabilities

- Registration, login, logout, and private user-owned boards, columns, and tasks.
- Task creation, editing, deletion, drag-and-drop movement, and ordering.
- Priorities, due dates, text search, combined filtering, and sorting.
- Board statistics with task totals, overdue counts, and priority/status distributions.

## Engineering

Java 21 and Spring Boot provide feature-oriented API, application, and persistence
boundaries. PostgreSQL and Flyway manage persisted state and schema migrations;
Hibernate validates the schema. Angular uses standalone components and signals.

Security includes bearer access tokens, rotating HttpOnly refresh cookies
(Secure on HTTPS deployments), CSRF protection, and server-side ownership checks.
Input validation, consistent Problem Detail errors, OpenAPI/Swagger documentation,
and request-correlated logging support API maintenance and diagnosis.

Backend unit/MVC and PostgreSQL Testcontainers integration tests complement
Angular/Vitest tests. Coverage gates, SpotBugs/FindSecBugs, formatting, and linting
support the quality workflow. Multi-stage, non-root Docker images and Compose
provide the local runtime. GitHub Actions validates both applications and both
Docker images; the required `ci-gate` and repository rules protect `main`.

## Live demo

[Try TaskFlow](https://taskflow-demo-frontend-bod0.onrender.com)

The demo is **non-production**: Angular runs on a Render Static Site, the Spring
Boot Docker backend runs on Render Free, and PostgreSQL 17 runs on Neon Free.
The backend may cold-start after inactivity; no keep-alive workaround is used.
Use disposable, non-sensitive data and do not enter personal information.

## Infrastructure and deployment

Terraform and CloudFormation implement the separate AWS reference architecture.
**AWS infrastructure is intentionally unprovisioned.** ECR publication and
production deployment contracts are designed and statically validated; the
deployment planner operates offline and performs no live deployment.

Current CI does **not** push to ECR or deploy to AWS, and requires no AWS
credentials. Demo deployments remain manual. Creating this GitHub Release does
not publish container images, change provider configuration, or deploy infrastructure.

## Verification

Repository evidence records automated backend/frontend suites, Docker build and
runtime-image validation, the aggregate CI gate, static/security checks, and
architecture/delivery contract validation. The final release commit must pass
CI on `main` before tagging; historical results do not substitute for that gate.

The recorded live demo smoke on 2026-09-27 validated HTTPS loading,
registration/logout/login, board and column creation, task creation/editing/deletion,
persistence after refresh, and logout followed by refresh remaining unauthenticated.
That evidence applies to deployed commit
`797a3e4e3ee815ed11380ad751da6c1c420afb00`; it does not assert that the eventual
release commit has been deployed. Detailed evidence and boundaries are maintained
in `docs/PORTFOLIO_DEMO.md`, `docs/CI_CD.md`, and `docs/ROADMAP.md`.

## Known limitations

- Free-tier sleeping, quotas, and provider availability affect the demo; no
  production SLA or high-availability guarantee is offered.
- Demo data is disposable and may be reset.
- AWS infrastructure remains reference-only, with no live AWS acceptance claim.
- Natural cold-start-after-sleep and live two-account isolation checks are not
  recorded as performed; automated security tests are separate evidence.
- Screenshots and portfolio image assets are deferred/optional. Real screenshots
  may be added manually later; they are not a technical completion requirement.
