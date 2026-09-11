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

## MacroStep 5 — Core TaskFlow features

In progress: MS5.1 domain design, MS5.2 Board backend, and MS5.3 Column backend are complete. MS5.4 Task backend implements V6, owner-scoped CRUD, basic priority/due-date persistence, full PUT content replacement, same-Board placement/resequencing, and COLUMN_NOT_EMPTY enforcement using the shared Board mutation lock; see [ARCHITECTURE.md](ARCHITECTURE.md#task-backend-ms54). MS5.4 is complete with 342 green backend tests (128 added). MS5.5 frontend work has not started. Actual PostgreSQL migration/persistence/concurrency verification remains an explicit integration-test boundary.

- [ ] Define and deliver the initial TaskFlow capabilities
- [ ] Enforce ownership of the first private Board/Task resources and test cross-user access

## MacroStep 6 — Software quality

- [ ] Establish automated testing and quality practices

## MacroStep 7 — Docker and local infrastructure

- [ ] Establish local containerized development infrastructure

## MacroStep 8 — Infrastructure as Code

- [ ] Define infrastructure as code

## MacroStep 9 — Cloud deployment

- [ ] Prepare cloud deployment

## MacroStep 10 — CI/CD and portfolio preparation

- [ ] Establish continuous integration and delivery
- [ ] Prepare portfolio materials
