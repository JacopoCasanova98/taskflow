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

## MacroStep 10 — CI/CD and portfolio preparation

- [x] MS10.1 — GitHub Actions baseline
- [x] MS10.2 — Docker CI
- [x] MS10.3 — Registry delivery design
- [ ] MS10.4 — Automated deployment design / dry-run contract
- [ ] MS10.5 — CI/CD identity and secrets security design
- [ ] MS10.6 — Branch / PR quality gate
- [ ] MS10.7 — Professional README
- [ ] MS10.8 — Diagrams
- [ ] MS10.9 — Portfolio assets
- [ ] MS10.10 — GitHub release
- [ ] MS10.11 — Final repository cleanup

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
This design milestone requires no live ECR execution. MS10.4–MS10.11 are deferred.

GitHub CI is real and executable; local/Docker build automation may also be real.
ECR delivery and AWS deployment remain reference/design only, with no AWS
credentials required. No deployed-app URL will be fabricated: MS10.9 uses
screenshots, demo and local evidence instead. GitHub Releases may be real because
they do not require AWS provisioning. These adaptations do not authorize any
later milestone's implementation during MS10.3.
