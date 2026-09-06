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

#### Organization and boundaries

The frontend is organized primarily by feature area. Standalone Angular APIs remain the project standard.

The conceptual structure under `frontend/` is:

```text
src/app/
├── core/
├── shared/
├── features/
├── app.config.ts
├── app.routes.ts
├── app.ts
├── app.html
├── app.scss
└── app.spec.ts
```

The `app.*` component files remain at the application root. The `core/`, `shared/`, and `features/` directories are introduced only when real implementation requires them. Do not create empty directories or use `.gitkeep` files just to materialize this structure.

- `core/` contains application-wide technical infrastructure and cross-cutting concerns. Future examples include authentication/session infrastructure, route guards, HTTP interceptors, application configuration adapters, and global error handling. It must not become a generic dumping ground for services or contain feature-specific business behavior.
- `shared/` contains genuinely reusable, domain-agnostic building blocks, such as UI primitives, directives, pipes, pure helpers, or validators needed by more than one feature. It must not contain feature-specific business logic, feature-owned API clients, or application-wide mutable state. Introduce shared abstractions only after concrete reuse exists.
- `features/` contains business capabilities, such as future `auth` and `boards` features. Each feature owns its UI, route configuration, data access/API clients, models/contracts, and feature state. Authentication UI and use cases belong to the `auth` feature; application-wide authentication/session infrastructure belongs to `core/`.

Within a feature, organize code by cohesive sub-feature or use case rather than global technical type. Avoid generic top-level `components/`, `services/`, `models/`, `directives/`, or `pipes/` directories. Keep related component TypeScript, template, SCSS, and spec files together where practical; tests live next to the code under test.

#### Dependency direction

- Features may depend on `core` and `shared`.
- `core` may depend on `shared`, but must not depend on features.
- `shared` must not depend on `core` or features.
- Avoid direct feature-to-feature dependencies. Extract a genuinely shared contract only when required, while respecting the domain-agnostic boundary of `shared/`.

#### Data access and routing ownership

Application-wide HttpClient configuration belongs in root `app.config.ts`, using `provideHttpClient()` as the explicit configuration point for HTTP features. Future application-wide HTTP middleware belongs to `core/http/`; introduce that directory only when real implementation requires it. Interceptors use Angular functional interceptors (`HttpInterceptorFn`) and are introduced only for concrete cross-cutting behavior, never as no-op or pass-through placeholders.

Feature-specific API clients and data-access code remain owned by their feature. Do not create a generic global services directory or generic HTTP wrappers such as `ApiService`, `BaseHttpService`, or `HttpService`.

Root `app.routes.ts` composes top-level application routes. Each feature should own its route configuration when implemented, and future business features should be lazy-loaded where appropriate. These are ownership conventions only: routing implementation belongs to MS3.3 and is not introduced in MS3.2.

#### Environment configuration

Build-specific configuration belongs to `frontend/src/environments/`, using Angular CLI file replacements. `environment.ts` supplies production/default values; the development build replaces it with `environment.development.ts`. These files are client-visible configuration bundled into the application and must never contain secrets, credentials, passwords, or private keys.

Root `app.config.ts` adapts environment values into DI tokens. Feature code does not import environment files directly: future API clients inject `API_BASE_URL`, defined in `core/config/api-base-url.ts`. Both current environments use the relative `/api` base path. A generic configuration service or runtime configuration fetch is not needed for this single value.

The Angular development serve configuration references `frontend/proxy.conf.json`, which forwards `/api/**` to `http://localhost:8080` without rewriting the path. This preserves same-origin frontend requests without development-only backend CORS changes. The proxy target is development tooling configuration only; production hosting must route `/api` to the backend on the frontend's origin.

#### Naming and style

- Use kebab-case filenames with one primary concept per file, retaining role suffixes such as `.routes.ts` and `.spec.ts` where appropriate.
- Colocate `.spec.ts` tests with their source.
- Use names that describe the specific responsibility; avoid vague filenames such as `helpers.ts`, `utils.ts`, or `common.ts`.
- Prefer Angular `inject()` for dependency injection in new code.

#### UI ownership

Global `src/styles.scss` contains application-wide visual and reset foundations only. The root app component owns the minimal application shell: a skip link, application header, and main content landmark containing `RouterOutlet`. Root component styles own shell layout; feature-specific UI and styling stay with their feature.

#### Forms conventions

New TaskFlow forms use Angular Signal Forms from `@angular/forms/signals`. Use a typed `signal()` model, `form()` to create the form field tree, `FormField` to bind controls, and schema-based validation. Use `FormRoot` and Signal Forms submission APIs such as `submit()` where appropriate for the use case.

- Business forms belong to their feature/use case. Keep form models, validation rules, submission behavior, and form-specific UI with that feature, colocating strongly related files. Extract a separate model or schema file only when complexity justifies it.
- Structural client-side validation belongs with the feature form. Prefer Signal Forms validation/schema APIs and clear, user-safe validation messages. Introduce shared validators only after genuine cross-feature reuse exists. Server-side business validation remains authoritative and must also be handled when APIs are implemented.
- Use semantic native form controls whenever practical. Every control has an accessible label; associate validation messages programmatically with the relevant control where applicable. Do not convey invalid state through color alone. Surface errors at useful interaction points, such as touched or submitted state, rather than overwhelming users immediately. Submission must not rely solely on disabled buttons to communicate invalid state.
- Keep form state local to the owning feature unless a concrete requirement proves otherwise; never place mutable form state in `shared/`. When APIs are implemented, the owning feature maps server validation failures to appropriate field or general form errors, rather than delegating this to a generic global form service.
- Classic Reactive Forms are acceptable only when a concrete integration or compatibility requirement justifies them; they are not the default for new forms. Do not mix form paradigms within one form without a concrete reason. Template-driven forms are not a project convention.

Introduce form implementation only for a real use case. Do not create demo forms, fake login/registration forms, a speculative `FormErrorComponent`, generic form services, shared validators, form state stores, `FormBuilder` abstractions, wrapper libraries, or `shared/forms/` placeholders merely to establish the foundation.

#### Frontend testing conventions

Vitest, through the Angular CLI, remains the frontend test runner. Run `npm test` during development and `npm run test:ci` for a single non-watch run from `frontend/`.

- **Location and naming:** Use `.spec.ts` files colocated with the production code they verify. Test organization follows the same feature-oriented structure as source code.
- **Pure unit tests:** Test pure TypeScript logic without `TestBed` when Angular DI, templates, or framework behavior are not required. Do not load Angular infrastructure unnecessarily.
- **Components:** Use Angular `TestBed` when rendering or DI is part of the behavior. Prefer semantic DOM assertions that verify observable behavior and user-facing contracts, not private fields, incidental implementation details, or exact CSS structure.
- **Routing:** Use real Angular route configuration with `RouterTestingHarness` where appropriate. Verify resulting URLs, rendered components, and application behavior. Do not mock Angular Router merely to avoid navigation.
- **Services and dependencies:** Test isolated business/application logic with simple fakes or stubs when dependencies are needed. Use Vitest spies/mocks for interactions only when the interaction itself is part of the contract; avoid excessive mocking that mirrors implementation internals.
- **HTTP clients:** Future feature API client unit/integration tests must not hit a real network. Use `provideHttpClientTesting()` and `HttpTestingController` to assert request method, URL, relevant body/headers, response mapping, and expected failure behavior. Call `HttpTestingController.verify()` after each test to detect unexpected outstanding requests. When a test needs `provideHttpClient(...)` features such as interceptors, register those providers before `provideHttpClientTesting()` so the testing backend takes precedence.
- **Determinism:** Tests must be isolated, repeatable, and deterministic. Do not depend on real network services, machine-specific data, current wall-clock time, uncontrolled random values, or execution order. Control asynchronous behavior explicitly.
- **Scope and coverage:** Test public/observable contracts and meaningful edge cases, not solely line coverage. Do not set an arbitrary coverage percentage at the foundation stage; add a coverage policy when sufficient application code exists.
- **Test levels and infrastructure:** Unit, component, and integration-style Angular tests belong in the frontend unit suite. Defer end-to-end testing until real user workflows exist. Add custom test configuration, global setup, utilities, or additional testing dependencies only for a concrete requirement; do not create fake application functionality solely to establish testing infrastructure.

#### State management

Prefer Angular-native and local state mechanisms, such as signals, when appropriate. Feature state belongs to its feature; `shared/` does not own application-wide mutable state. Do not introduce NgRx or another state-management framework at the foundation stage. Add a dedicated state-management library only when concrete application complexity justifies it.

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

## Authentication architecture (MS4.1)

This section records the approved design for MacroStep 4. Authentication is not implemented by MS4.1; the existing MS1–MS3 conventions above remain authoritative.

### Backend authentication and security boundaries

Authentication is server-authoritative. The backend authenticates requests and enforces authorization; frontend route guards are UX controls only and never replace either responsibility.

MS4.3 introduces an explicit Spring Security filter chain in `shared.security`: `/api/**` requires authentication, `GET /actuator/health` remains public, and other requests retain existing routing behavior. Form login, HTTP Basic, default logout, and saved-request caching are disabled. Authentication is stateless; generated-user auto-configuration is excluded, with no placeholder authentication provider or login session. Until JWT arrives in MS4.4, unauthenticated API GET requests receive a safe `401` ProblemDetail.

CSRF remains enabled. Unsafe requests without a valid CSRF token can receive `403` before authentication checks. The current default CSRF token repository may use a session for CSRF state, not a login session. Before exposing any public unsafe authentication endpoints, complete Spring Security SPA CSRF integration using `CookieCsrfTokenRepository` with Angular's `XSRF-TOKEN` / `X-XSRF-TOKEN` convention. No public auth exceptions or partial SPA flow are introduced in MS4.3.

TaskFlow uses short-lived signed JWT access tokens and longer-lived opaque refresh tokens:

- **Access tokens:** approximately 15 minutes of validity, returned after successful registration, login, and refresh. Angular holds the access token only in application memory and sends it to protected APIs as `Authorization: Bearer <token>`. Never persist it in `localStorage`, `sessionStorage`, or a JavaScript-readable persistent cookie.
- **JWT claims:** `sub` is the stable TaskFlow user UUID. Include only claims actually required by the application. Never include passwords or sensitive/private profile data.
- **JWT infrastructure:** use Spring Security OAuth2 Resource Server/JWT support and Spring Security `JwtEncoder` / `JwtDecoder`. Do not implement a custom JWT authentication filter. Signing keys/secrets are server-side configuration only, must never reach the frontend, and must not be hardcoded production secrets. Concrete cryptographic configuration belongs to MS4.4.
- **Refresh tokens:** opaque, cryptographically random values with approximately 30 days of validity, not bearer access JWTs. Deliver them only through an `HttpOnly` cookie; JavaScript must never read them. Store only a cryptographic hash of each refresh token in the database, with the information needed to validate expiry and revocation.
- **Refresh cookie:** use `SameSite=Strict` and `Secure=true` in production. Localhost development may explicitly allow `Secure=false`. Restrict cookie scope to TaskFlow authentication endpoints where practical, for example with a path of `/api/auth`; clearing the cookie must use matching scope.
- **Rotation and revocation:** rotate refresh tokens on use. Successful rotation invalidates the previous token; validation and replacement must preserve this rule under concurrent requests. Logout revokes the applicable refresh token and clears its cookie.

Normal protected API mutations authenticate through the Authorization bearer header, so their backend authorization remains independent of browser cookies. Refresh/logout endpoints use browser-supplied cookies and require an explicit CSRF/same-site security review during implementation. Neither frontend guards nor the cookie attributes alone replace that review. Preserve the existing same-origin `/api` deployment and development-proxy conventions.

Logout also clears the frontend's in-memory session. With access-token deny lists deferred, revoking a refresh token does not revoke an already issued access JWT: it can remain valid until its short expiry. This is a deliberate boundary of the initial model.

### User identity and credentials

The initial user contains only the UUID primary key inherited from `BaseEntity`, email, password hash, and inherited audit timestamps. Do not add profile fields, roles, permissions, MFA fields, or preferences without a concrete requirement. Existing persistence, auditing, and Flyway conventions apply when user persistence is implemented.

Email normalization is consistent before persistence and authentication: trim and lowercase using locale-independent behavior (for example, Java `Locale.ROOT`). Persisted normalized email is unique, and all authentication lookups use the same normalization. MS4.2 establishes a maximum email length of 254 characters, reflected in the JPA mapping and Flyway schema; future API validation must enforce the same limit.

Passwords:

- Never persist or log plaintext passwords.
- MS4.3 uses Argon2id for new password hashes through Spring Security `DelegatingPasswordEncoder`, with encoding id `argon2id` (stored prefix `{argon2id}`). Parameters are salt length 16 bytes, hash length 32 bytes, parallelism 1, memory 19456 KiB (19 MiB), and iterations 2. Algorithm prefixes preserve future migration options; unknown or missing ids fail without a plaintext/noop fallback. This supersedes the preliminary `PasswordEncoderFactories.createDelegatingPasswordEncoder()` preference: its bcrypt default has a practical 72-byte input limit incompatible with TaskFlow's 128-character policy. Bouncy Castle supplies the Argon2 implementation.
- Accept a minimum of 15 and a maximum of 128 characters, without arbitrary uppercase/lowercase/number/symbol composition requirements.
- Never silently trim or alter password contents. Implementation review must verify that the selected encoder supports the full accepted password range without silent truncation or alteration.
- Validation errors must never echo submitted password values.

### Authentication API contract

The following action-oriented authentication endpoints are an explicit exception to the general plural-resource naming convention. They retain the `/api` prefix, DTO boundaries, validation conventions, and existing error contract. Authenticated-user DTOs expose only safe current-user fields; never serialize persistence entities or password hashes.

| Endpoint | Contract |
| --- | --- |
| `POST /api/auth/register` | Validate email/password, create the user, and automatically establish authentication. Return the authenticated user plus an access token and set the refresh-token HttpOnly cookie. Duplicate normalized email returns `409 Conflict` through the existing API error contract. |
| `POST /api/auth/login` | Authenticate normalized email/password, return the authenticated user plus an access token, and set/rotate the refresh cookie as appropriate. An unknown email and an incorrect password produce the same generic `401 Unauthorized` response; never identify which credential was wrong. |
| `POST /api/auth/refresh` | Read the HttpOnly refresh cookie and validate its stored hash, expiry, and revocation state. Rotate the refresh token, return a new access token and authenticated-user/session information as appropriate, and set the replacement cookie. Missing, invalid, expired, or revoked tokens produce a safe `401 Unauthorized` authentication failure. POST only. |
| `POST /api/auth/logout` | Revoke the presented refresh token when available and clear its cookie. Safe and idempotent from the client's perspective, including when no usable refresh token remains. Successful logout returns `204 No Content`. POST only. |
| `GET /api/auth/me` | Require a valid bearer access token and return the authenticated user's safe public/current-user representation. Never return password hashes or token material. |

Registration and login do not require an existing access token. Refresh and logout use the refresh-cookie contract rather than requiring a still-valid access JWT. These boundaries do not exempt cookie endpoints from the CSRF/same-site review above. Malformed or structurally invalid requests remain validation failures; the generic login `401` applies to credential authentication failures.

### Authentication error contract

Authentication and security failures remain compatible with TaskFlow's RFC 9457 `ProblemDetail` contract: `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`, and a stable application `code`, plus safe structured violations where applicable. MS4.3 security authentication/denial handling preserves this contract for filter-chain failures through a custom authentication entry point (`AUTHENTICATION_REQUIRED`, 401) and access denied handler (`ACCESS_DENIED`, 403). Both use the same shared ProblemDetail construction as controller advice and serialize with the application-configured Jackson `ObjectMapper`, without exposing exception details.

| Status | Meaning |
| --- | --- |
| `400 Bad Request` | Malformed request or validation failure. |
| `401 Unauthorized` | Authentication required, invalid credentials, or missing/invalid/expired/revoked authentication token as applicable. |
| `403 Forbidden` | Authenticated caller is forbidden from the requested operation. |
| `409 Conflict` | Registration conflicts with an existing normalized email. |
| `500 Internal Server Error` | Unexpected server failure with a safe public response. |

In particular, `401` and `403` responses must not leak stack traces, token parsing details, internal security classes, password hashes, signing information, or sensitive credential values. Invalid email and invalid password authentication failures share the same safe login response. The approved registration `409` does disclose an email conflict; generic login errors reduce enumeration through login but do not remove that registration disclosure.

### Angular authentication and session architecture

Authentication is a business feature owned by `frontend/src/app/features/auth/`. When implemented, it owns login/register UI, the auth API client, auth-specific request/response models, feature route configuration, and auth-specific state/use cases.

Application-wide auth/session infrastructure may live in `core/` only when actually required, such as the bearer-token HTTP interceptor, authentication route guard, or session bootstrap integration. Preserve the existing dependency direction: `core` must not import the auth feature. Root application composition can connect feature-owned authentication behavior with core infrastructure through narrowly scoped contracts when needed; do not move feature business behavior into `core` or mutable session state into `shared`.

Session restoration follows this lifecycle:

1. Start in an `initializing` state. A page reload loses the memory-only access token.
2. Call `POST /api/auth/refresh`; the browser sends the HttpOnly refresh cookie without JavaScript reading it.
3. A successful refresh repopulates the in-memory access token and current user and establishes the `authenticated` state.
4. A failed refresh establishes an `anonymous` state. Route decisions wait for refresh/bootstrap to complete instead of treating initialization as anonymous authentication.

At least these three states must be distinguishable conceptually: `initializing`, `authenticated`, and `anonymous`. Access JWT storage remains memory-only: never `localStorage`, `sessionStorage`, or a JavaScript-readable persistent cookie.

The future functional HTTP interceptor adds `Authorization: Bearer <access token>` only to TaskFlow API requests. Match the intended API origin and path boundary so tokens cannot leak to unrelated/external URLs. Login/register/refresh behavior must avoid inappropriate bearer attachment where required, including during session restoration. Feature API clients continue to use the injected `API_BASE_URL` convention.

Login/register routes belong to the auth feature. Protected application routes use functional Angular route guards. Unauthenticated navigation redirects to login and should preserve an appropriate application-local return URL, validated before navigation to avoid external redirects. Guards remain UX controls, never security boundaries.

Login/register forms follow the existing Angular Signal Forms conventions. They may provide structural validation for UX but must not duplicate backend-only business/security decisions. Surface server validation and authentication failures safely as appropriate field or general form errors, without exposing credentials or internal failure details.

### Deferred scope and implementation sequence

The following are explicitly deferred until concrete requirements justify them:

- MFA.
- Password reset and email verification.
- OAuth/social login.
- Roles/admin authorization.
- Remember-me variants.
- Account lockout/rate limiting implementation.
- Breached-password API integration.
- Multi-device session management UI.
- Access-token deny lists.
- Authorization/ownership for Board/Task resources until their features exist.

The planned MS4 sequence is user persistence → Spring Security → JWT → auth endpoints → refresh/logout → current user/backend authorization → Angular auth → auth UI/guards → final verification. Concrete JWT cryptographic configuration is implemented in MS4.4.

MS4.1 is documentation only. It introduces no Spring Security dependencies, application/configuration changes, user entities, migrations, endpoints, JWT code, or frontend auth directories/components/services. Implementation starts in subsequent steps; this decision does not start MS4.2 or change the roadmap.

## Repository boundaries

- `docs/` records architecture decisions and project direction.
- `.github/workflows/` is reserved for continuous integration and delivery automation.
- `scripts/` holds repeatable development and operational automation.
- `.agents/` holds repository context and guidance for AI-assisted work.
