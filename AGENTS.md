# TaskFlow Agent Guide

TaskFlow is a production-quality full-stack project for managing work and tasks. Changes must favor clarity, maintainability, security, testability, and reliable operation.

## Technology stack

- Backend: Java 21 and Spring Boot
- Frontend: Angular
- Database: PostgreSQL
- Local infrastructure: Docker

## Coding principles

- Keep responsibilities focused and code easy to understand.
- Favor simple, explicit solutions over clever abstractions.
- Follow established project conventions and preserve clear boundaries between backend, frontend, database, and infrastructure concerns.
- Validate inputs, handle failures deliberately, and avoid exposing sensitive information.
- Add or update tests when behavior changes, once the relevant test tooling exists.
- Do not introduce dependencies, generated artifacts, or configuration that are not necessary for the requested change.

## AI agent workflow rules

- Analyze the repository and relevant documentation before modifying files.
- Propose a plan before undertaking work that has meaningful scope or architectural impact.
- Make small, focused changes that are straightforward to review.
- Never introduce unnecessary complexity or speculative implementation.
- Keep documentation updated when a change affects architecture, development workflow, or project direction.
- Verify changes proportionately and report what changed, including any limitations or follow-up work.
