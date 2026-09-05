# TaskFlow Architecture

## Overview

TaskFlow is a full-stack application organized as a single repository. It separates the backend, frontend, infrastructure, and project documentation so each concern can evolve independently while remaining coordinated.

## Components and responsibilities

### Backend

The `backend/` directory contains the Java 21 and Spring Boot application. It is responsible for server-side business behavior, API delivery, data access, and integration with the PostgreSQL database.

#### Package conventions

- Backend code is organized primarily by feature. Each feature package owns its entity, repository, service, controller, DTOs, and mappers as needed.
- Controllers handle HTTP/API concerns and request validation. Services contain use cases and business orchestration. Repositories handle persistence access only. Entities represent persisted state, DTOs represent API contracts, and mappers translate between them.
- Feature packages are introduced only when their corresponding feature is implemented. Do not create empty feature packages or generic global `controller`, `service`, `repository`, `entity`, `dto`, or `mapper` packages.
- `shared` is reserved exclusively for genuinely cross-feature technical concerns. `shared.persistence` contains the persistence foundation; `shared.config`, `shared.error`, and `shared.web` are reserved for cross-cutting configuration, error handling, and HTTP/API behavior respectively.

#### Validation conventions

- TaskFlow uses Jakarta Bean Validation for HTTP request validation. Feature request DTOs own structural input constraints such as `@NotBlank`, `@Size`, and `@Pattern`; controllers invoke validation at the HTTP boundary with `@Valid`.
- Services enforce business rules, including uniqueness, existence, authorization, state transitions, and cross-record rules. Custom validators are introduced only when a concrete feature requires one.
- Validation failures return HTTP 400 Bad Request. The API error contract is client-safe and supports field-level errors where applicable.
- API errors include an HTTP status, stable error code, concise summary/message, request path, and applicable structured violations. They never expose stack traces, exception class names, SQL/database details, internal implementation details, or submitted sensitive values.
- Centralized exception translation is provided by `@RestControllerAdvice` in `shared.error`. It maps validation and bad-request failures to 400, missing resources to 404, conflicts to 409, and unexpected exceptions to 500.

#### Testing conventions

- Tests mirror the corresponding production package structure under `backend/src/test/java`. For example, tests for `com.taskflow.shared.error` belong in `com.taskflow.shared.error`.
- Unit tests verify isolated logic without loading the Spring application context. MVC tests use Spring MVC test support for HTTP behavior, controller boundaries, validation, exception handling, and JSON/API contracts. Integration or context tests load Spring only when application wiring or component integration requires it.
- Test classes use the `FooTest` name when their type is clear from context. Suffixes such as `FooMvcTest` and `FooIntegrationTest` are used only when they add useful clarity.
- Tests verify observable behavior and contracts rather than incidental implementation details. They must remain deterministic, isolated, and repeatable.
- `BackendApplicationTests` verifies application wiring and the Actuator health contract with the database-free `test` profile. This suite does not verify a live PostgreSQL connection, Flyway execution, or persistence round trips. Run the backend suite with `cd backend && ./mvnw test`.

The backend is a feature-oriented modular monolith. Each future feature is organized under `com.taskflow.<feature>` and may contain `api`, `application`, `domain`, and `persistence` packages. Public HTTP DTOs live in `<feature>.api.dto`; JPA entities are persistence details and must not be exposed by controllers.

Cross-cutting conventions live in `com.taskflow.shared`:

- `shared.web` defines reusable HTTP API conventions, including the `/api` endpoint prefix.
- `shared.error` provides RFC 9457 Problem Detail responses, including structured validation violations and stable application error codes.
- Mappers belong to their feature. MapStruct and shared mapper configuration are deferred until a concrete feature requires them.
- `shared.config` is reserved for narrowly scoped Spring configuration shared by more than one feature.

Future REST controllers use plural resources below `/api`, return resource DTOs directly for successful single-resource responses, and rely on standard HTTP status codes: `200 OK` for successful reads and updates, `201 Created` for creations, and `204 No Content` for successful deletions. Errors are returned as `application/problem+json`; validation and malformed requests use `400 Bad Request`, missing resources use `404 Not Found`, conflicts use `409 Conflict`, and unexpected failures use `500 Internal Server Error`. Problem responses expose `type`, `title`, `status`, `detail`, `instance`, and a stable application `code`. Validation failures additionally expose a `violations` array with `field`, `message`, and `code` values. Expected API failures use `ApiException` with a client-safe message and stable code; validation messages must never interpolate sensitive submitted values. Controllers must use `@Valid` for request-body validation; application services enforce business rules and own transaction boundaries. Spring Boot Actuator exposes health at `/actuator/health` only.

### Frontend

The `frontend/` directory contains the Angular application. It is responsible for the user-facing experience and communication with the backend through its exposed API.

### Database

PostgreSQL is the application's persistent data store. Its use, local configuration, and related operational assets are kept separate from application source code.

#### Persistence conventions

- Persistent entities extend `BaseEntity` (`com.taskflow.shared.persistence`), which provides an application-generated random UUID primary key and `createdAt` / `updatedAt` audit fields.
- Audit values use `Instant`; the application clock, JSON configuration, and Hibernate JDBC timezone are all UTC. Database timestamp columns must use `TIMESTAMP WITH TIME ZONE` (`timestamptz`).
- JPA auditing is enabled by default. The database-free `test` profile disables it because that profile intentionally has no JPA metamodel.
- Hibernate validates mappings and never creates or updates the schema. Schema changes are introduced only through Flyway migrations in `backend/src/main/resources/db/migration`.
- Migration files use Flyway's versioned format: `V<version>__<description>.sql` (for example, `V1__create_users.sql`). Versions are ordered numerically; descriptions use lowercase words separated by underscores. Existing migrations are immutable.
- Flyway validates migration file names and has cleaning disabled in every application environment.

### Infrastructure

The `infra/` directory contains infrastructure definitions. Docker assets support local containerized development and execution. Directories for Terraform and CloudFormation are reserved for infrastructure definitions when those tools are adopted.

## Repository boundaries

- `docs/` records architecture decisions and project direction.
- `.github/workflows/` is reserved for continuous integration and delivery automation.
- `scripts/` holds repeatable development and operational automation.
- `.agents/` holds repository context and guidance for AI-assisted work.
