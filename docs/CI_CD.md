# CI/CD

## GitHub Actions baseline (MS10.1)

[CI](../.github/workflows/ci.yml) runs on pull requests targeting `main`, pushes
to `main` or `feature/**`, and manual dispatch. Superseded runs are cancelled per
workflow and PR number or branch ref; separate branches do not cancel each other.
The two jobs are independent:

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
`backend/pom.xml` and `frontend/package-lock.json`. Build outputs, coverage,
Docker layers and application images are not cached.

Only official actions are used, pinned to full upstream commits. Release tags
and commit targets were checked against the official repositories on 2026-09-25:

| Action | Verified release | Commit |
| --- | --- | --- |
| checkout | [v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1) | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| setup-java | [v6.0.1](https://github.com/actions/setup-java/releases/tag/v6.0.1) | `de7274f081f381c8f8158605e0321c36c376e2e6` |
| setup-node | [v7.0.0](https://github.com/actions/setup-node/releases/tag/v7.0.0) | `820762786026740c76f36085b0efc47a31fe5020` |

CI performs no container image build, registry publishing, deployment or AWS
operation. Testcontainers starts disposable local test containers only.
`security-audit.sh`, OWASP Dependency-Check and `npm audit` are excluded from this
baseline because their vulnerability-feed automation needs separate decisions.
Their existing local security contract is unchanged.

## Validation and status

[`test_ci_workflow.py`](../scripts/ci/test_ci_workflow.py) checks YAML with PyYAML,
exact events/permissions, independent jobs, runtime/cache inputs, full action
pins and allowed commands. It is a static contract, not a GitHub Actions emulator.
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

**MS10.1 HOSTED VALIDATION IN PROGRESS — first run exposed backend portability defects.**
Both hosted jobs must succeed on a new pushed commit before MS10.1 is complete.

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

Workflow YAML, quality thresholds and the frontend job are unchanged. This
correction is left uncommitted for manual review; a second hosted run is pending.

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

## Later ownership

MS10.2 owns Docker CI; MS10.3 registry delivery design; MS10.4 deployment design
and dry-run contracts; MS10.5 CI/CD identity/secrets; MS10.6 branch/PR quality
gates and further quality/security automation decisions. These are unimplemented.
GitHub CI is real executable automation. AWS registry delivery and deployment
remain reference/design only under the zero-AWS policy.
