# Deployment automation design — MS10.4

**Offline planning only. No deployment performed.** This document owns the MS10.4
intent and dry-run output contract. [MS9.7](DEPLOYMENT_RUNBOOK.md) remains the
operational authority: its D1–D7 order, failure handling and rollback decisions
are unchanged. The [zero-AWS policy](AWS_ARCHITECTURE.md#hard-project-execution-policy)
still applies. [MS10.5 identity/security design](CI_CD_SECURITY.md) defines the identity boundary.
MS10.4 defines WHAT an executor would do; MS10.5 constrains WHO could do it.
The live executor remains disabled until a constrained host execution interface
and migration credential path are independently resolved.

The implemented path is embedded [MS10.3 release pair](REGISTRY_DELIVERY.md) +
non-secret deployment intent → offline validation → deterministic JSON plan.
An independent future live executor is **not implemented**. No AWS interaction,
production Compose startup, registry pull, secret retrieval, migration, DNS/ALB
check or telemetry activation occurs. A generated plan is not deployment evidence.

## Non-secret intent version 1.0

The [intent schema](../ops/deployment/deployment-intent.schema.json) defines a
closed object: every listed field is required, unknown fields are rejected,
and JSON duplicate keys are rejected by the reused MS10.3 loader. The planner
performs the additional URL, Region and release-pair checks described below.
No real or sample live intent/plan is committed; artificial fixtures exist only
in tests.

| Field | V1 contract |
| --- | --- |
| `schema_version` | String `1.0` |
| `environment`, `region` | Exactly `prod`, `eu-west-1` |
| `compose_project`, `compose_file` | Exactly `taskflow`, `compose.production.yaml` |
| `database_url` | Non-secret PostgreSQL JDBC URL for regional RDS, port 5432, database `taskflow`, approved verify-full TLS parameters only |
| `database_app_username` | Exactly `taskflow_app` |
| `database_secret_id` | Exactly `taskflow-prod/database/application` |
| `jwt_secret_id` | Exactly `taskflow-prod/jwt/signing` |
| `rds_ca_path` | `/opt/taskflow/trust/rds-ca-bundle.pem` |
| `cloudwatch_agent_config_path` | `/opt/taskflow/config/amazon-cloudwatch-agent.json` |
| `public_hostname` | Lowercase DNS hostname, no scheme/path/port/wildcard, at most 128 characters |
| `release_pair` | Embedded MS10.3 v1.0 record, validated by the existing release validator |

The exact secret names are non-secret IaC-derived identifiers for the approved
`taskflow-prod` reference environment. V1 deliberately does not generalize ARN,
environment, Compose, telemetry or path overrides; changing those needs a reviewed
contract revision. It does not check that identifiers/resources exist or contain
the correct material. Those remain D1/D3 live requirements.

Database name is derived from the validated `/taskflow` URL path, not duplicated.
The hostname must have the reference `eu-west-1.rds.amazonaws.com` endpoint shape;
no DNS or database connection is attempted. Only `sslmode=verify-full` and
`sslrootcert=/opt/taskflow/trust/rds-ca-bundle.pem` are allowed, once each.
Query parameter order and percent-encoded values are supported as in MS9.4.
Userinfo, credentials, fragments, extra/duplicate parameters, non-approved CA paths,
wrong database/port/Region and TLS overrides fail. URL shape does not establish
endpoint existence, trust-file validity, TLS success or live DB identity.

No password, JWT payload, AWS credential, registry token, migration credential
or master credential field is supported. The closed field set rejects these at
the intent level, and MS10.3 rejects extra embedded pair fields. Exact identifier
and path constraints reject secret payload substitutions; JDBC query allowlisting
rejects credential parameters. Diagnostics never echo candidate values. This is
structural validation, not a general secret scanner: callers must never disguise
secret bytes as otherwise-valid identifiers. Gitleaks remains a separate check.

## Release identity and execution limits

The planner imports [validate_release_pair.py](../scripts/release/validate_release_pair.py)
without changing or duplicating its rules. Invalid or partial pairs produce no
plan. Both repository Regions must additionally agree with `eu-west-1`. Platform
validation remains owned by MS10.3 and requires `linux/amd64`.

`frontend_image`, `backend_image`, `TASKFLOW_FRONTEND_IMAGE` and
`TASKFLOW_BACKEND_IMAGE` derive exclusively from the validator's
`<repository>@sha256:<registry-manifest-digest>` references. Git SHA comes from
the pair; no runtime image/tag override is accepted. Semantic aliases and Git tags
never become runtime selectors. A local image ID falsely declared as a registry
manifest digest is still indistinguishable offline; D1 must verify registry
provenance and separate completed/reviewed scan evidence for both exact artifacts.

The [planner](../scripts/deployment/render_deployment_plan.py) uses Python's standard
library and the existing validator only. `urllib.parse` parses strings; it is not
an HTTP client. There are no process-execution, shell, Docker, AWS, SSH, socket or
HTTP calls, dynamic code evaluation or credential reads. It reads the supplied
intent and repository schemas and serializes output to stdout. It never opens
CA/config/secret paths contained in the intent or runs a helper named in the plan.

```sh
python3 scripts/deployment/render_deployment_plan.py /path/to/non-secret-intent.json
```

Exit 0 means offline structural validation succeeded, not deployment eligibility.
Invalid input exits 1 with a generic stderr diagnostic and no stdout plan. Shell
redirection is an optional caller choice; no output file or deployment record is
created by the planner. It introduces no current time, random ID, username,
machine hostname or workstation path into output. Identical input produces
identical JSON; input objects are not modified.

## Plan version 1.0 and canonical gates

Every output has `plan_schema_version="1.0"`, `execution_mode="dry-run"`,
`live_operations_performed=false` and `deployment_evidence=false`, plus the full
`git_commit`, `environment`, `region`, `platform`, digest-qualified `frontend_image`
and `backend_image`, non-secret `runtime_inputs`, `failure_policy` and `gates`.
Each gate contains `id`, `purpose`, `requires`, `produces`, `contract`,
`live_execution_required=true` and `status="not_executed"`. `produces` describes
evidence a future executor must obtain, never evidence generated by this planner.
Every gate after D1 requires its predecessor; D6 also explicitly requires successful
migration and compatible schema. Missing, extra, reordered or disconnected gates fail.

| Gate | Planned contract; none is executed |
| --- | --- |
| D1 | Validate release and intent structure; require independent authorization, registry/scan evidence, dependency readiness, deployment serialization and live schema-compatibility decision |
| D2 | Prepare/pull both exact amd64 digest references using the existing host-role reference model; abort on failure before disturbing current runtime, remove temporary registry authentication material |
| D3 | Prepare public CA and reviewed configuration; identify DB then JWT inputs to `materialize-secrets.sh`, AWSCURRENT, `/run/taskflow/secrets`, root-only directory and 10001:10001/0400 files; serialize materialization/recreation |
| D4 | Activate/verify the reviewed host agent configuration, pre-created backend/nginx/host/RDS groups, six native and two guest alarms; verify optional external SNS only if configured |
| D5 | Always require the existing `DatabaseMigration` entry point in the exact backend artifact, PropertiesLauncher and `/app/app.jar`; `taskflow_migrator`, externally supplied protected ephemeral credential, UID/GID 10001, read-only root, `/tmp`, no-new-privileges, isolated bridge and public CA; no app/JWT/master secrets |
| D6 | Only after D5 succeeds and schema compatibility is confirmed, recreate backend and frontend with the stable production Compose/project, no build/pull/down pre-step, `taskflow_app`, runtime Flyway disabled and Hibernate validate |
| D7 | Apply the existing smoke/readiness contract: runtime health, both running digests, HTTPS host/certificate, HTTP redirect, wrong-host rejection, operational blocking, auth/application smoke, logs/metrics/alarms and conditional SNS evidence; record independent acceptance only after all pass |

The output contains semantic actions, file identifiers and requirements, not
copy-paste AWS/Docker command sequences. D5 cannot be omitted because migrations
appear unnecessary; the real migration execution must determine the result.
Secret rotation/materialization requires **recreate backend**, not a simple
restart, because existing bind mounts retain old inodes. No helper is executed.

See [runtime](PRODUCTION_RUNTIME.md), [database/TLS](RDS_DATABASE_OPERATIONS.md),
[materialization](EC2_BOOTSTRAP_SECRETS.md), [observability](OBSERVABILITY.md),
[edge](EDGE_SECURITY.md), [smoke](PRODUCTION_SMOKE_TEST.md) and
[readiness](PRODUCTION_READINESS.md) for the authoritative details. The plan does
not prove migrations, telemetry, HTTPS, health, scans or resource availability.

## Failure and rollback boundary

Invalid release pair/intent, missing secret identifier, wrong Region/platform,
wrong Compose identity, secret payload fields or invalid DB/TLS contract → no plan.
No input can assert that live schema compatibility or scan approval already passed.
At execution time, unknown compatibility must stop for an explicit decision;
migration failure stops before D6. Earlier migrations may already have committed.
D6 is not atomic; a mixed runtime pair fails D7 acceptance. Failure is never
converted to a successful dry-run deployment record.

Rollback automation is not implemented or generated. MS9.7's interface remains:
previous accepted release pair + explicit current-schema compatibility decision
→ possible rollback candidate. Old images are not automatically safe after a
schema change. The independent operator owns containment and the decision.

## Verification and ownership

```sh
python3 -m unittest discover -s scripts/deployment/tests -v
python3 -m unittest discover -s scripts/release/tests -v
```

Synthetic tests cover positive deterministic plans, fail-closed inputs and safe
diagnostics, inherited release constraints, gate-order mutations, source-level
process/network capability checks and cross-contract alignment. Existing CI,
readiness and runbook tests remain regression gates. Cached tooling runs with
networking disabled and read-only repository access; tests create temporary
fixtures only. No application suite, Docker build or production Compose is needed.
Normal CI and all runtime/IaC files remain unchanged. MS10.6–MS10.11 are deferred.

Local validation passed with cached `taskflow-cfn-lint:1.57.0`, pulls forbidden,
networking disabled and read-only repository mounts: 13 deployment-plan tests,
11 release-pair tests, four CI contract tests, five production readiness tests and
five runbook tests. The inherited planner/schema were reviewed and retained;
no MS10.3 validator refactor was needed.

**MS10.4 COMPLETE — offline deployment dry-run contract validated, no deployment performed.**
