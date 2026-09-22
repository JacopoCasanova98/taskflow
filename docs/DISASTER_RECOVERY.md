# Disaster recovery and backup — MS9.7

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

All procedures below are reference decisions for an independently authorized
operator. TaskFlow performs no backup, snapshot, restore, secret retrieval, host
replacement, endpoint switch, AWS query or deployment. See the
[zero-provisioning policy](AWS_ARCHITECTURE.md#hard-project-execution-policy) and
[deployment runbook](DEPLOYMENT_RUNBOOK.md). MS9.8 supplies the
[final smoke/readiness specification](PRODUCTION_READINESS.md), without live execution.

**Regional disaster recovery is NOT provided.** This is one Region (`eu-west-1`),
one application host and Single-AZ RDS. There is no cross-Region RDS backup
replication, Secrets Manager replication contract, multi-Region ECR release
contract, secondary ALB, DNS failover or multi-Region IaC deployment. The two ALB
subnets do not make the application/database highly available. An AZ/database
failure can cause a longer outage than Multi-AZ. These are accepted cost trade-offs,
not an HA or automatic failover design.

## Backup inventory and recovery objectives

| Item | Existing protection / limitation |
| --- | --- |
| RDS business data | Automated backup retention **7 days**; non-zero retention provides PITR capability, subject to actual backup availability |
| RDS deletion | Deletion protection; Terraform `skip_final_snapshot=false`, named final snapshot and `delete_automated_backups=false` |
| CloudFormation RDS deletion/replacement | `DeletionPolicy: Snapshot`, `UpdateReplacePolicy: Snapshot`, `DeleteAutomatedBackups: false`; deletion protection still requires explicit operator handling |
| Manual/final snapshots | Persist until explicitly removed; manual snapshots are optional and are not scheduled by TaskFlow |
| Code/configuration | Reviewed Git and independently retained IaC ownership/state records |
| Release artifacts | Same-commit ECR digest pairs; tagged releases are not auto-expired by current policy; deletion remains possible |
| Runtime credentials | Operator-managed Secrets Manager current/previous versions; not an RDS backup or cross-Region recovery promise |
| EC2/root EBS | Replaceable host; no authoritative application data, no EBS application backup strategy |
| Logs | Four CloudWatch log groups, 14-day retention; diagnostics, not database backups |

AWS Backup is not configured. Backups must actually exist and be usable; IaC intent
does not prove a successful backup. The independent operator retains source DB
resource identity, snapshot/backup identity and encryption-key accessibility outside
Git. Do not delete needed encryption keys, snapshots or release artifacts while
their recovery value remains.

**RPO:** Recovery-point capability is bounded by RDS backup retention and the latest
restorable point reported by RDS (`LatestRestorableTime`), within the available
earliest/latest window. Reference retention is 7 days. Transaction logs are uploaded
approximately every five minutes, which is not an exact five-minute RPO or RPO=0
guarantee. A corruption recovery may intentionally choose an older point and lose
more recent valid work. No live RPO measurement has been performed.
See [RDS PITR](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PIT.html).

**RTO is not guaranteed or measured by TaskFlow.** No live restore drill exists.
Detection/decision time, new Single-AZ DB creation and data initialization, private
data/schema/TLS validation, credential reconciliation, optional host replacement,
application cutover and acceptance dominate recovery. Do not infer an RTO from a
successful static test or container startup time.

## Incident decision and containment

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

Use the [observability first-response map](OBSERVABILITY.md#first-response-map):
backend, Nginx, host and RDS logs plus eight alarms. Preserve bounded evidence and
UTC incident times without copying secrets into tickets/logs. Missing telemetry is
not proof of health; non-blocking delivery can lose events. SNS paging exists only
if an external topic/action and subscriptions are actually configured.

| Incident | Initial decision |
| --- | --- |
| Bad application release, compatible schema | Previous verified image pair via deployment rollback |
| Incompatible migration | Contain impact; choose forward fix/compatible release or explicitly approved data recovery |
| Accidental deletion/corruption | Stop the offending actor/writes, determine UTC boundary and candidate PITR/snapshot |
| RDS instance deleted | Assess still-retained automated backups and final/manual snapshots; recovery is not guaranteed if absent/expired |
| EC2 lost | Replace host, preserve RDS; no database restore solely for host loss |
| Secret compromise | Contain access, rotate affected credentials, assess unauthorized data changes and refresh sessions |
| Region unavailable | Regional DR unsupported; escalate continuity decision, no invented secondary environment |

Identify whether the problem is process, host, network, database or release-related
before destructive action. Preserve the source DB/evidence when safe. Stop corruption
at its source before restoring; otherwise the same code/actor can corrupt the new DB.

## PITR to a new database

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

PITR creates a **NEW DB instance** and endpoint; it does not overwrite the source DB.
The operator authorizes data loss and a recovery candidate explicitly. Do not select
"latest" automatically for logical corruption. See
[AWS restoration semantics](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PIT.html).

| Gate | Operator action | Required outcome |
| --- | --- | --- |
| R1 | Identify incident timestamp in UTC and contain damaging writes | Known recovery intent and evidence preserved |
| R2 | Inspect available restorable window; choose a point BEFORE corruption | Candidate lies within actual earliest/latest restorable times; data-loss decision recorded |
| R3 | Restore to a NEW DB instance | Source preserved; intended private network/configuration explicitly selected |
| R4 | Validate restored configuration and data privately | Roles, Flyway history, representative data, TLS and credentials verified |
| R5 | Choose compatible release and intentional migration path | No automatic "restore then run latest" assumption |
| R6 | Quiesce all TaskFlow writes and switch endpoint | Only one selected database accepts application writes |
| R7 | Materialize/recreate, validate and accept | Controlled recovery record and monitoring/backup continuity |

RDS restoration may use default groups unless the operator selects appropriate
ones; do not assume IaC security settings carry over. On the candidate verify VPC,
DB subnet group, SG allowing only App→DB 5432, `PubliclyAccessible=false`, instance
class, PostgreSQL major/minor compatibility, storage/type/capacity, encryption/key
access, 7-day retention, deletion protection, PostgreSQL log export, parameter/option
groups where relevant, and tags. Verify both actual settings and intended IaC ownership.
See [restore configuration considerations](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_RestoreFromSnapshot.html).

Privately inspect representative users/boards/tasks and recovery-point correctness;
check schema, `public.flyway_schema_history`, checksums/status, object ownership,
`taskflow_app` least privileges and `taskflow_migrator` identity. Verify certificate
chain/hostname with the official CA and verify-full. Never run missing migrations
until the chosen recovery state and compatible application release are understood.
Do not blindly rerun privileged bootstrap over restored objects or manually rewrite
Flyway history to make a version fit.

Restored PostgreSQL passwords reflect the restored state; Secrets Manager may hold
newer values. An authorized DB operator must reconcile app/migrator credentials
before cutover without exposing them or restoring compromised values. Materializing
AWSCURRENT alone cannot change a restored DB password. Master credential access
remains independent of EC2. Restores can also resurrect previously revoked refresh
sessions; review authentication/session containment before reopening writes.

If the recovery point predates migrations, choose the compatible release plus the
desired forward migration path deliberately. An approved migration uses the MS9.4
one-shot migrator and must succeed before that newer backend starts. An older
compatible release is also possible without inventing down migrations.

## Endpoint cutover and recovery acceptance

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

Before switching `TASKFLOW_DB_URL`, quiesce/stop backend and prevent every old host
or one-shot writer from reconnecting. A short single-host maintenance window is
acceptable. Do not run old and new databases with concurrent TaskFlow writes.
Source writes occurring after the chosen restore point are not merged automatically;
authorize their loss or a separately reviewed reconciliation before cutover.

Set the real restored RDS endpoint in the existing verify-full JDBC contract, never
an IP/custom alias. Reconcile current credentials, materialize again and recreate
backend with the chosen compatible release (both images if changing releases).
Keep writes closed until private validation succeeds. Then repeat deployment health,
edge/authentication/application/observability acceptance and reopen deliberately.
If the candidate fails, do not flip back to a source that has diverged or remains
corrupt; choose the authority and reconcile before admitting writes again.

A new instance identifier changes native RDS metric dimensions and its PostgreSQL
log-group path. The independent IaC owner must reconcile the existing RDS/alarm/log
definitions to the recovered resource and pre-create its correct 14-day destination
before export; do not assume the old identifier's logs/alarms monitor the replacement.
Do not give the app role log-group creation or collect RDS logs through the host agent.
Coordinate backup continuity and confirm a meaningful restorable window once the
recovered instance's backups are established. This is an operator ownership change,
not a second permanent database architecture or an automatic stack update here.

Record source/target identities, selected restore point, estimated discarded work,
approved data decision, compatible Git SHA/digest pair, Flyway version, endpoint
switch/acceptance timestamps and verification evidence outside Git. Preserve the
old DB only as long as authorized forensic/recovery value justifies costs; remove
it later through a separate reviewed lifecycle decision, never as part of blind
cutover. Never run both Terraform and CloudFormation against the same resources,
or apply stale definitions that could undo the recovery.

## Snapshot and deleted-instance recovery

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

Snapshot restore also creates a NEW DB instance. Select a verified manual/final
snapshot before the incident when PITR is unavailable or a discrete recovery point
is preferred. Its recovery point is the snapshot's state, not current data. Follow
R3–R7 with the same private configuration/data/role/TLS checks, release choice and
write-quiescence gate. Availability alone does not prove correct data; initialization
can affect performance. No snapshot ID or restore command is populated in Git.

For a deleted instance, determine whether retained automated backups still have a
usable window; retain the source resource identity needed by the operator's restore
workflow. They eventually expire according to backup retention and do not accumulate
new recovery points after deletion. Otherwise inspect surviving final/manual snapshots.
If no usable recovery artifact exists, this design cannot reconstruct lost business data.
Never represent recreating an empty database as successful data recovery.
See [retained automated backups](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.Retaining.html).

Intentional deletion requires separately authorized disabling of deletion protection,
normally a final snapshot, and a deliberate retained-backup decision. Terraform's
reference final snapshot identifier is `<name-prefix>-db-final`; a previous snapshot
with that name can require an independently reviewed unique identifier before a
later deletion. Do not delete a useful snapshot to bypass a naming collision.
CloudFormation Snapshot deletion/replacement policies express protection but are
not identical naming/ownership behavior to Terraform. No deletion is executed here.

Manual/final snapshots persist until explicitly deleted; TaskFlow schedules no
manual snapshots. Retained automated backups expire, so they do not replace final
snapshot retention. Independent operators review inventory, recovery value and
storage charges before removal. Restored instances, retained source instances,
manual/final snapshots and retained backups may incur costs; no precise bill is
claimed. TaskFlow's actual AWS infrastructure cost remains €0.

## EC2 and process failures

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

EC2 contains no authoritative application data. Its loss does not itself imply RDS
data loss. Recovery is independent IaC host replacement → profile/SG/ALB target
attachment → secret-free bootstrap/IMDS guard → reviewed Git configuration and
release record → official CA → Secrets Manager materialization → observability →
known digest pulls → schema validation/appropriate migration → production runtime
and acceptance. Do not restore an old EC2/EBS filesystem backup as application data.
Fence a returning old host. The new EC2 public IPv4 may change: users reach public
hostname → ALB; no DNS record targets EC2. See
[host replacement sequencing](DEPLOYMENT_RUNBOOK.md#reboot-and-host-replacement).

| Failure | Actual mechanism / operator boundary |
| --- | --- |
| Container process exits | `unless-stopped` may restart it; diagnose recurrence |
| Container merely unhealthy | Health status alone does not trigger `unless-stopped`; operator diagnosis required |
| Host lost | Requires host recovery/replacement, not a container restart |
| Database failed | Separate RDS diagnosis/recovery; replacing EC2 does not repair data |
| ALB unhealthy target | Availability signal, not remediation; ALB can fail open when all targets are unhealthy |

Neither the eight alarms nor ALB automatically replaces EC2, repairs schema or
restores RDS. Do not bypass security/health boundaries to make a failing deployment
look healthy.

## Secret compromise

**REFERENCE ONLY — INDEPENDENT AUTHORIZED OPERATOR**

**REFERENCE ONLY — DO NOT RUN AS PROJECT VALIDATION**

Contain the compromised access path and investigate unauthorized data changes before
declaring recovery. Replace a compromised host from reviewed configuration rather
than trusting its secret files. Coordinate DB role/password and Secrets Manager
rotation; rotate the single JWT signing key and assess refresh-session revocation
separately. Follow [rotation and secret rollback decisions](DEPLOYMENT_RUNBOOK.md#credential-and-ca-rotation).
AWSPREVIOUS is not inherently safe: never promote a known-compromised value. A
database restore does not revoke external credentials and may revive older session
state/passwords. Choose any restore point together with credential containment.

## Validation boundary

`scripts/production/tests/test_runbooks.py` checks the documented operational gates
and links against current repository contracts without interpreting or executing
runbook commands. Existing helper tests use fake AWS/Java/firewall programs in
network-disabled containers. No `deploy.sh`, `rollback.sh` or `restore-rds.sh` is
introduced. A static pass is not evidence of a real backup, successful restore,
measured RPO/RTO or live recovery drill. MS9.8's [final smoke/readiness review](PRODUCTION_READINESS.md)
preserves this live-only boundary.
