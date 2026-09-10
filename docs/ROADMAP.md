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
