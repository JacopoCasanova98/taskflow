# TaskFlow Roadmap

This roadmap tracks the project at a macro level. Detailed scope and implementation decisions are introduced only when they are approved.

## MacroStep 1 — Foundation

- [x] Repository initialization
- [x] Project structure creation
- [x] Project documentation baseline

## MacroStep 2 — Backend foundation

- [x] Establish backend foundations

## MacroStep 3 — Frontend foundation

- [x] Establish frontend foundations

## MacroStep 4 — Authentication

- [x] Establish authentication

MS4.10 closes the implemented authentication scope after final backend/frontend tests, production build, and security/contract review. PostgreSQL integration/concurrency and real browser verification remain explicit later verification boundaries; see `ARCHITECTURE.md`.

Roadmap refinement: authentication completes the typed UUID identity foundation. Concrete private-resource ownership moves to the first real Board/Task resources in MS5, with cross-user access tests; no fake domain resource was created during MS4.

## MACROSTEP 5 — Core TaskFlow Features ✅

**MS5.1–MS5.16 complete.** MS5.16 closes the roadmap functional flow with one routed frontend acceptance test using real application code and mocked HTTP. All 377 backend tests and 586 frontend tests pass; the production frontend build passes without warnings. No production fix was needed. The Definition of Done—TaskFlow is usable as a complete task manager—is satisfied within the documented automated acceptance boundaries; live PostgreSQL/Flyway, browser pointer/cookie integration, and deployed smoke verification remain later work. See [ARCHITECTURE.md](ARCHITECTURE.md#functional-acceptance-and-macrostep-5-close-out-ms516).

Next: **MACROSTEP 6 — SOFTWARE QUALITY**. MS6.1 close-out is recorded below.

Feature history: MS5.1 domain design, MS5.2 Board backend, and MS5.3 Column backend are complete. MS5.4 Task backend implements V6, owner-scoped CRUD, basic priority/due-date persistence, full PUT content replacement, same-Board placement/resequencing, and COLUMN_NOT_EMPTY enforcement using the shared Board mutation lock; see [ARCHITECTURE.md](ARCHITECTURE.md#task-backend-ms54). MS5.4 is complete with 342 green backend tests (128 added). MS5.5 Board list frontend is implemented: feature-owned typed API and signal state, guarded `/boards` landing and root redirect, create/rename Signal Forms, explicit delete confirmation, and loading/empty/error/retry UX. MS5.5 is complete with 151 green frontend tests and a successful production build; see [ARCHITECTURE.md](ARCHITECTURE.md#board-list-frontend-ms55). MS5.6 read-only Board workspace is implemented: guarded reactive route, Board → Columns → parallel Tasks loading, server ordering, cancellation, loading/not-found/error/Retry states, responsive Kanban lanes, and minimal task cards. MS5.6 is complete with 173 green frontend tests and a successful production build; see [ARCHITECTURE.md](ARCHITECTURE.md#board-workspace-frontend-ms56). MS5.7 Column UI is implemented with create/rename Signal Forms, confirmed deletion and COLUMN_NOT_EMPTY feedback, pessimistic complete-order keyboard reorder, and generation-safe mutation reconciliation. MS5.7 is complete with 226 green frontend tests and a successful production build; see [ARCHITECTURE.md](ARCHITECTURE.md#column-ui-ms57). MS5.8 Task CRUD UI is implemented with a shared Signal Form, inline details, canonical create/update, confirmed delete, safe backend validation mapping, and coordinated generation-safe Column/Task writes. MS5.8 is complete with 318 green frontend tests and a successful production build; see [ARCHITECTURE.md](ARCHITECTURE.md#task-crud-ui-ms58). MS5.9 Task drag/drop is complete with compatible CDK 22.1.6, optimistic same-/cross-Column placement, empty targets, canonical response reconciliation, rollback, safe stale-server reloads, shared write coordination, and generation-safe route/reload handling. All 369 frontend tests pass and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#task-drag-and-drop-ms59). MS5.1–MS5.9 remain complete. MS5.10 Priority is complete: existing backend enum/schema/CRUD reused without migration/API additions, accessible Task indicators, single-priority filtering, stable priority sorting as a local projection, preserved canonical manual positions, and guarded drag/drop in projected views. All 396 frontend tests pass and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#priority-product-ui-ms510). MS5.1–MS5.10 remain complete. MS5.11 Due dates is complete: existing LocalDate/DATE/CRUD reused without migration/API additions, browser-local civil-day semantics, localized date-only indicators, reactive midnight classification, exclusive Priority/Due-date filters, preserved canonical ordering, and guarded drag/drop. All 450 frontend tests pass and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#due-date-product-ui-ms511). MS5.1–MS5.11 remain complete. MS5.12 Search is complete: Board/owner-scoped backend title/description search, literal case-insensitive substring matching, bounded escaped queries, and deterministic manual ordering; frontend Search uses 300 ms debounce, cancellation, canonical membership projection, safe loading/error/retry/empty UX, exclusive Search/business filters, guarded drag/drop, and mutation/route/reload coherence. All 351 backend tests and 478 frontend tests pass, and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#task-search-ms512). MS5.1–MS5.12 remain complete. MS5.13 combined Filters is complete: canonical Column/status options, simultaneous Search/Column/Priority/Due AND projection, Clear filters preserving Task order, active-filter feedback, coherent mutations/reloads, and guarded drag/drop. All 526 frontend tests pass and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#combined-task-filters-ms513). MS5.1–MS5.13 remain complete. MS5.14 general Sorting is complete: nine local per-Column order modes, null-last Due dates, Created/Updated instant ordering, deterministic position/ID ties, and preserved canonical state, combined filters, drafts, and drag guards. All 561 frontend tests pass and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#general-task-sorting-ms514). MS5.1–MS5.14 remain complete. MS5.15 Dashboard statistics is complete with 377 green backend tests, 585 green frontend tests, and a successful production build: the resolved metric set is totalTasks, overdueTasks, priorityDistribution, and statusDistribution (Column workflow state), scoped to the owned Board with explicit browser-local asOf semantics. Completed/open were reviewed and intentionally omitted because the current domain has no explicit completion semantic; neither Column names nor positions define completion. Future completed/open metrics require an explicit workflow completion semantic first. See [ARCHITECTURE.md](ARCHITECTURE.md#board-dashboard-statistics-ms515). MS5.16 functional acceptance is complete; see the close-out above. Real-browser pointer verification remains deferred. Actual PostgreSQL migration/persistence/concurrency verification remains an explicit integration-test boundary.

- [x] Define and deliver the initial TaskFlow capabilities
- [x] Enforce ownership of the first private Board/Task resources and test cross-user access

## MacroStep 6 — Software quality

- [x] MS6.1 — OpenAPI / Swagger: complete (24 operations documented; 384 backend tests, 586 frontend tests and production build pass)
- [ ] MS6.2 — Logging: not started
- [ ] Establish automated testing and quality practices

MS6.1 is limited to generated application API documentation, Swagger UI and focused documentation/security regression checks. Later MS6 testing, integration, logging and quality-toolchain work has not started. See [ARCHITECTURE.md](ARCHITECTURE.md#openapi-and-swagger-ui-ms61).

## MacroStep 7 — Docker and local infrastructure

- [ ] Establish local containerized development infrastructure

## MacroStep 8 — Infrastructure as Code

- [ ] Define infrastructure as code

## MacroStep 9 — Cloud deployment

- [ ] Prepare cloud deployment

## MacroStep 10 — CI/CD and portfolio preparation

- [ ] Establish continuous integration and delivery
- [ ] Prepare portfolio materials
