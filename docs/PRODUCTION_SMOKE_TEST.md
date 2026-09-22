# Production smoke-test specification — MS9.8

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR. NOT EXECUTED BY TASKFLOW.**

The [zero-AWS policy](AWS_ARCHITECTURE.md#hard-project-execution-policy) forbids
running this matrix against a public deployment as project validation. It specifies
the final acceptance gate of the [deployment runbook](DEPLOYMENT_RUNBOOK.md), not
a deployment script. [Production readiness](PRODUCTION_READINESS.md) separates
local evidence from the still-unproven live boundary.

## Infrastructure precheck — STOP on failure

Run these checks only after independently authorized deployment gates D1–D6.
Use the operator's real deployment record, never populate credentials or actual
deployment values in Git. A failed or unavailable prerequisite stops smoke acceptance;
do not weaken TLS, Host, health, logging or network controls to continue.

| ID | Operator check | Required result |
| --- | --- | --- |
| P1 | Open the recorded public HTTPS hostname | Correct hostname and intended environment; certificate chain, SAN, validity and browser TLS valid without bypass |
| P2 | Request its HTTP URL | Redirect to HTTPS/443; no application served over HTTP |
| P3 | Inspect ALB target and container health privately | Target healthy via backend-aware probe; frontend/backend healthy, no restart loop. Direct Nginx loopback `/internal/health` correctly returns 404 and is not a substitute probe |
| P4 | Compare running release identities | Both registry manifest digests match the same-commit accepted release record; successful migration version matches the intended schema |
| P5 | Verify observability prerequisites | Correct log groups/permissions, agent running with valid configuration, expected telemetry arriving; alarm dimensions and states reviewed. Missing data is not evidence of health |

## Functional matrix

Original master flow: **open site → register → login → create board → task operations → logout**.
Use a fresh browser profile and two operator-generated dedicated smoke accounts
(A and B), unique non-sensitive Board/Column/Task names and ephemeral passwords.
Never reuse personal credentials. Record results per case as PASS/FAIL/NOT RUN;
NOT RUN is not a production pass. Observe the UI and browser network/console
without exporting sensitive response bodies, cookies or headers.

| ID | Action and current capability | Required result / persistence check |
| --- | --- | --- |
| F1 | Open the public site and registration page | Angular assets load; same-origin `/api`; no unexpected console errors, failed assets or cross-origin requests |
| F2 | Register A through the UI | `POST /api/auth/register` returns 201; authenticated user/state displayed and private Board area available; no unexpected network failure |
| F3 | Logout, attempt one wrong-password login, then correct login | Logout 204; wrong login 401 with safe generic error; correct login 200 and A restored. No high-volume guessing |
| F4 | Inspect cookie attributes without recording values; reload authenticated page | `TASKFLOW_REFRESH`: HttpOnly, Secure, SameSite=Strict, Path=/api/auth. `XSRF-TOKEN`: intentionally readable, Secure, SameSite=Strict, Path=/. Access token stays in memory; reload restores via refresh, with refresh/CSRF rotation and functional unsafe requests |
| F5 | Create, open and rename Board | `POST /api/boards`, GET and PATCH `/api/boards/{id}` succeed; list/detail agree; renamed Board persists after reload |
| F6 | Create two Columns, rename one and reorder with available controls | POST Board Columns, PATCH Column and PUT Board `/columns/order` succeed; complete ordering and names persist after reload |
| F7 | Create two Tasks; open details and edit title, description, priority and due date | Current create/update APIs succeed; updated fields and default/manual ordering persist after reload. Use dates deliberately in the browser's local calendar |
| F8 | Reorder the two Tasks within their Column, then move one to the other Column using pointer drag/drop | Clear search, select ALL Columns, ALL priorities, ALL due dates and MANUAL sort before dragging. PUT `/api/tasks/{id}/placement` persists column/position; reload confirms. Do not infer browser drag geometry from an API-only test |
| F9 | Search by a unique title/description fragment, then clear; apply Column/priority/due filters and Priority: High to Low sort | Search finds the expected Board-scoped Task; filters/sort change the projection without mutating canonical placement. Clear controls to recover full view; data remains after reload |
| F10 | Inspect Board statistics | Total, overdue, priority and Column distributions agree with these controlled Tasks. Columns do not imply completed/open status; no such metric is invented |
| F11 | Register/login B in a separate isolated browser session; test A's known Board and Task identifiers | A's Board is absent from B's list; GET of A's Board/Task returns ownership-safe 404, with no private data. A's state remains unchanged. This is minimal isolation verification, not penetration testing |
| F12 | As A, delete a smoke Task and reload | DELETE succeeds (204); Task disappears, subsequent GET returns 404 and lists/statistics update |
| F13 | Clean up A's remaining test Board and data, then logout both accounts | See cleanup below. Protected UI/state becomes unavailable; unauthenticated GET `/api/boards` and `/api/auth/me` returns 401. Reload must not restore the logged-out refresh session |

Authentication expectations follow the current
[controller](../backend/src/main/java/com/taskflow/auth/api/AuthenticationController.java)
and [frontend session service](../frontend/src/app/features/auth/auth-session.service.ts).
With valid CSRF state, refresh after logout returns 401; missing CSRF may instead
produce 403 and does not prove session revocation. An unsafe request missing the
CSRF header must fail safely; confirm it creates no test Board. Never bypass CSRF.
Logout revokes the presented refresh session and clears frontend access state;
an already-issued Bearer token retains its normal expiry. Do not require immediate
server revocation of that token or capture it for smoke evidence.

## Edge negative matrix

| ID | Controlled request | Expected result |
| --- | --- | --- |
| E1 | HTTP URL | HTTPS redirect as P2 |
| E2 | HTTPS request to the approved TLS hostname with an unapproved HTTP Host | 404, no TaskFlow page/API. Keep TLS SNI/verification on the approved name; do not resolve a second real hostname or disable certificate validation |
| E3 | Public `/actuator`, `/actuator/health`, `/internal/health` and another `/internal/` path | 404, no operational data |
| E4 | Public `/swagger-ui`, `/swagger-ui/index.html`, `/v3/api-docs`, `/v3/api-docs/swagger-config` | 404, no documentation or SPA fallback |
| E5 | Normal HTTPS page/API response | Expected HSTS and other edge security headers; no duplicate contradictory values |

Login/register rate limiting is a separately authorized controlled operational
check, not a mandatory burst during every routine smoke. Do not intentionally
trip alarms or flood public endpoints. [Edge security](EDGE_SECURITY.md) owns
the trust/negative-check contract; do not spoof trusted ALB peers to obtain health.

## Observability and evidence

| ID | Operator confirmation | Expected result |
| --- | --- | --- |
| O1 | Inspect the bounded smoke interval | Representative backend request/event and Nginx access entry exist in their correct log groups. Match UTC/path/status and backend request ID where available; Nginx does not log that request ID today |
| O2 | Inspect host and RDS telemetry | Expected host journal/cloud-init streams, memory/root metrics and native RDS metrics/log export destination exist. Quiet RDS logs need not contain one entry per HTTP request; do not change logging to capture sensitive SQL |
| O3 | Review all eight alarm states and dimensions | No unexplained deployment-induced ALARM or missing telemetry. Allow collection/evaluation time; explain every deviation. Optional external SNS delivery is verified only if configured |

Keep the independent evidence record outside Git: deployment record reference,
UTC timestamp/window, Git SHA, frontend digest, backend digest, migration version,
hostname tested, per-case results, relevant request IDs, redacted alarm/log evidence,
known deviations and operator acceptance/rejection decision. Never include passwords,
JWT values, refresh tokens, Authorization headers, DB credentials or cookie contents.
Do not attach raw HAR exports or screenshots containing those values.

## Cleanup and acceptance

Delete only the Boards/Tasks created by this run, after checking ownership and the
recorded IDs. Board deletion cascades its Columns/Tasks; an individual non-empty
Column cannot be deleted until its Tasks are removed/moved. Logout A and B.
TaskFlow has no account-delete UI/API: retain dedicated smoke accounts under an
operator retention decision, or use a separately reviewed administrative lifecycle
procedure. Do not invent a delete-account endpoint or perform ad-hoc database cleanup.
Never delete production infrastructure, required logs or evidence as smoke cleanup.

Accept only after all mandatory P/F/E/O cases pass and cleanup is recorded. On
failure, withhold acceptance, preserve safe evidence and use the
[deployment failure decisions](DEPLOYMENT_RUNBOOK.md#rollback-and-failure-decisions)
or [incident containment](DISASTER_RECOVERY.md#incident-decision-and-containment).
Do not blindly rollback an image across incompatible schema or restore a database.
This document contains no executed production results.
