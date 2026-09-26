# CI/CD

## GitHub Actions baseline (MS10.1)

[CI](../.github/workflows/ci.yml) runs on pull requests targeting `main`, pushes
to `main` or `feature/**`, and manual dispatch. Superseded runs are cancelled per
workflow and PR number or branch ref; separate branches do not cancel each other.
The two application quality jobs are independent:

| Job | Gate | Environment | Timeout |
| --- | --- | --- | --- |
| `backend-quality` | `./mvnw -B -ntp verify` in `backend` | Temurin Java 21, Ubuntu 24.04 | 30 minutes |
| `frontend-quality` | `npm ci`, then `npm run quality` in `frontend` | Node 22.23.1, Ubuntu 24.04 | 20 minutes |

The Maven Wrapper remains authoritative, including PostgreSQL Testcontainers,
JaCoCo thresholds and SpotBugs/FindSecBugs. The official
[Ubuntu 24.04 runner image](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
includes Docker client/server; `docker info` checks daemon accessibility before
verification. No PostgreSQL service container or alternate database is introduced.
CI #1 confirmed hosted Docker/Testcontainers availability; its backend gate failed
inside Maven tests, as recorded below.

Frontend quality owns formatting, lint, coverage tests and production bundle
build. Node 22.23.1 matches the local baseline and satisfies installed Angular 22.1
engine requirements (`^22.22.3 || ^24.15.0 || >=26.0.0`); see also
[Angular compatibility](https://angular.dev/reference/versions). The repository
records npm 10.9.8 in `packageManager`; setup-node uses the selected Node release's
bundled npm, and `npm ci` installs the lockfile without updating it. No separate
global npm installation or CI-only quality standard is introduced.

[`scripts/quality.sh`](../scripts/quality.sh) remains the local aggregate gate
(with frontend dependencies already installed). Timeouts allow cold dependency
caches and hosted-runner variation; they are bounds, not duration guarantees.

## Permissions, dependencies and boundaries

Workflow permissions are only `contents: read`; checkout does not persist Git
credentials. No repository secrets, variables, AWS credentials or OIDC role are
required. Official setup actions cache Maven/npm dependencies using
`backend/pom.xml` and `frontend/package-lock.json`. Build outputs, coverage and
application images are not uploaded as artifacts. MS10.2 adds component-scoped
BuildKit layer caches, described below.

Only official actions are used, pinned to full upstream commits. Release tags
and commit targets were checked against the official repositories on 2026-09-25:

| Action | Verified release | Commit |
| --- | --- | --- |
| checkout | [v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1) | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| setup-java | [v6.0.1](https://github.com/actions/setup-java/releases/tag/v6.0.1) | `de7274f081f381c8f8158605e0321c36c376e2e6` |
| setup-node | [v7.0.0](https://github.com/actions/setup-node/releases/tag/v7.0.0) | `820762786026740c76f36085b0efc47a31fe5020` |

CI builds container images locally but performs no image publishing, ECR/GHCR interaction,
deployment or AWS operation. No AWS credentials, AWS OIDC or repository secret
is required. Testcontainers starts disposable local test containers only.
`security-audit.sh`, OWASP Dependency-Check and `npm audit` are excluded from this
baseline because their vulnerability-feed automation needs separate decisions.
Their existing local security contract is unchanged.

## Validation and status

[`test_ci_workflow.py`](../scripts/ci/test_ci_workflow.py) checks YAML with PyYAML,
exact events/permissions, independent quality jobs, Docker matrix dependencies,
runtime/cache inputs, full action pins and allowed commands. It also exercises the
image validator against invalid metadata. It is not a GitHub Actions emulator.
Use `python3 scripts/ci/test_ci_workflow.py` where PyYAML is available. For this
milestone the existing cached `taskflow-cfn-lint:1.57.0` image supplies Python and
PyYAML with networking disabled, pulls forbidden and the repository read-only.
Actionlint is unavailable locally; static parsing cannot prove hosted execution.

Reproduce the cached static check from the repository root:

```bash
docker run --rm --pull never --network none \
  --mount "type=bind,source=$PWD,target=/repo,readonly" --workdir /repo \
  --entrypoint python taskflow-cfn-lint:1.57.0 scripts/ci/test_ci_workflow.py
```

**MS10.1 COMPLETE — GitHub-hosted CI run #2 passed on Ubuntu 24.04 with both backend-quality and frontend-quality successful.**

### First hosted execution and portability corrections

[CI #1, run 36108328937](https://github.com/JacopoCasanova98/taskflow/actions/runs/36108328937)
ran on GitHub-hosted Ubuntu 24.04 on 2026-09-25, triggered by a push of
`97d320c5c418f1948ae22992e6e6e10b61794133` on `feature/ci-portfolio-preparation`.
Checkout, Java setup and Docker checks succeeded. `frontend-quality` **passed**;
`backend-quality` **failed**: 487 tests, 2 failures, 16 errors, 0 skipped.
GitHub API job metadata was verified locally on 2026-09-26. Log archive retrieval
returned HTTP 403; the detailed hosted diagnostics here are from the supplied run
evidence, corroborated by local reproduction. This was not dismissed as flaky.

Two independent defects were exposed:

1. **Session-backed CSRF in shared test contexts.** Clean isolated
   `AuthenticationMvcTest` passed (18 tests), and the requested clean three-class
   security run passed in default order (30 tests). Repeating it with
   `-Dsurefire.runOrder=reversealphabetical` failed: 30 tests, 1 failure, 16 errors.
   Authentication requests then had `HttpSessionCsrfTokenRepository.CSRF_TOKEN`
   session attributes, no `Set-Cookie`, and no `XSRF-TOKEN`, matching hosted evidence.
   Spring Security Test 6.5.11's `.with(csrf())` replaces the live `CsrfFilter`
   repository using reflection with `TestCsrfTokenRepository` delegating to
   `HttpSessionCsrfTokenRepository`. The cached context carries that mutation to
   subsequent tests. The sole application bean remains `CookieCsrfTokenRepository`;
   production injection is already explicit and has no competing repository.
   Clean compilation alone does not explain the failure; ordering reproduces it.
   All three mutating helper calls in security/OpenAPI tests now use real SPA
   bootstrap cookies and headers. Production security wiring and `STATELESS`
   remain unchanged. An after-each context regression checks the sole bean and
   filter repository identity, then verifies cookie policy and absence of a session.
   It failed against the old helpers (7 of 10 security tests), then passed after
   correction. The expanded clean reverse-order suite passed all 41 tests.
2. **Host-clock nanoseconds versus database microseconds.** The hosted comparison
   changed `2026-09-25T07:35:38.748745747Z` to `2026-09-25T07:35:38.748746Z` after
   reload. Existing migrations use `TIMESTAMP WITH TIME ZONE`, whose
   [PostgreSQL 17 resolution is one microsecond](https://www.postgresql.org/docs/17/datatype-datetime.html).
   The installed pgJDBC 42.7.13 rounds sub-microsecond values at serialization.
   Auditing now deliberately truncates `Instant.now(utcClock)` to `ChronoUnit.MICROS`
   before persistence and DTO creation. Fixed-clock unit and PostgreSQL integration
   regressions use `.123456789Z`, expect audit values `.123456Z`, check raw JDBC
   rounding to `.123457Z`, and compare committed representations with reloads.
   The existing cross-user equality assertions remain intact. No schema migration,
   timezone workaround, production clock replacement or assertion tolerance is used.

Workflow YAML, quality thresholds and the frontend job are unchanged. The
corrections were committed as `28c95b20028d4b1ebd01f0ff64c32f44da32232c` and
pushed for the second hosted execution.

Local clean validation on 2026-09-26: `cd backend && ./mvnw clean verify` passed
490 tests with 0 failures, 0 errors and 0 skipped in 1m56s. PostgreSQL 17.11
Testcontainers passed both deterministic precision tests and all three existing
API integration tests, including cross-user persisted-state equality. JaCoCo
reported 94.64% lines (900/951) and 87.59% branches (247/282), above the unchanged
91%/84% thresholds. SpotBugs/FindSecBugs reported 0 bugs and 0 errors.
The subsequent complete `./scripts/quality.sh` passed: 490 backend tests and
593 frontend tests across 38 files, frontend coverage thresholds, formatting,
lint and production build. Frontend coverage was 98.36% statements, 96.85%
branches, 98.82% functions and 100% lines. The cached offline static CI contract passed both
tests. No quality gate was removed or weakened.

### Second hosted execution and acceptance

[CI #2, run 36240871848](https://github.com/JacopoCasanova98/taskflow/actions/runs/36240871848)
executed workflow `CI` on GitHub-hosted Ubuntu 24.04, triggered by a `push` of
`28c95b20028d4b1ebd01f0ff64c32f44da32232c` on `feature/ci-portfolio-preparation`.
The supplied hosted run evidence records overall conclusion **success**:

- `backend-quality` **SUCCESS**: checkout, Java 21 setup, Docker availability
  verification and the Maven quality gate including PostgreSQL Testcontainers.
- `frontend-quality` **SUCCESS**: checkout, Node setup, locked `npm ci` and the
  frontend quality gate.

CI #1 passed frontend but exposed CSRF test-order contamination and PostgreSQL
timestamp-precision portability defects in backend tests. Deterministic CSRF tests
and audit timestamps aligned to PostgreSQL microsecond precision corrected them.
CI #2 passed both jobs, validating backend/Testcontainers portability in a clean
Linux hosted environment. **MS10.1 accepted.** This is CI validation; it performs
no deployment or CD delivery.

## Docker CI (MS10.2)

**MS10.2 COMPLETE — GitHub-hosted CI run #3 passed all four required jobs.**

The existing workflow now defines quality → Docker image build matrix → local
image inspection. `docker-build (backend)` and `docker-build (frontend)` both
require successful `backend-quality` and `frontend-quality`. They use
GitHub-hosted `ubuntu-24.04`, a 30-minute timeout per execution and `fail-fast: false`
so a component failure does not cancel its sibling. The existing quality jobs,
checkout pin, permissions and trigger/concurrency contracts are unchanged.

The explicit matrix maps backend to `./backend` and frontend to `./frontend`,
each using its existing production `Dockerfile`. Buildx builds only `linux/amd64`,
with `push: false` and `load: true`. No QEMU or additional platform is configured.
Provenance export is disabled for these local single-platform validation images.

Official Docker release tags and their full commit targets were verified through
the upstream GitHub API on 2026-09-26:

| Action | Verified release | Commit |
| --- | --- | --- |
| setup-buildx-action | [v4.4.1](https://github.com/docker/setup-buildx-action/releases/tag/v4.4.1) | `f87e5991a6d7451dcb8d9637bfbc97413f497069` |
| build-push-action | [v7.4.0](https://github.com/docker/build-push-action/releases/tag/v7.4.0) | `c3c9e263c25d99ce0380d002d59b67737d91b0dc` |

Both images use the same full `${{ github.sha }}` in runner-local tags:
`taskflow-backend:git-${{ github.sha }}` and
`taskflow-frontend:git-${{ github.sha }}`. On pull requests this identifies GitHub's
tested merge revision. OCI `org.opencontainers.image.revision` records that SHA;
`org.opencontainers.image.source` records the repository URL. There is no fabricated
semantic version, secret, actor email or workstation path in these labels.
These CI tags are not deployment identities or published releases; MS9.2's
production registry-digest identity and coordinated image-pair contract remain authoritative.

Buildx uses its Docker-container builder and the
[GitHub Actions cache backend](https://docs.docker.com/build/cache/backends/gha/),
with separate `taskflow-backend` and `taskflow-frontend` scopes and `mode=max`.
GitHub supplies ephemeral cache authorization through the action; no configured
repository/environment secret or registry credential is needed. Cache reuse is
subject to GitHub branch access, eviction and service availability. Dockerfile
cache mounts are not separately exported. Final images are not uploaded as
artifacts; automatic build-record upload is disabled.

After loading, `docker image inspect` checks Linux/amd64, exposed `8080/tcp`,
backend user `10001:10001` and entrypoint `java -jar /app/app.jar`, frontend user
`nginx`, and exact OCI source/revision labels. The serialized final image
configuration must not contain `TASKFLOW_DB_PASSWORD`, `TASKFLOW_JWT_SECRET_BASE64`,
`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` or `AWS_SESSION_TOKEN`, even empty.
This is a targeted configuration check, not a complete layer/filesystem secret
scanner; Gitleaks remains separate.

No registry login, push, ECR/GHCR destination, GitHub Packages, deployment, AWS
credentials/API/OIDC, Terraform or CloudFormation operation is introduced.
Permissions remain only `contents: read`. No backend startup with fake credentials,
application E2E topology, frontend runtime smoke or production Compose startup is
added; this milestone validates builds and image packaging metadata.

Local validation on 2026-09-26 built and loaded both unchanged Dockerfiles using
Buildx 0.35.0 for `linux/amd64`, with temporary `ms102-local-<full-sha>` tags based
on source `fb48c1ed291215f58b8bea09852f90e8b976e6fe`. Both passed the workflow's
image validator against actual `docker image inspect` output. Cached layers were
reused; this does not prove cold hosted builds or GHA cache service integration.
The four cached/offline Python CI contract tests passed, including rejection of
invalid architecture, users, ports, labels, backend entrypoint and forbidden secret
variables. Actionlint remains unavailable. The unchanged application test suites
were not rerun. Temporary validation image tags were removed after inspection.

### Hosted execution and acceptance

[CI #3, run 36242343400](https://github.com/JacopoCasanova98/taskflow/actions/runs/36242343400)
executed workflow `CI` on GitHub-hosted Ubuntu 24.04, triggered by a `push` of
`ee084993fe14c07c19557049f3f367deb9b05a07`. The supplied GitHub Actions evidence
records overall conclusion **success**:

| Job | Result |
| --- | --- |
| `backend-quality` | SUCCESS |
| `frontend-quality` | SUCCESS |
| `docker-build (backend)` | SUCCESS |
| `docker-build (frontend)` | SUCCESS |

After both quality gates passed, each Docker matrix execution successfully
completed `Set up Docker Buildx`, `Build production image locally` and
`Inspect runtime image contract`: quality gates → Buildx → `linux/amd64` production
image build → local image load → runtime image contract inspection.
Both existing production Dockerfiles built successfully from the same full source
Git SHA. This hosted evidence satisfies MS10.2 acceptance.

Images were not pushed. No Docker registry login, ECR, GHCR, image publication,
AWS credentials/API, OIDC, deployment, Terraform plan/apply, CloudFormation
operation or production Compose execution occurred; no repository secret was
required. Successful build/load inspection establishes Docker buildability and
the packaging contract, not a registry release or production deployment identity.
Production deployment identity remains the registry digest contract defined by
[MS9.2](CONTAINER_RELEASE.md).

## Registry delivery design (MS10.3)

[REGISTRY_DELIVERY.md](REGISTRY_DELIVERY.md) defines the reviewed-commit → gated
image pair → reference-only ECR publication/scan review/digest capture → validated
pair-record interface. The version `1.0` JSON Schema and standard-library offline
validator reject partial pairs, mismatched repository identities and malformed
Git SHA/digest/platform/timestamp/version fields. They derive immutable Git tags
and repository-qualified digest references. Scan approval remains separate future
eligibility evidence; structural validation cannot prove registry provenance.

Application CI and Docker build CI are executable and proven by hosted runs.
Registry delivery is designed, not executed. AWS deployment remains reference
design, not executed; MS10.4 automation is not started. MS10.5 owns the future
short-lived identity interface, and MS10.10 owns any actual GitHub Release asset.
Normal CI remains unchanged with `push: false`; no AWS/OIDC, registry login,
digest lookup, publication, secrets or artifact upload is introduced.

MS10.3 acceptance is offline validator tests, static ECR/IaC/runtime alignment
and documentation review under the zero-AWS policy, not a hosted ECR execution.
**MS10.3 COMPLETE:** all 11 release-contract tests, five existing readiness checks,
five runbook checks and four CI contract tests passed using cached tooling with
networking disabled. MS10.4–MS10.11 remain deferred.

## Later ownership

MS10.4 owns deployment design
and dry-run contracts; MS10.5 CI/CD identity/secrets; MS10.6 branch/PR quality
gates and further quality/security automation decisions. These are unimplemented.
GitHub CI is real executable automation. AWS registry delivery and deployment
remain reference/design only under the zero-AWS policy.
