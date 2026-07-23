# ADR 001: Monorepo architecture

## Context

TaskFlow consists of a Spring Boot backend, an Angular frontend, PostgreSQL database assets, and supporting infrastructure. These parts are developed together and share a single product roadmap, documentation set, and delivery lifecycle.

## Decision

TaskFlow will use a monorepo. The backend, frontend, infrastructure, documentation, scripts, and automation will be maintained in this repository with clear top-level boundaries.

## Consequences

The repository provides a single source of truth for cross-stack changes, architecture decisions, and developer guidance. It makes coordinated changes easier to review and supports consistent local development and CI/CD workflows.

This approach also requires clear directory ownership, focused changes, and automation that can validate each component independently as the project grows.
