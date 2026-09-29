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

**MS5.1–MS5.16 complete.** MS5.16 closes the roadmap functional flow with one routed frontend acceptance test using real application code and mocked HTTP. All 377 backend tests and 586 frontend tests pass; the production frontend build passes without warnings. No production fix was needed. The Definition of Done—TaskFlow is usable as a complete task manager—is satisfied within the documented automated acceptance boundaries; live PostgreSQL/Flyway, browser pointer/cookie integration, and production smoke-test design remain later work (no live AWS acceptance). See [ARCHITECTURE.md](ARCHITECTURE.md#functional-acceptance-and-macrostep-5-close-out-ms516).

Next: **MACROSTEP 6 — SOFTWARE QUALITY**. MS6.1 close-out is recorded below.

Feature history: MS5.1 domain design, MS5.2 Board backend, and MS5.3 Column backend are complete. MS5.4 Task backend implements V6, owner-scoped CRUD, basic priority/due-date persistence, full PUT content replacement, same-Board placement/resequencing, and COLUMN_NOT_EMPTY enforcement using the shared Board mutation lock; see [ARCHITECTURE.md](ARCHITECTURE.md#task-backend-ms54). MS5.4 is complete with 342 green backend tests (128 added). MS5.5 Board list frontend is implemented: feature-owned typed API and signal state, guarded `/boards` landing and root redirect, create/rename Signal Forms, explicit delete confirmation, and loading/empty/error/retry UX. MS5.5 is complete with 151 green frontend tests and a successful production build; see [ARCHITECTURE.md](ARCHITECTURE.md#board-list-frontend-ms55). MS5.6 read-only Board workspace is implemented: guarded reactive route, Board → Columns → parallel Tasks loading, server ordering, cancellation, loading/not-found/error/Retry states, responsive Kanban lanes, and minimal task cards. MS5.6 is complete with 173 green frontend tests and a successful production build; see [ARCHITECTURE.md](ARCHITECTURE.md#board-workspace-frontend-ms56). MS5.7 Column UI is implemented with create/rename Signal Forms, confirmed deletion and COLUMN_NOT_EMPTY feedback, pessimistic complete-order keyboard reorder, and generation-safe mutation reconciliation. MS5.7 is complete with 226 green frontend tests and a successful production build; see [ARCHITECTURE.md](ARCHITECTURE.md#column-ui-ms57). MS5.8 Task CRUD UI is implemented with a shared Signal Form, inline details, canonical create/update, confirmed delete, safe backend validation mapping, and coordinated generation-safe Column/Task writes. MS5.8 is complete with 318 green frontend tests and a successful production build; see [ARCHITECTURE.md](ARCHITECTURE.md#task-crud-ui-ms58). MS5.9 Task drag/drop is complete with compatible CDK 22.1.6, optimistic same-/cross-Column placement, empty targets, canonical response reconciliation, rollback, safe stale-server reloads, shared write coordination, and generation-safe route/reload handling. All 369 frontend tests pass and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#task-drag-and-drop-ms59). MS5.1–MS5.9 remain complete. MS5.10 Priority is complete: existing backend enum/schema/CRUD reused without migration/API additions, accessible Task indicators, single-priority filtering, stable priority sorting as a local projection, preserved canonical manual positions, and guarded drag/drop in projected views. All 396 frontend tests pass and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#priority-product-ui-ms510). MS5.1–MS5.10 remain complete. MS5.11 Due dates is complete: existing LocalDate/DATE/CRUD reused without migration/API additions, browser-local civil-day semantics, localized date-only indicators, reactive midnight classification, exclusive Priority/Due-date filters, preserved canonical ordering, and guarded drag/drop. All 450 frontend tests pass and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#due-date-product-ui-ms511). MS5.1–MS5.11 remain complete. MS5.12 Search is complete: Board/owner-scoped backend title/description search, literal case-insensitive substring matching, bounded escaped queries, and deterministic manual ordering; frontend Search uses 300 ms debounce, cancellation, canonical membership projection, safe loading/error/retry/empty UX, exclusive Search/business filters, guarded drag/drop, and mutation/route/reload coherence. All 351 backend tests and 478 frontend tests pass, and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#task-search-ms512). MS5.1–MS5.12 remain complete. MS5.13 combined Filters is complete: canonical Column/status options, simultaneous Search/Column/Priority/Due AND projection, Clear filters preserving Task order, active-filter feedback, coherent mutations/reloads, and guarded drag/drop. All 526 frontend tests pass and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#combined-task-filters-ms513). MS5.1–MS5.13 remain complete. MS5.14 general Sorting is complete: nine local per-Column order modes, null-last Due dates, Created/Updated instant ordering, deterministic position/ID ties, and preserved canonical state, combined filters, drafts, and drag guards. All 561 frontend tests pass and the production build succeeds; see [ARCHITECTURE.md](ARCHITECTURE.md#general-task-sorting-ms514). MS5.1–MS5.14 remain complete. MS5.15 Dashboard statistics is complete with 377 green backend tests, 585 green frontend tests, and a successful production build: the resolved metric set is totalTasks, overdueTasks, priorityDistribution, and statusDistribution (Column workflow state), scoped to the owned Board with explicit browser-local asOf semantics. Completed/open were reviewed and intentionally omitted because the current domain has no explicit completion semantic; neither Column names nor positions define completion. Future completed/open metrics require an explicit workflow completion semantic first. See [ARCHITECTURE.md](ARCHITECTURE.md#board-dashboard-statistics-ms515). MS5.16 functional acceptance is complete; see the close-out above. Real-browser pointer verification remains deferred. Actual PostgreSQL migration/persistence/concurrency verification remains an explicit integration-test boundary.

- [x] Define and deliver the initial TaskFlow capabilities
- [x] Enforce ownership of the first private Board/Task resources and test cross-user access

## MACROSTEP 6 — SOFTWARE QUALITY ✅

- [x] MS6.1 — OpenAPI / Swagger: complete (24 operations documented; 384 backend tests, 586 frontend tests and production build pass)
- [x] MS6.2 — Logging: complete (request correlation and safe event policy; 414 backend tests, 586 frontend tests and production build pass)
- [x] MS6.3 — Backend unit tests: complete (coverage audit; 29 focused unit cases, 443 backend tests, 586 frontend tests and production build pass)
- [x] MS6.4 — Backend integration tests: complete (16 PostgreSQL integration tests; 459 backend tests, 586 frontend tests and production build pass)
- [x] MS6.5 — Frontend test audit: complete (589 frontend tests across 38 files and production build pass)
- [x] MS6.6 — Code quality automation: complete (local gate; 462 backend tests, 589 frontend tests, formatting/lint/static analysis/coverage checks and production build pass)
- [x] MS6.7 — Security quality review: COMPLETE (quality and security gates pass; 466 backend tests, 589 frontend tests; no unresolved dependency findings or real secrets found)
- [x] MS6.8 — Performance sanity review ✅ (472 backend tests, including 22 PostgreSQL integration cases; 593 frontend tests; complete quality gate passes)

MS6.1 is limited to generated application API documentation, Swagger UI and focused documentation/security regression checks. MS6.2 adds the logging policy and request correlation; see [Logging](ARCHITECTURE.md#logging-ms62). MS6.3 audits unit coverage and closes meaningful gaps; see [Backend unit-test audit](ARCHITECTURE.md#backend-unit-test-audit-ms63). MS6.4 establishes real PostgreSQL/Flyway integration coverage; see [Backend integration tests](ARCHITECTURE.md#backend-integration-tests-ms64). MS6.5 audits existing frontend behavioral coverage and closes focused lifecycle gaps; see [Frontend test audit](ARCHITECTURE.md#frontend-test-audit-ms65). MS6.6 establishes the [local quality gate](ARCHITECTURE.md#local-code-quality-automation-ms66). MS6.7 is recorded in the [security quality review](ARCHITECTURE.md#security-quality-review-ms67). MS6.8 measurements, optimizations and accepted/deferred boundaries are recorded in the [performance sanity review](ARCHITECTURE.md#performance-sanity-review-ms68).

MacroStep 6 Definition of Done:

- [x] Repeatable quality gate
- [x] API documentation available (25 operations)
- [x] Adequate critical-flow tests
- [x] Primary technical risks covered, with residual deployment/scale boundaries documented

**MS6.8 COMPLETE. MACROSTEP 6 COMPLETE.** MacroStep 7 progress is recorded below.

## MACROSTEP 7 — Docker and local infrastructure ✅

- [x] MS7.1 — Backend Dockerfile
- [x] MS7.2 — Frontend Dockerfile
- [x] MS7.3 — PostgreSQL container
- [x] MS7.4 — Docker Compose
- [x] MS7.5 — Local developer experience
- [x] MS7.6 — Container verification

MS7.1 passed two local image builds (second build cached), disposable PostgreSQL
startup/Flyway verification, HTTP 200/UP health and non-root/JRE-only runtime
checks. See [Backend container](ARCHITECTURE.md#backend-container-ms71).
MS7.2 passed production/cached image builds, non-root read-only Nginx runtime,
SPA/static/cache/header checks, same-origin API and cookie/XSRF round trips, and
backend replacement through Docker DNS. See [Frontend container](ARCHITECTURE.md#frontend-container-ms72).
MS7.3 verified the official PostgreSQL 17 image, environment-based credentials,
native readiness and named-volume persistence across container removal/recreation,
including Flyway history and real TaskFlow user/Board data. The durable artifact
is the [PostgreSQL container contract](ARCHITECTURE.md#postgresql-container-contract-ms73);
no custom database image or Compose file is needed in this milestone.
MS7.4 passed clean Compose source builds, health-based startup, same-origin
user/Board flow, down/up persistence, unchanged Flyway history and PostgreSQL
restart recovery. See [Local Compose orchestration](ARCHITECTURE.md#local-compose-orchestration-ms74).
MS7.5 provides private generated local configuration and a native Compose
[developer workflow](LOCAL_DEVELOPMENT.md), verified through setup, healthy
startup, shutdown/restart, logs, migration inspection and isolated reset.
MS7.6 passed clean source builds, fresh startup/health, same-origin auth and full
Board/Column/Task flow, and persistence of updated/moved state across down/up.
Flyway, runtime boundaries, logs, secret scan and isolated cleanup passed.

MacroStep 7 Definition of Done:

- [x] TaskFlow completo può essere avviato localmente tramite Docker Compose.

**MS7.6 COMPLETE. MACROSTEP 7 COMPLETE.** MS8 progress is recorded below.

## MacroStep 8 — Infrastructure as Code

- [x] MS8.1 — AWS architecture decision
- [x] MS8.2 — Terraform provider/state
- [x] MS8.3 — Terraform networking
- [x] MS8.4 — Terraform security
- [x] MS8.5 — Terraform compute
- [x] MS8.6 — Terraform database
- [x] MS8.7 — Terraform outputs
- [x] MS8.8 — CloudFormation equivalent
- [x] MS8.9 — IaC verification (static/local)

MS8.1 selects the [AWS reference architecture](AWS_ARCHITECTURE.md) recorded in
[ADR 002](ADR/002-aws-deployment-architecture.md): Ireland, ALB/ACM, one EC2
application host and private Single-AZ RDS, with explicit cost/security trade-offs.
MS8.2 establishes the [Terraform root module](../infra/terraform/README.md):
provider/version constraints, validated inputs, naming/tags, metadata outputs,
local state policy and a generated multi-platform dependency lock. Terraform
1.16.3 / AWS provider 6.65.0 initialization, formatting and credential-free,
network-disabled validation passed for the foundation.
MS8.3 implements the approved VPC, two public and two isolated database subnets,
IGW and explicit tier routing. Formatting, offline validation and static graph /
network-invariant review passed with the unchanged provider lock.
MS8.4 adds ALB/App/DB Security Groups and exactly seven dedicated traffic rules:
public ALB entry, ALB-only Nginx ingress, app-only PostgreSQL ingress and explicit
restricted egress. No SSH; future SSM IAM belongs to MS8.5. Formatting, offline
validation and static security/graph review passed for MS8.4.
MS8.5 defines one EC2 host with encrypted root storage and minimal host bootstrap,
EC2-only IAM/profile with SSM, scoped ECR pull and telemetry, two ECR repositories,
ALB/target/listeners with required external certificate input and internal-health
blocking, three log groups and four alarms. Formatting, offline validation,
graph/static review and bootstrap syntax checks passed; no runtime deployment
or telemetry collection is claimed.
MS8.6 defines private Single-AZ PostgreSQL 17, encrypted 20 GiB gp3, seven-day
backups/PITR and deletion/final-snapshot safeguards. RDS manages the master;
two application secrets contain metadata only, with exact app-role read grants.
Native PostgreSQL logs and two RDS alarms are defined. Formatting, offline
validation and static dependency/security review passed; no live data sources
or secret values exist. Flyway/schema and external role-bootstrap boundaries remain.
MS8.7 exposes a curated twelve-output interface: foundation metadata, ALB
hostname/zone, operational IDs/maps, ECR URLs and private database hostname.
No credentials/master-secret metadata or EC2 public IP are exposed. Formatting
and offline validation passed; infrastructure inventory and lock are unchanged.
MS8.8 implements the [CloudFormation alternative](../infra/cloudformation/README.md):
the same networking/security, IAM, EC2/ECR/ALB, database/secrets, observability
and non-sensitive output semantics. Its native SG-egress suppression and
secret/database lifecycle differences are explicit. cfn-lint 1.57.0 passed for
eu-west-1 offline without credentials, together with static security/parity,
bootstrap syntax and Gitleaks checks. Terraform remains unchanged.
MS8.9 completed the [final static parity audit](../infra/cloudformation/README.md#final-parity-audit-ms89).
Terraform 1.16.3 / AWS 6.65.0 fmt, validate/JSON and graph passed; cfn-lint 1.57.0
passed for eu-west-1. Security/parity/bootstrap assertions and Gitleaks 8.30.1
passed without networking, AWS credentials, state or deployment inputs.
Deprecated repository scanning was removed from both implementations: BASIC
registry scanning is an external prerequisite by default, with guarded sole-owner
opt-in and a TaskFlow prefix filter. Regional ownership implications are explicit.
**MS8.1–MS8.9 COMPLETE. MACROSTEP 8 COMPLETE.**
MacroStep 9 progress is recorded below. No resources provisioned.

TaskFlow's portfolio AWS infrastructure is intentionally non-provisioned; no recurring AWS hosting cost is required to complete the project.

The hard [zero-provisioning policy](AWS_ARCHITECTURE.md#hard-project-execution-policy)
applies to all later milestones, including CI/CD: no AWS mutation, hosting,
image push, stack operation or billable resources. No AWS credentials, access
keys, authenticated provider API calls, real account/hosted-zone IDs, domains or
secret population are required for completion.

MS8.2–MS8.7 implement genuine un-applied Terraform providers, variables, locals,
networking, security/IAM, compute/ALB/ECR, database, Secrets Manager metadata,
CloudWatch and outputs. MS8.2 designs state conventions without provisioning a
bucket or requiring a real S3 backend; `.tfstate` remains ignored/sensitive.
Verification uses formatting, credential-free `init -backend=false`, validate,
static dependency/reference inspection and provider-schema checks where possible.
The original live plan/stack-diff requirement is superseded: no Terraform plan,
apply or destroy is run. MS8.8 implements
a genuine CloudFormation equivalent with local/static tooling, never a stack or
AWS-backed change set. MS8.9 verified parity and static/local validation evidence.

MacroStep 8 Definition of Done:

- [x] Terraform and CloudFormation express the approved AWS reference architecture,
  remain semantically aligned, and pass local/static verification without requiring
  AWS credentials or creating AWS resources.

## MacroStep 9 — Deployment Design & Production Readiness

Goal: prove that application and IaC contain a coherent, documented production
deployment path without actually provisioning AWS.
**MS9.1–MS9.8 COMPLETE. MACROSTEP 9 COMPLETE.** This is validated deployment design,
not live AWS deployment or a claim of public reachability.

- [x] MS9.1 — Production runtime and Docker Compose configuration design
- [x] MS9.2 — Container release/tagging and conceptual ECR workflow
- [x] MS9.3 — EC2 bootstrap/user-data and Secrets Manager retrieval design
- [x] MS9.4 — RDS connection/TLS, database-role bootstrap and migration design
- [x] MS9.5 — Reverse-proxy, HTTPS/ACM/Route 53 and security checklist
- [x] MS9.6 — CloudWatch/logging and operational monitoring design
- [x] MS9.7 — Deployment, rollback and disaster-recovery/backup runbooks
- [x] MS9.8 — Static production smoke-test plan and readiness review

MS9.1 defines the separate [production runtime contract](PRODUCTION_RUNTIME.md)
and frontend/backend Compose model. Static model validation, all six inputs'
missing/empty rejection checks, forced Secure cookies and Gitleaks passed.
Local Compose, application code and IaC remain unchanged; no AWS, registry or
production deployment operation occurred. See the concise
[architecture close-out](ARCHITECTURE.md#production-runtime-design-ms91).

MS9.2 defines the [paired container release contract](CONTAINER_RELEASE.md):
immutable full-Git-SHA tags, optional shared version aliases, amd64 builds and
digest-only production selection. ECR scanning prerequisites, retention and
partial-push failures are explicit. Static contract/Compose checks, reference
shell syntax validation and offline Gitleaks passed. Dockerfiles, Compose and
IaC remain unchanged; no AWS/registry operation or actual release occurred.

MS9.3 defines [secret-free bootstrap and protected secret delivery](EC2_BOOTSTRAP_SECRETS.md):
backend-only Compose secrets/configtree, atomic ephemeral materialization and
persistent Docker metadata isolation. All 479 backend tests pass, including seven
focused configuration cases; fake-AWS/materializer and fake-firewall/parity tests,
static Compose checks, offline Terraform fmt/validate/graph, cfn-lint and Gitleaks
pass. No AWS/IMDS/registry call, secret population or production startup occurred.

MS9.4 defines [RDS TLS, roles and migration preparation](RDS_DATABASE_OPERATIONS.md):
independent administrator, migrator and runtime identities; password-free role
bootstrap; verify-full JDBC and regional CA trust; migrations from the existing
backend artifact before runtime startup with Flyway disabled and Hibernate validate.
All 485 backend tests and packaging passed. Local PostgreSQL verifies non-superuser
bootstrap, migration ownership, runtime CRUD, denied DDL and future-object grants.
Offline helper/materializer tests, static Compose checks and Gitleaks passed.
Local Compose, existing migrations and IaC are unchanged. No AWS/RDS connection,
master-secret retrieval, registry operation or production startup occurred.

MS9.5 defines the [edge security contract](EDGE_SECURITY.md): trusted ALB/Nginx
forwarding, production-only Spring NATIVE processing, approved-host HTTPS routing,
backend-aware internal health, operational-path blocks and login/register limits.
The interrupted backend failure was a test-context defect; the embedded Tomcat
harness now loads the actual Boot customizer. All 38 focused and 487 full backend
tests pass, with no failures, errors or skips. Earlier 593 frontend tests and image
build remain valid; no frontend/Nginx edits were made during the recovery.
Cached-image edge tests, RealIP/configuration checks, offline Terraform
fmt/validate/graph, cfn-lint, static parity and Gitleaks passed. No AWS, DNS, ACM,
registry or production runtime operation occurred. MS9.6 follows below.

MS9.6 defines [observability and monitoring](OBSERVABILITY.md): non-blocking Docker
awslogs for production containers, selected host journals/cloud-init through the
CloudWatch Agent, and only memory/root-disk custom metrics at 60-second cadence.
Two matching guest warnings complement six native alarms; optional external SNS
ALARM/OK actions default to silent. Four log groups/14-day retention, scoped IAM,
application logging and IMDS isolation are preserved. Focused static configuration,
rendered Compose, metric/alarm/IAM parity, edge/bootstrap regression, offline Terraform
fmt/validate/graph, cfn-lint and cached Gitleaks passed. No application test rerun was
needed because application/Nginx sources and formats did not change. No AWS API,
credentials, logs/metrics delivery, agent activation or production startup occurred.
MS9.7 and MS9.8 follow below.

MS9.7 defines the operator-guided [deployment/rollback](DEPLOYMENT_RUNBOOK.md) and
[disaster-recovery/backup](DISASTER_RECOVERY.md) runbooks. Immutable digest pairs,
pull-before-mutate, migration-before-runtime, schema-compatible rollback, secret/CA
rotation, replaceable hosts and new-instance RDS recovery preserve the existing
architecture. Write quiescence, restored credentials/sessions, monitoring identity
reconciliation and unmeasured recovery objectives are explicit. Static runbook/link,
observability, edge, bootstrap and migration-helper contracts pass; cached read-only,
network-disabled Gitleaks found no leaks. Materializer regression was not rerun because
cached tooling lacks real jq; the unchanged helper was reviewed statically. Only
documentation and the static runbook test change. No AWS/deployment/recovery operation
occurred. MS9.8 final verification follows below.

MS9.8 adds the [production smoke specification](PRODUCTION_SMOKE_TEST.md) and
[final readiness record](PRODUCTION_READINESS.md), separating proven local/static
invariants, live-only operator checks and accepted limitations. The original
open site → register → login → create Board → Task operations → logout intent is
preserved. Static readiness/runbook/observability/edge/bootstrap/migration-helper
tests, fixture-only production Compose render, isolated Nginx edge tests, offline
Terraform fmt/validate/validate-json/graph (valid=true, zero errors/warnings) and
eu-west-1 cfn-lint pass. Cached Gitleaks found no leaks in all 78 Git commits and
final production artifacts. Prior 487 backend/593 frontend suite evidence is
retained; no full-suite rerun claimed. Materializer remains unchanged since its
MS9.4 validation; no MS9.7/MS9.8 rerun without cached real jq.

An isolated local Compose HTTP/API rehearsal passed auth, CSRF, Board/Column/Task
operations, move/search, ownership isolation, statistics, persistence across full
container recreation, deletion and logout. Its containers/networks/volume and
credentials were removed without touching developer data. Browser pointer/filter
interaction and all cloud/public-HTTPS behavior remain outside that local evidence.
No runtime/application/IaC implementation changed and no AWS operation occurred.

MacroStep 9 Definition of Done under the adapted zero-provisioning policy:

- [x] Production architecture + runtime + release + security + operations + recovery
  + smoke/readiness procedure are coherent and locally/statically validated without
  provisioning AWS.

**MS9.8 COMPLETE — MACROSTEP 9 COMPLETE.** Live-only checks remain deliberately
unchecked in the readiness record. MS10 had not started at the MS9 close-out.

Acceptance is local/static evidence and coherent artifacts/runbooks. No domain
purchase, live URL, real certificate, secret population, AWS stack, cloud restore
or public-cloud smoke test is required or authorized. Runbooks describe what an
independent real operator could do; the project does not execute their AWS steps.

## MacroStep 10 — CI/CD and portfolio preparation — COMPLETE

- [x] MS10.1 — GitHub Actions baseline
- [x] MS10.2 — Docker CI
- [x] MS10.3 — Registry delivery design
- [x] MS10.4 — Automated deployment design / dry-run contract
- [x] MS10.5 — CI/CD identity and secrets security design
- [x] MS10.6 — Branch / PR quality gate
- [x] MS10.7 — Professional README / portfolio demo
  - [x] MS10.7A — Free portfolio live demo — COMPLETE
  - [x] MS10.7B — Professional README — COMPLETE
- [x] MS10.8 — Diagrams
- [ ] MS10.9 — Portfolio assets: DEFERRED / OPTIONAL
- [x] MS10.10 — GitHub release: COMPLETE
- [x] MS10.11 — Final repository cleanup

MacroStep 10 is complete following publication of `v1.0.0`. The checklist
above records current status; closeouts below preserve evidence and milestone
states at the time each was written.

**MS10.1 COMPLETE — GitHub-hosted CI run #2 passed on Ubuntu 24.04 with both backend-quality and frontend-quality successful.**
CI #1 passed frontend quality and failed backend quality (CSRF test-context
pollution and audit timestamp precision). After deterministic CSRF tests and
PostgreSQL microsecond-precision audit timestamp corrections, push-triggered
[CI #2, run 36240871848](https://github.com/JacopoCasanova98/taskflow/actions/runs/36240871848)
passed on commit `28c95b20028d4b1ebd01f0ff64c32f44da32232c`. See
[CI/CD](CI_CD.md).

**MS10.2 COMPLETE — GitHub-hosted CI run #3 passed with backend-quality,
frontend-quality, docker-build (backend), and docker-build (frontend)
all successful.**
Push-triggered [CI #3, run 36242343400](https://github.com/JacopoCasanova98/taskflow/actions/runs/36242343400)
passed on commit `ee084993fe14c07c19557049f3f367deb9b05a07`.
Both Docker jobs successfully completed Buildx setup, production image build
for `linux/amd64`, local image load and runtime image contract inspection.
Both CI images share the same full source Git SHA; no image was published.

**MS10.3 COMPLETE — registry delivery design validated; no registry operation performed.**
[Registry delivery](REGISTRY_DELIVERY.md) defines the existing ECR pair's immutable
Git-SHA identity, registry-digest deployment references, versioned JSON Schema and
offline standard-library validator. Partial/mismatched pairs are rejected; external
registry provenance and scan-review evidence remain required for eligibility.
All 11 release-contract tests, five existing readiness checks, five runbook checks
and four CI contract tests passed with cached tooling and networking disabled.
Normal CI, application code, Dockerfiles, Compose and IaC remain unchanged.
This design milestone requires no live ECR execution.

**MS10.4 COMPLETE — offline deployment dry-run contract validated; no deployment performed.**
[Deployment automation](DEPLOYMENT_AUTOMATION.md) adds a closed non-secret v1.0
intent schema and deterministic offline planner reusing MS10.3 release validation.
It derives digest-qualified runtime images and preserves MS9.7's D1–D7 order,
mandatory migration, preflight/compatibility and acceptance boundaries. No live
executor or automatic rollback is introduced. All 13 deployment-plan tests,
11 release-pair tests, four CI checks, five readiness checks and five runbook
checks passed with cached, network-disabled tooling. CI, application, Compose
and IaC remain unchanged.
MS10.7–MS10.11 are deferred.

**MS10.5 COMPLETE — CI/CD identity and secret boundaries validated offline; no AWS identity used.**
[CI/CD security](CI_CD_SECURITY.md) defines separate runtime, publisher, deployment
and provisioner identities, exact future OIDC trust, scoped ECR publisher actions
and protected secret flows. The closed v1 reference contract and offline validator
reject privilege expansion and unsafe deployment authority. All 14 identity tests,
11 release-pair tests, 13 deployment-plan tests, four CI checks, five readiness
checks and five runbook checks passed with cached, network-disabled tooling.
Current CI and runtime/IaC remain unchanged.
MS10.7–MS10.11 remain deferred.

**MS10.6 COMPLETE — GitHub-hosted CI run #4 passed all five jobs,
including ci-gate, and live GitHub ruleset 24046584 actively
protects main with PR + strict ci-gate requirements.**
Push-triggered [CI #4, run 36259778449](https://github.com/JacopoCasanova98/taskflow/actions/runs/36259778449)
passed on commit `f898a6b5b6dafdd6ae9576efb5383d0a097368b7`: both quality jobs,
both Docker matrix jobs and `ci-gate` succeeded, including its upstream-result step.
[Repository governance](REPOSITORY_GOVERNANCE.md) records active ruleset
`24046584` (`Taskflow main Ruleset`), targeting `~DEFAULT_BRANCH` = `main`.
PRs require zero approvals and conversation resolution; strict `ci-gate`,
force-push/deletion blocks and no bypass actors are active. Merge commits remain
allowed. The desired-policy JSON remains a reference, not a GitHub configurator.
MS10.7–MS10.11 remain unchecked and deferred.

GitHub CI is real and executable; local/Docker build automation may also be real.
ECR delivery and AWS deployment remain reference/design only, with no AWS
credentials required. No deployed-app URL will be fabricated: MS10.9 uses
screenshots, demo and local evidence instead. GitHub Releases may be real because
they do not require AWS provisioning. These adaptations do not authorize any
later milestone's implementation during MS10.6.

**MS10.7A COMPLETE — free portfolio demo live and core user flow validated (2026-09-27).**
[Portfolio demo](PORTFOLIO_DEMO.md) records deployed commit
`797a3e4e3ee815ed11380ad751da6c1c420afb00` (`deploy: prepare free portfolio demo`).
[CI run #5, 36309160331](https://github.com/JacopoCasanova98/taskflow/actions/runs/36309160331)
was green: both quality jobs, both Docker build jobs and `ci-gate` passed.
The Render Static Site [frontend](https://taskflow-demo-frontend-bod0.onrender.com)
and Render Free Docker [backend](https://taskflow-6udg.onrender.com) are live;
the backend and Neon Free PostgreSQL 17.11 are in Frankfurt. Flyway validated six
migrations with schema v6; Hibernate validation passed. Manual ordered API/SPA
rewrites support the same-origin frontend. The human browser smoke passed HTTPS
loading, registration/logout/login, board/column creation, task creation/editing/
deletion, persisted data after refresh, and logout followed by refresh remaining
unauthenticated. Two-account isolation and natural cold-start testing remain
unverified live; automated security tests are separate evidence.

No AWS resource was provisioned and no paid resource was introduced. AWS production
contracts remain unchanged; Render/Neon hosting is explicitly non-production.
Both Render services have auto-deploy OFF and temporarily use
`feature/ci-portfolio-preparation`; restore `main` after MacroStep 10 merges.
MS10.7A live evidence remains separate from automated security tests and the AWS reference design.
MS10.8–MS10.11 remain unchecked and unstarted.

**MS10.7B COMPLETE — professional README (2026-09-27).**
The [README](../README.md) now prominently links the validated live portfolio demo
and summarizes product capabilities, engineering decisions, technology, local
setup, quality gates, repository structure, and technical documentation. It
clearly separates the non-production Render/Neon demo from implemented but
non-provisioned AWS reference IaC. Claims and links were reviewed against tracked
configuration and the MS10.7A evidence; no application or provider change is part
of this documentation milestone. MS10.7A and MS10.7B are complete, closing parent
MS10.7. Diagrams (MS10.8) and screenshots/portfolio assets (MS10.9) remain deferred;
MS10.8–MS10.11 remain unchecked and unstarted.

**MS10.8 COMPLETE — source-controlled architecture diagrams (2026-09-27).**
[Canonical Mermaid diagrams](DIAGRAMS.md) cover the logical application, live
Render/Neon portfolio demo, non-provisioned AWS reference architecture, and
implemented CI versus reference delivery flow. Markdown sources and every flow
were audited against the authoritative architecture, runtime, IaC, workflow,
governance, release, deployment and identity contracts. Mermaid source received
manual syntax/structure review; no renderer or diagram dependency was introduced.
README and architecture navigation link to the canonical page. No AWS resource
was provisioned and no provider configuration, code, CI or IaC behavior changed.
MS10.9–MS10.11 remain unchecked and unstarted.

**MS10.9 adaptation — DEFERRED / OPTIONAL (2026-09-27).**
Real screenshots may be added manually later. The repository already provides a
live public demo and source-controlled architecture diagrams; screenshots are
presentation polish, not a technical completion requirement. MS10.9 no longer
blocks MacroStep 10 completion and is not marked complete. No fake/generated
screenshots, placeholders, or image assets were added.

**MS10.10 PREPARED — v1.0.0 release material (2026-09-27).**
[Reviewed-body source](RELEASE_NOTES_v1.0.0.md) describes the implemented portfolio
application, verification evidence, live non-production demo, and unprovisioned
AWS reference design. [Manual release procedure](CI_CD.md#manual-github-release-ms1010)
requires the MacroStep 10 merge and green CI on the selected `main` commit before
an annotated tag and GitHub Release are created. Local/remote tag lists and the
public GitHub release listing were empty during preparation; recheck before
publication. Maven/npm package versions remain unchanged. No tag, release, commit,
or push was performed. MS10.10 remains unchecked until the actual GitHub Release
exists; MS10.11 remains unstarted.

**MS10.11 COMPLETE — final repository cleanup (2026-09-27).**
Audited release-preparation baseline `a6142099feee66308dce6f03ab1e22b8c15301b0`.
No accidental tracked build outputs, logs, archives, credentials or screenshot
artifacts required removal; no ignore-rule change was justified. Corrected stale
README/governance milestone wording and scoped registry deployment statements to
AWS. Release notes explicitly remain prepared material, not publication evidence.
README and release scope remain consistent with the live non-production demo and
the unprovisioned AWS reference architecture.

Validation passed: 63 lightweight demo, readiness, runbook, CI, governance,
identity, release and deployment tests; standalone observability and edge
contracts; cached offline Terraform formatting/validation and CloudFormation
lint for `eu-west-1`; relative Markdown links; Gitleaks history/worktree scans;
and whitespace checks. The interrupted observability failure was an invocation
error (a standalone script passed to `unittest`), resolved without changing tests.
No application, dependency, workflow, Docker, IaC or provider behavior changed.

At the MS10.11 closeout, MS10.9 was deferred/optional and non-blocking; MS10.10
was prepared but unpublished, so MacroStep 10 was not yet fully complete.

External closeout planned at MS10.11, subsequently completed by the human:

1. Commit and push MS10.11.
2. Open the final MacroStep 10 PR to `main`.
3. Obtain a green required `ci-gate`.
4. Merge to `main`.
5. Manually restore both Render demo services from the temporary feature branch to `main`.
6. Verify final `main` CI and the live demo.
7. Create annotated tag `v1.0.0` on the exact verified release commit.
8. Publish the GitHub Release using [reviewed release notes](RELEASE_NOTES_v1.0.0.md).

No AWS operation belongs to this sequence. No tag, release, PR, commit or push
was performed during cleanup.

**MS10.10 and MacroStep 10 COMPLETE — v1.0.0 published.**
PR #9, “Complete MacroStep 10 — CI/CD and Portfolio Preparation”, merged into
`main` at `4098751c0cdad81ae8c26e0371eda5cba709f90c`. Main CI run
`36325514971` completed successfully for that exact commit. Both Render services
were restored to `main`, and both frontend and backend deployed that release
commit. The human manually revalidated the
[live demo](https://taskflow-demo-frontend-bod0.onrender.com) after deployment.
Tag `v1.0.0` was verified against the same main/release commit, and the human
published GitHub Release `v1.0.0` using the reviewed release notes.
AWS remained completely unprovisioned. MS10.11 remains complete; MS10.9
screenshots remain deferred/optional and do not block completion.

## MacroStep 11 — Product UI & Responsive Experience

**Goal:** A premium, consumer-grade, spacious and highly polished product
experience inspired by the interaction quality of products such as Airbnb,
while preserving TaskFlow's own identity. This is inspiration for clarity, warmth,
soft geometry and attention to detail, not copied branding, colors, assets or
layouts. Preserve the domain model, API contracts, security model and functional
behavior. Avoid dense developer interfaces and enterprise/admin-dashboard styling.

- [x] MS11.1 — UI foundation and design system
- [x] MS11.2 — Branding and browser polish
- [x] MS11.3 — Application shell and interaction infrastructure
- [x] MS11.4 — Authentication experience
- [x] MS11.5 — Boards dashboard
- [x] MS11.6 — Modern Kanban workspace
- [x] MS11.7 — Task details and task forms
  - Local implementation revised on 2026-09-28: one large task dialog for details/create/edit, compact in-dialog delete confirmation, canonical-state updates, pending dismissal protection, and focus restoration. Existing inline-DOM tests migrated to CDK overlay DOM without removing behavioral assertions. Focused task/form/mutation/shared-dialog/column tests: 133 passed. Headless Chrome with mocked API data reviewed desktop (1440×1000) and mobile (390×844) task surfaces; mobile form fields remain contained without horizontal overflow. Real-device/touch and human visual acceptance remain pending.
  - Final validation (2026-09-28): the search/filter migration blockers are resolved. `npm run quality` passes formatting, lint, all 614 tests in 40 files, unchanged coverage thresholds and production build; `git diff --check` passes. Coverage: statements 97.44%, branches 95.85%, functions 97.94%, lines 98.56%. No additional task-modal visual changes were needed.
- [x] MS11.8 — Search, filters and sorting experience
  - Final validation (2026-09-28): approved search/pill/popover appearance preserved. Restored Retry search, named the compact clear action accessibly, and exposed selected options through CDK radio-menu semantics. Removed hidden compatibility selects; migrated priority/due/combined-filter and acceptance tests to visible menu interactions. Search, combined filters, sorting, clearing, selected values, expanded state, Escape/focus restoration and outside dismissal pass 102 focused tests in 5 files. Full quality evidence is recorded above. Test-only menu support lives in `frontend/testing`, outside production sources. No new browser visual acceptance is claimed by this test-only closeout; MS11.9–MS11.12 remain unchecked.
- [x] MS11.9 — Board statistics presentation
  - Complete (2026-09-28): one spacious Board overview surface using existing radius, spacing, border and restrained elevation tokens. Prominent total/overdue values and readable priority/column rows pair exact counts with decorative CSS-only proportional bars; zero totals and empty boards remain explicit. Server statistics, board-wide scope, column ordering, retry and mutation-refresh behavior are unchanged. Narrow layouts stack groups and wrap long labels without changing the interaction architecture.
  - Validation: 27 statistics tests pass; statistics plus functional acceptance pass 28 tests in 4 files. Full `npm run quality` passes formatting, lint, all 616 tests in 40 files, unchanged coverage thresholds and production build. Coverage: statements 97.44%, branches 95.85%, functions 97.94%, lines 98.56%. `git diff --check` passes. Headless Chrome with mocked API data inspected the section at 1440px, 900px and 390px; narrow-width checks showed no page overflow. This is section-level sanity checking, not MS11.10–MS11.12 final acceptance. Approved filters/task dialogs were preserved; no backend, API, database, chart dependency, commit or push changes.
  - MS11.6–MS11.9 checkpoint (2026-09-28): task-card title buttons now use transparent, borderless styling, brand-teal hover feedback, wrapping semibold text, a 44px minimum hit area and a visible keyboard focus outline. Native activation and separate drag handles are unchanged. Task/Kanban focused tests: 113 passed in 3 files. The complete quality gate remains green with 616 tests and unchanged coverage thresholds; `git diff --check` passes. This title-only correction does not start MS11.10.
- [x] MS11.10 — Responsive desktop/tablet/mobile UX
- [x] MS11.11 — Product polish and interaction states
- [ ] MS11.12 — Visual, responsive, accessibility and regression acceptance

**Scope:** Extend the existing Angular architecture with centralized tokens and
small reusable styling primitives; keep dependencies minimal. Preserve backend,
API, database, authentication, ownership, Board/Column/Task, search, filtering,
sorting and statistics semantics. Deployment, Docker/runtime contracts and
Terraform/CloudFormation remain unchanged. No collaboration, comments, realtime,
notifications, uploads, calendar, PWA/offline, themes/dark mode, avatar uploads,
new endpoints/domain fields or major state-management rewrite is included.

**Responsive contract:** Design deliberate desktop (approximately >= 1200px),
tablet (768–1199px) and smartphone (< 768px) experiences. Desktop remains
Kanban-first; tablet uses compact navigation and touch-friendly board navigation;
mobile reorganizes controls and forms rather than squeezing desktop toolbars.
Task details adapt to available width, controls have comfortable touch targets,
and the page must not overflow horizontally. Exact breakpoints can follow layout
evidence. Full responsive redesign belongs to later milestones, not MS11.1.

**MS11.2 scope:** A coherent TaskFlow mark/logo, SVG and fallback favicons,
Apple touch icon where justified, document title, useful description, browser
`theme-color`, and `index.html` cleanup. Add social preview metadata only where
justified for the public demo; favicon support does not require PWA infrastructure.
Branding is not part of MS11.1.

**Product design contract (implemented progressively in MS11.2+):**

- Clean top navigation with brand, Boards and account actions; no heavy permanent
  sidebar by default. Give the board workspace generous horizontal space.
- White/light neutral surfaces, near-black text, readable gray metadata and one
  TaskFlow accent. Use spacing and subtle borders rather than nested tinted boxes.
  Normal cards have little or no shadow; floating elements use soft elevation.
- Comfortable 44–48px controls, 10–12px control radii, 12–16px small cards/popovers,
  16–20px primary cards, 24–28px dialogs and fully rounded chips where appropriate.
  Typography provides hierarchy without oversized headings or visual density.
- Progressive disclosure: secondary actions belong in contextual menus, suitable
  forms and destructive confirmations in dialogs. Primary, neutral secondary,
  quiet ghost and circular/soft-square icon controls form one button language;
  danger styling is prominent only where needed.
- Boards become spacious content cards with useful information and contextual
  actions. Kanban columns stay light; task cards dominate with comfortable padding,
  restrained priority/due metadata and polished existing drag interactions.
- Search and rounded filter/sort controls replace the raw-select visual treatment
  in MS11.8; removable active chips and dedicated mobile overlays may organize
  the same filter semantics. Do not crowd the workspace with all actions at once.
- Forms use strong labels, comfortable fields, helpful spacing and quiet inline
  validation. Empty, loading, error and confirmation states are designed product
  states, including within dialogs; avoid unnecessary boxes inside boxes.
- Use one lightweight icon family if needed later, never mixed families or emoji
  UI icons. Subtle hover, press, menu and dialog motion must respect reduced motion
  and never delay the workflow.

**Dialog contract:** A reusable dialog foundation using Angular CDK Dialog/Overlay
and accessibility capabilities is required for later form/detail milestones.
Evaluate board/column create, rename and deletion confirmations, task create/edit,
deletion and details per milestone; do not implement overlays during MS11.1.
Desktop prefers centered dialogs, especially focused task details rather than an
enterprise side drawer. Use a dark translucent backdrop without theatrical blur,
soft large corners, generous padding, clear title/close affordance and footer
actions where useful. Tokenized SM/MD/LG widths serve confirmations, board/column
forms and task flows. Widths must remain viewport-constrained, with max-height and
contained scrolling. Require dialog ARIA semantics, focus trapping, initial focus,
focus restoration, and Escape/backdrop closing where safe for the current action.
Choose the simplest coherent detail/edit interaction without changing semantics.

Tablet can retain adapted centered dialogs and horizontally navigable Kanban.
On mobile use compact centered confirmations, sheets for suitable short forms,
and near-fullscreen/fullscreen task forms/details, with obvious close/navigation,
safe scrolling and sticky actions where useful. Compact top navigation, dedicated
filter overlays when helpful, >=44px targets and no page-level horizontal overflow
are acceptance criteria, not claims about the current foundation.

**Definition of Done:**

- All existing functionality remains available with a consistent visual language.
- Desktop, tablet and smartphone layouts are intentionally designed.
- Major workflows are comfortable with mouse, keyboard and touch.
- Accessibility semantics and visible focus are preserved or improved; important
  meaning does not depend on color alone, and reduced motion is respected.
- Branding, favicon and browser metadata are complete.
- Loading, empty, error, confirmation and interaction states are polished.
- Frontend tests, production build and repository quality gates pass.
- Portfolio assets may then be refreshed from the real redesigned application.

**MS11.1 COMPLETE — corrected consumer-product foundation (2026-09-27).**
Reopened before acceptance to correct the initial admin-oriented visual values,
then revalidated. Preserved semantic tokens, opt-in control mixins, legacy color
aliases, focus indication, reduced motion and all existing behavior.

The foundation now uses white/warm-neutral surfaces, near-black and readable gray
text, and a restrained TaskFlow teal accent. Subtle surface borders are separate
from accessible input boundaries. Controls use 48px minimum height, generous
padding and 12px corners; the radius scale extends through 16px small cards, 20px
cards, 28px dialogs and pills. Spacing extends to 64/96px for future layouts;
system typography uses comfortable line heights and restrained heading tracking.
Soft elevation is selective, not applied to every card. SM/MD/LG dialog widths,
backdrop and 140/180ms interaction timings are tokens only; no overlay is built.

`npm run quality` passed formatting, lint, all 593 tests in 38 files, coverage
thresholds and the production build outside the sandbox. Coverage: statements
98.36%, branches 96.85%, functions 98.82%, lines 100%. Token references and
whitespace checks passed. Calculated text/status contrast exceeds 4.5:1 and input
border contrast exceeds 3:1 against both white and muted surfaces. These checks
are not browser visual/responsive/accessibility acceptance, which remains MS11.12.
No browser review is claimed. MS11.2+ remain unstarted: branding, shell, product
screens, dialogs, contextual menus, filters and responsive overlays are deferred.
No templates, domain/API/security behavior, dependencies or deployment changed.

**MS11.2 COMPLETE — branding and browser polish (2026-09-27).**
Added an original geometric white T on a softly rounded TaskFlow teal surface
(`#176b60`, matching the existing accent). The canonical `public/taskflow-mark.svg`
also serves as the SVG favicon; no duplicate logo/wordmark asset is needed.
Replaced the starter ICO with a 32px fallback and added a 180px Apple touch icon
with a centered 132px mark and white padding. These raster assets derive from the
SVG via local Quick Look rasterization and `sips`; no dependency was introduced.
Later shell usage should render TaskFlow as real text and treat an adjacent mark
as decorative, or provide an accessible name when the mark stands alone.

The entry HTML now uses the correctly cased TaskFlow title, a concise description,
white browser theme color, minimal Open Graph title/description/type and icon links.
Charset, viewport, base URL and Angular root are preserved; existing route titles
already use TaskFlow. No manifest, PWA infrastructure or remote font was added.
`og:image` is deferred to MS11.12 / the real redesigned portfolio asset refresh.

Validation: SVG XML/viewBox and minimal markup checked; local 512px render, decoded
32px ICO and padded Apple icon visually inspected. All icon references resolve,
and production output contains byte-identical assets. `npm run quality` passed
formatting, lint, 593 tests in 38 files, unchanged coverage thresholds and production
build; `git diff --check` passed. Browser/tab/device acceptance is not claimed.
MS11.1 work is preserved. MS11.3–MS11.12 remain unchecked and unstarted: no shell,
navigation, product-screen, dialog or responsive-layout redesign was performed.

**MS11.3 COMPLETE — application shell and interaction infrastructure (2026-09-27).**
Resumed and retained the interrupted implementation and all MS11.1/MS11.2 work.
The shell now uses the existing mark with real TaskFlow text, authenticated Boards
navigation, a CDK account menu, responsive gutters and compact narrow-screen
branding. The skip link and full-width workspace area remain. Logout retains its
success-only navigation, duplicate-request guard and accessible, recoverable errors;
the idle live region stays accessible and progress uses neutral styling.

Shared native icon buttons require labels and provide comfortable targets and
interaction states. Two consistent stroke icons avoid a new dependency. Installed
CDK Dialog/Overlay powers a small typed TaskFlow wrapper with labelled frame,
focus trapping/initial focus/restoration, Escape/backdrop/close controls, protected
dismissal and scroll locking. SM/MD/LG sizes support centered confirmations,
mobile sheets and fullscreen task-sized panels with contained scrolling and a
non-shrinking header. No actual feature dialogs were introduced. CDK Menu and shared
floating-surface styles support keyboard, Escape and outside dismissal; board,
column and task actions have not been migrated. Reduced motion is respected.

Final `npm run quality` passed formatting, lint, all 603 tests in 40 files, coverage
thresholds and production build. Coverage: statements 98.42%, branches 96.90%,
functions 98.84%, lines 100%. Initial bundle: 367.06 kB within unchanged budgets.
Tests cover auth-aware navigation, skip focus, menu keyboard/outside dismissal,
logout retry/duplicate prevention, labelled icon controls, dialog size/configuration,
focus, dismissal and scroll restoration. Token/link checks and `git diff --check`
passed. No browser was available: desktop/tablet/phone visual acceptance, actual
layout overflow and assistive-technology behavior remain unverified in a browser.
MS11.4–MS11.12 remain unchecked and unstarted. No feature-screen redesign, backend,
API, database, dependency, infrastructure or deployment change was made; no commit
or push was performed.

**MS11.4 COMPLETE — authentication experience (2026-09-27).**
Login and Register now use an open, horizontally centered single-column layout
with a 448px maximum form width, clear heading/supporting copy and no nested card.
The existing signed-out shell remains the single TaskFlow mark/wordmark location;
authenticated navigation and account controls stay absent. Shared 48px controls,
12px corners, reserved feedback space, full-width primary actions and spacious
router cross-links establish a consistent entry experience. Narrow or short
viewports reduce vertical spacing; forms use natural page scrolling and existing
shell gutters rather than fixed viewport heights.

Email/password fields, autocomplete, validation rules, error messages, safe return
navigation and duplicate-submission prevention are unchanged. Visible labels,
error associations and polite live regions remain. A separate progress status
outside the busy form announces submission without moving the layout. No password
reveal, extra field or new authentication feature was added.

`npm run quality` passed formatting, lint, all 604 tests in 40 files, unchanged
coverage thresholds and production build. Coverage: statements 98.42%, branches
96.90%, functions 98.84%, lines 100%. Tests additionally verify error associations,
busy/progress lifecycle and real Login/Register cross-navigation in the brand-only
shell. `git diff --check` passed. No browser was available; desktop/mobile visual,
keyboard-viewport and assistive-technology acceptance remain unverified.
MS11.5–MS11.12 remain unchecked and unstarted. No Boards, Kanban or task redesign,
backend/API/database/dependency/infrastructure changes, commit or push occurred.

**MS11.5 COMPLETE — Boards dashboard (2026-09-27).**
The Boards page now uses a spacious title/action header and a constrained card grid:
three columns on desktop, two below 1024px and one below 640px, with a stacked mobile
header. Cards show actual board names, a large navigation area and separate labelled
CDK contextual actions. Subtle borders, soft geometry and restrained hover/focus
elevation reuse the existing design tokens; no new board metadata was introduced.

Create and Rename use medium TaskFlow dialogs with the existing name form and
validation. Delete uses a small named confirmation with Cancel initially focused.
Submission prevents duplicate requests and unsafe dismissal; server failures remain
recoverable. Keyboard menu navigation, Escape/outside dismissal, input focus,
focus restoration and a removed-card fallback are covered by tests. Loading, empty
and retry states retain accessible announcements. API/state reconciliation, ordering
and board navigation behavior remain unchanged.

`npm run quality` passed formatting, lint, all 607 tests in 40 files, unchanged
coverage thresholds and production build. Coverage: statements 98.63%, branches
97.39%, functions 98.60%, lines 100%. The focused board/acceptance suite passed
62 tests. `git diff --check` and documentation relative-link validation passed.
No browser was available; desktop/tablet/mobile visual acceptance, actual overflow
and dialog/menu appearance remain unverified in a browser.
MS11.6–MS11.12 remain unchecked and unstarted. No Kanban, column, task, filter or
statistics redesign, backend/API/database/dependency/infrastructure changes,
commit or push occurred.

**MS11.6 COMPLETE — Modern Kanban workspace (2026-09-28).** The Board workspace now has a spacious Board navigation/title hierarchy and a contained horizontal Kanban surface. Lanes have stable desktop widths, visible task counts, calm neutral backgrounds, an intentional empty-lane treatment, and smartphone-aware horizontal scrolling; desktop and tablet retain the horizontal workspace. Column create/rename/delete use the shared CDK dialog frame with existing validation and server-error behavior, while Rename, valid Move actions and Delete live in labelled contextual menus. Column ordering and deletion semantics are unchanged.

Task cards now use white soft-edged surfaces, restrained priority and due-date metadata, separate labelled drag handles, and refined CDK preview/placeholder/transition states with reduced-motion support. Existing task detail and create/edit interactions remain in place intentionally; search, filters, sorting and statistics presentation are untouched for MS11.7–MS11.9. Drag/drop still uses Angular CDK and the existing placement API/state behavior. Automated quality evidence and browser/touch acceptance are recorded with the milestone closeout; true device touch drag acceptance remains for MS11.12. No backend, API, database, dependency, infrastructure, commit or push change was made.

MS11.10 COMPLETE — responsive desktop/tablet/mobile UX (2026-09-28).

Validated the existing TaskFlow product experience across representative desktop,
tablet and smartphone widths without redesigning the approved MS11.6–MS11.9
visual language. The Kanban remains horizontally contained on narrow viewports,
filters and menus remain viewport-safe, long labels are constrained, and Task
dialogs remain viewport-bounded with contained scrolling.

Column Move left/right continuity was corrected after browser investigation showed
that the perceived page reload was caused by the Board overview collapsing during
its statistics refresh rather than by a full Board reload. Previously confirmed
statistics now remain rendered during background refresh, preventing the vertical
page jump while preserving backend-authoritative reconciliation. Kanban horizontal
position is preserved and focus returns sensibly to the moved Column control.

Browser checks covered representative widths including 1440, 1024, 768, 430,
390 and 360px. Final automated quality validation and git diff checks passed.
MS11.11 product polish and MS11.12 final visual/accessibility/regression acceptance
remain separate later milestones.

MS11.11 COMPLETE — product polish and interaction states (2026-09-29).

Completed a final product-polish pass across navigation, Boards, Kanban, task
interactions, menus, dialogs, filters and Board overview. Remaining visual
inconsistencies and interaction-state issues identified during manual browser
review were corrected without adding features or changing domain/API semantics.

Hover, focus, active, disabled, pending, empty and validation states were reviewed
for consistency with the established TaskFlow consumer-product visual language.
Responsive sanity checks at representative desktop, tablet and mobile widths
confirmed that the polish changes did not regress MS11.10.

The complete frontend quality gate and git diff validation pass. MS11.12 remains
the separate final visual, responsive, accessibility and regression acceptance.
