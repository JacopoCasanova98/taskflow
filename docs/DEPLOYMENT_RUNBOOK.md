# Deployment and rollback — MS9.7

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

Every operational step and command in this document describes an independently
authorized environment. TaskFlow executes none of them: no AWS identity/API, SSM
session, ECR login/pull, secret retrieval, production Compose, agent activation,
public test or infrastructure change. Placeholders must not be populated in Git.
The [zero-provisioning policy](AWS_ARCHITECTURE.md#hard-project-execution-policy)
is authoritative. MS9.8 remains deferred; it owns the detailed smoke/readiness matrix.

This runbook joins the existing [runtime](PRODUCTION_RUNTIME.md),
[release](CONTAINER_RELEASE.md), [bootstrap/secrets](EC2_BOOTSTRAP_SECRETS.md),
[database](RDS_DATABASE_OPERATIONS.md), [edge](EDGE_SECURITY.md) and
[observability](OBSERVABILITY.md) contracts. Use [disaster recovery](DISASTER_RECOVERY.md)
when recovering data or a lost host. No new architecture or deployment automation
is introduced. One operator coordinates the change; do not overlap deployments,
migrations, materialization, rotations or container recreation.

## Systems of record

| Concern | Authority |
| --- | --- |
| Application code and reviewed non-secret configuration | Git repository |
| Infrastructure definition | Terraform or CloudFormation reference IaC; choose one owner |
| Release identity | Reviewed paired frontend/backend ECR manifest digests and release record |
| Persistent business data and refresh-session state | RDS PostgreSQL |
| Runtime application credentials | Secrets Manager values/staging labels, managed by the operator; IaC owns metadata |
| Ephemeral host credentials | `/run/taskflow/secrets`; derived cache, never a backup authority |
| Public database trust | Official regional RDS CA bundle, outside secret storage |
| Runtime host | Replaceable EC2, no authoritative application data |
| Operational logs | CloudWatch Logs; bounded local cache is not durable storage |

The PostgreSQL role password must agree with its Secrets Manager value. Git holds
neither credentials nor actual deployment records. Terraform state/CloudFormation
ownership records remain under the independent provisioner's custody; Git alone
is not a substitute for live resource ownership/state. EC2 filesystem backups are
not the application recovery model.

## First deployment preflight

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

STOP if any required item is absent or inconsistent. TaskFlow does not instantiate
the prerequisites below, and no placeholder value establishes live readiness.

| Boundary | Required operator evidence |
| --- | --- |
| IaC ownership | Approved reference instantiated by one independent Terraform/CloudFormation owner; intended account/environment and `eu-west-1` |
| Network | Expected VPC/subnets/routes/SGs; ALB→host 8080 only, App→DB 5432, private DB, host HTTPS egress; no SSH or public backend port |
| Host | AL2023 x86_64 EC2, instance profile, SSM administration, rootful Docker/Compose, bootstrap tools; IMDSv2 and working persistent DOCKER-USER guard |
| Registry | Two immutable ECR repositories, applicable registry BASIC scan-on-push configuration, complete eligible release pair |
| Edge | ALB, HTTPS/HTTP listeners and target group; external ACM certificate and public DNS/Route 53 A alias if used |
| Database | Intended RDS instance available, PostgreSQL 17, correct endpoint/subnet group/SG, encrypted storage, 7-day backups, deletion protection and PostgreSQL log export |
| Recovery readiness | Real backup availability and meaningful `LatestRestorableTime` once backups exist; previous accepted release record for updates |
| Secrets | Existing application-DB/JWT secret metadata; independently populated valid values; separate privileged/migration credential custody |
| Telemetry | Four IaC-owned 14-day log groups, eight alarms, existing scoped telemetry permissions, reviewed local agent configuration |

Use the independent environment's recorded outputs, not new project queries.
Terraform `app_instance_id`, `database_endpoint`, `ecr_repository_urls`,
`security_group_ids`, `alb_dns_name` and `alb_zone_id` correspond to CloudFormation
`AppInstanceId`, `DatabaseEndpoint`, the two `*EcrRepositoryUrl` outputs, three
`*SecurityGroupId` outputs, `AlbDnsName` and `AlbZoneId`. They are locators, not
credentials or proof that a dependency is healthy.

Verify the full public-edge relationship: public hostname ↔ certificate SAN coverage
and validity ↔ ACM Region eu-west-1 ↔ ALB HTTPS certificate/listener ↔ Route 53 A
alias target/canonical zone ↔ approved-host forwarding rule. Preserve HTTPS default
404, operational blocks and XFF append mode. Nginx trusts only the approved ALB
subnet peers; subnet overrides require the MS9.5 image allowlist review. Wrong or
incomplete edge prerequisites stop deployment acceptance; do not weaken Host/TLS
checks to continue.

## Release eligibility and host preparation

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

Require a reviewed release record with `git_commit`, `frontend_repository`,
`frontend_digest`, `backend_repository`, `backend_digest`, `platform=linux/amd64`,
UTC `build_timestamp`, and optional shared `semantic_version`. Both images must
come from the same full reviewed Git commit. If one artifact is missing, the
digests do not match the record, or commits/platforms differ: STOP; no partial pair.

Use `<repository>@sha256:<digest>` for both `TASKFLOW_FRONTEND_IMAGE` and
`TASKFLOW_BACKEND_IMAGE`. Never deploy `latest`, `prod`, `stable` or `current` tags.
Verify immutable Git-SHA tags, registry manifest digests, amd64 platform and both
completed BASIC scan results against operator vulnerability policy. A push or
Compose render is not a vulnerability gate. TaskFlow does not automatically block
vulnerable images; CI enforcement belongs to MS10.

Via the existing authorized SSM administration model, place reviewed Compose,
helper scripts, agent JSON and non-secret release configuration on the host.
Keep a stable Compose project identity (`taskflow`) and controlled working directory
across updates; changing identity must not create a second competing stack. Verify
delivered files against Git/release artifacts. No SSH or new file-distribution system
is specified; MS10 may package this later. Do not rerun host bootstrap/restart Docker
casually during a healthy routine release.

Prepare the [official eu-west-1 CA bundle](RDS_DATABASE_OPERATIONS.md#verified-tls-and-public-trust-material)
at `/opt/taskflow/trust/rds-ca-bundle.pem`: non-empty parsed PEM, reviewed roots and
validity, root-owned 0644, directory 0755, readable by backend UID 10001. Keep it
outside `/run/taskflow/secrets`. Runtime JDBC must use the intended RDS-generated
hostname, `sslmode=verify-full` and the identical container CA path, with no embedded
credentials or TLS overrides. A readable file alone does not prove TLS validity.

First database deployment only: the independent privileged DB operator privately
runs [bootstrap-database.sql](../scripts/production/bootstrap-database.sql), following
the MS9.4 verify-full/psql password-prompt procedure. Configure `taskflow_migrator`
and `taskflow_app`, defaults and least privileges; stop on unexpected ownership or
role privileges. Do not rerun bootstrap as a restore/adoption shortcut. Runtime
never uses RDS master; the EC2 role cannot retrieve its managed secret. Populate
the application secret with exactly username `taskflow_app` and its matching password.
Migrator credentials remain operator-controlled and separate from app/JWT material.

## Gated release sequence

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

This table is the canonical deployment order. It is a reviewable procedure, not
an executable pipeline; a failed gate stops progression.

| Gate | Action | Continue only when |
| --- | --- | --- |
| D1 | Validate release and preflight | Complete eligible digest pair and dependency evidence |
| D2 | Authenticate host to ECR and pull both digests | Both exact amd64 images available locally; current runtime untouched |
| D3 | Prepare CA/config and materialize secrets | Valid trust, config, complete protected files; no concurrent recreate |
| D4 | Activate/verify host observability | Agent prerequisites resolved, existing log groups/permissions ready |
| D5 | Run controlled Flyway migration | Successful exit using `taskflow_migrator`; compatible schema confirmed |
| D6 | Recreate backend and frontend | D5 succeeded; selected pair and production settings retained |
| D7 | Verify runtime, edge and observability; record acceptance | All acceptance gates pass |

**D2:** EC2 instance role → ECR GetAuthorizationToken → short-lived registry login
→ two digest pulls. No long-lived AWS keys or credentials in containers. Protect
temporary Docker authentication material and remove it after the controlled pull;
never put it in Git or release metadata. Pull failure aborts before replacing or
stopping healthy containers. Verify actual local image identity/platform after pull;
do not substitute a Docker image/config ID for the registry manifest digest.

**D3:** Render the standalone production Compose with only reviewed non-secret
inputs (explicit env file; never merge local Compose). Check the MS9.1–MS9.6 security,
secret/CA mounts and awslogs settings. Immediately before the controlled deployment,
run [materialize-secrets.sh](../scripts/production/materialize-secrets.sh) as root with
the application DB identifier and JWT identifier, in that order. It reads AWSCURRENT
using the host role; never supply master/migrator identifiers. Check successful exit,
the three expected non-empty files and numeric ownership/modes from MS9.3 without
printing contents. Failure leaves the previous generation/current runtime intact:
STOP. Successful exchange still leaves existing container bind mounts on old inodes.

Do not overlap materialization and Docker opening the three file mounts. The helper's
lock coordinates materializers, not Compose. After an interrupted materializer,
ensure no writer is active, inspect only metadata and remove abandoned protected
staging directories deliberately before retry; never print/reuse uncertain material.
`/run` is ephemeral: reacquire after reboot/replacement before backend creation.

**D4:** Ensure backend/nginx/host/RDS log groups and eight alarms exist. Validate
installed journald-capable agent/schema/SELinux prerequisites against the reviewed
[agent JSON](../ops/cloudwatch/amazon-cloudwatch-agent.json). An independent operator
activates the reviewed host file with:

```text
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 -s \
  -c file:/opt/taskflow/config/amazon-cloudwatch-agent.json
```

Inspect host service status and authorized telemetry evidence. On routine deployment,
verify the already configured agent rather than restarting it unnecessarily. Do not
silently bypass failure: a still-serving application may remain available while
monitoring is repaired, but the release cannot be accepted as production-ready.
Docker non-blocking logging does not guarantee awslogs initialization success.

**D5:** Use the exact selected backend release artifact. Preferred one-shot image
invocation overrides its entrypoint to Java, selecting
`-Dloader.main=com.taskflow.operations.DatabaseMigration`, classpath `/app/app.jar`
and `org.springframework.boot.loader.launch.PropertiesLauncher`. Use an isolated
ordinary Docker bridge with the existing host SG egress/IMDS guard (it need not wait
for the Compose application bridge); no host networking or published ports. Preserve
UID/GID 10001, read-only root and writable `/tmp`, no-new-privileges, and mount only
the public CA and a protected ephemeral migration password file owned 10001:10001,
0400. Set the non-secret `TASKFLOW_DB_URL` and `TASKFLOW_MIGRATION_PASSWORD_FILE` path.
Never mount app/JWT/master secrets or expose a password in argv/environment/logs.

The alternative existing [run-migrations.sh](../scripts/production/run-migrations.sh)
requires Java 21 on a controlled private-network runner and `TASKFLOW_BACKEND_JAR`
pointing to the exact release JAR, not an independently rebuilt artifact. Both routes
use the same migration entry point and packaged migrations. No application startup
follows automatically. Capture exit status; protect diagnostics; remove the one-shot
container and ephemeral migration credential after the controlled attempt.

On failure: STOP; do not replace the current healthy application, retry blindly,
edit Flyway history, run as migrator in production Compose or grant DDL to app.
Earlier migrations may have committed before a later failure. Keep old runtime
serving only while schema-compatible and healthy; otherwise contain the incident.
For routine schema changes, require expand/contract compatibility with the running
release. Never rewrite historical migrations. Migration and application deployment
are not one transaction.

**D6:** Only after migration success, recreate both services with the selected pair.
No normal `docker compose down` pre-step. The standalone file keeps long-running
`taskflow_app`, `SPRING_FLYWAY_ENABLED=false`, Hibernate `validate`, Secure cookies,
sanitized proxy processing and protected mounts. Reference command, after all gates:

```text
docker compose --project-name taskflow --env-file '<REVIEWED_NON_SECRET_ENV_FILE>' \
  -f compose.production.yaml up -d --no-build --pull never --force-recreate backend frontend
```

The env file contains non-secret release references/DB URL and paths only. This
single-host Compose architecture is not zero-downtime orchestration: recreation
can briefly interrupt service and can fail partway, leaving a mixed pair. Inspect
both running digests; do not accept a partially switched release.

## Acceptance and deployment record

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

Accept only after migration success + runtime health + edge checks + observability
prerequisites/evidence all succeed. On the host, confirm backend/frontend health,
frontend root response, both expected running digests, no restart loop, runtime
`taskflow_app`, Flyway disabled and successful Hibernate validation. Inspect actual
DB identity through an authorized private check without exposing credentials.

Backend-aware `/internal/health` is an ALB target-probe path. Verify target health
through the actual authorized ALB boundary and backend health; a direct loopback
request to Nginx must return 404, not 200. Never spoof trusted headers or widen its
peer allowlist for diagnosis. Public listener access remains blocked.

Then verify the expected HTTPS hostname/certificate, HTTP redirect, wrong-Host
rejection, blocked Actuator/internal/OpenAPI paths, authentication and representative
application flow. Verify container/host/RDS log destinations and expected metric/alarm
identities; guest metrics need collection time and missing data is not health.
SNS delivery is conditional on an external topic/subscriptions, not assumed.
These checks occur before acceptance; their detailed test matrix remains MS9.8.

Record the Git SHA, frontend/backend digests, migration version, UTC deployment
timestamp, operator and evidence/decision in the independent deployment record.
Keep the previous accepted record. Do not fabricate a live record in Git.

## Rollback and failure decisions

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

| Failure point | Safe decision |
| --- | --- |
| Preflight/scan/config/CA failure | Abort before runtime mutation; no rollback required |
| Either image pull fails | Stop; keep current runtime, no partial pair |
| Secret materialization fails | Stop; old generation and healthy containers remain |
| Agent activation/verification fails | Repair monitoring; do not accept the release or silently bypass logging |
| Migration fails | Stop replacement; investigate partial schema changes; old runtime only if still compatible/healthy |
| Migration succeeds, before switch | Old runtime may continue only if schema-compatible; otherwise contain incident immediately |
| New backend/frontend unhealthy or mixed pair | Assess current schema; restore previous pair only if compatible, otherwise forward fix or data recovery |
| Edge validation fails | Withhold acceptance; distinguish edge/config issue from release issue; roll back pair only if compatible |
| Database corruption | Contain writes and use the recovery decision process |
| EC2 lost | Replace stateless host; do not restore its filesystem as application data |

Code-only rollback: select the previous accepted record → verify both previous
digests/platform and availability → verify compatibility with the CURRENT database
schema → prepare both images and valid current secrets/CA → set both previous digest
references → recreate backend/frontend → repeat health/edge/observability acceptance.
Never retag or overwrite images. Do not run an older migrator as a down-migration
mechanism. There are no automatic Flyway down migrations.

If schema compatibility is unknown or false, STOP image rollback. Choose a forward
fix, a compatible newer release, or [database recovery](DISASTER_RECOVERY.md) with
explicit data-loss acceptance. A restore can discard legitimate writes after its
chosen point; it is not a routine way to undo an application release.

## Credential and CA rotation

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

**Application DB credential:** coordinate a maintenance window and protect the
new value using the MS9.4 password-handling method. Quiesce/stop backend writes,
change PostgreSQL `taskflow_app` password, update the application secret's AWSCURRENT
to the matching value, materialize, then **recreate backend**, verify DB access and
resume acceptance. This is not atomic across DB and Secrets Manager. Keep service
quiesced if either update fails; reconcile the pair deliberately. Existing pooled
connections are not proof the new password works. Do not use `docker restart` to
reload atomically replaced bind-mounted files. Do not give EC2 secret-write or
master-secret access to perform the operator's administrative steps.

**JWT signing secret:** publish valid new 32-byte canonical Base64 material through
the authorized secret owner, materialize and recreate backend. TaskFlow has one
active signing key; previously signed access tokens become invalid after the switch.
No seamless overlap exists. Opaque refresh tokens are DB-backed and are not revoked
merely by changing the signing key; if compromise requires forced logout, explicitly
revoke affected refresh sessions through an independently reviewed security action.
Communicate this security/maintenance event and validate reauthentication behavior.

**Secret rollback:** AWSCURRENT/AWSPREVIOUS labels identify versions, not a transaction
with PostgreSQL. A previous version may be promoted only after checking validity
and incident intent. Database password and promoted value must agree; label movement
alone does not change PostgreSQL. Never restore a known-compromised value. JWT key
rollback can revalidate old tokens; do not undo security containment for convenience.
After any replacement, materialize successfully and recreate backend serially.

**RDS CA rotation:** before relevant CA expiration/server transition, fetch the
updated official regional AWS root bundle, validate identities/validity/PEM, and
atomically install it with the same ownership/path. Recreate backend to remount the
new inode and establish fresh verify-full connections; verify before acceptance.
Never pin a server leaf certificate or disable hostname/certificate validation.

## Reboot and host replacement

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

For a planned reboot, deliberately stop the backend before shutdown and coordinate
recovery so automatic restarts cannot consume missing ephemeral files. After an
unplanned reboot, do not trust Docker's restored containers as ready: `/run` secrets
are gone and startup may fail. Verify bootstrap/IMDS isolation, reacquire secrets,
then recreate backend and validate the pair. A plain restart is not rematerialization.

For host loss, follow [EC2 recovery](DISASTER_RECOVERY.md#ec2-and-process-failures):
independent IaC owner replaces EC2 and restores profile/SG/ALB target attachment →
bootstrap → restore reviewed non-secret Git/release configuration → install CA →
materialize → activate observability → pull known digests → assess schema and run
only appropriate migrations → start runtime and accept. Do not use an old host
filesystem backup. Fence any returning old host to prevent unintended duplicate
runtime/deployments. New public IPv4 is acceptable: DNS points to ALB, never EC2.

Terraform replaces on user-data change; CloudFormation may stop/start an EBS-backed
host for that update without rerunning first-boot preparation. The independent
owner must explicitly choose/verify replacement, rather than equating a template
update with a freshly bootstrapped host. No live IaC command is part of this project.
