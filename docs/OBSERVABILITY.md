# Observability and operational monitoring — MS9.6

This is a production reference design, verified locally without AWS credentials,
AWS API calls, telemetry delivery or provisioning. Actual TaskFlow AWS infrastructure
cost remains **€0**. Do not start production Compose or the agent as a project test.
MS9.7 owns deployment ordering and operator response; this document is a triage map,
not an incident or deployment runbook.

## One path per telemetry source

| Source | Transport | Destination | Retention |
| --- | --- | --- | --- |
| Backend stdout/stderr | Host Docker awslogs driver | `/taskflow/prod/backend` | 14 days |
| Nginx access/error stdout/stderr | Host Docker awslogs driver | `/taskflow/prod/nginx` | 14 days |
| Selected host journals and cloud-init output | CloudWatch Agent | `/taskflow/prod/host` | 14 days |
| RDS PostgreSQL server logs | Native RDS export | `/aws/rds/instance/taskflow-prod-db/postgresql` | 14 days |
| EC2 / ALB / RDS service metrics | Native AWS metrics | Existing service namespaces | AWS-managed |
| Guest memory and root disk usage | CloudWatch Agent, every 60 seconds | `TaskFlow/prod` | CloudWatch metric retention |

IaC owns all four existing log groups and their retention. Runtime has no
CreateLogGroup or PutRetentionPolicy permission; the agent omits retention fields.
There are no application file logs, duplicate container collectors, RDS agent
collectors, new log groups, S3 buckets, traces, sidecars or dashboards.

## Container delivery and local visibility

Production Compose selects `awslogs` in `eu-west-1`, with the table's service group,
`awslogs-create-group: "false"`, `mode: non-blocking` and `max-buffer-size: 4m`.
No fixed stream or tag is specified: Docker uses a unique container ID, so recreated
generations have different streams. Stream churn is expected. Docker's host daemon
uses the EC2 instance profile; credentials and IMDS access are not given to containers.
Local development Compose is unchanged.
See [Docker awslogs](https://docs.docker.com/engine/logging/drivers/awslogs/).

Availability is preferred over blocking application stdout/stderr on remote logging.
The bounded non-blocking buffer can drop messages under sustained backpressure;
process/host failures can also lose buffered events. This is not zero-loss logging.
Non-blocking delivery does not guarantee container startup: driver initialization,
stream creation, absent groups or unavailable credentials can still cause startup
failure. IaC prerequisites must exist before a real operator starts the runtime.
See [Docker delivery modes](https://docs.docker.com/engine/logging/configure/).

Docker dual logging remains enabled by default: `docker logs` and
`docker compose logs` can read the bounded local cache for remote drivers. Docker
documents default rotation of five 20 MB files per container, before compression.
No unbounded json-file driver or disabled cache is configured. Network/driver/cache
write failures and buffer overflow can lose locally visible entries too; the cache
is not a durable outage spool. Inspect host daemon failures when remote logs disappear.
See [dual logging and its limitations](https://docs.docker.com/engine/logging/dual-logging/).

## Host agent configuration and lifecycle

The durable [agent JSON](../ops/cloudwatch/amazon-cloudwatch-agent.json) is host-only.
It reads journal entries at info and above from exactly `docker.service`,
`amazon-ssm-agent.service` and `amazon-cloudwatch-agent.service`, using distinct
`{instance_id}/docker`, `/ssm` and `/cloudwatch-agent` streams. It does not collect
the entire journal. These are service lifecycle/diagnostic journals, not a claim
to collect SSM session command transcripts or every SSM application log file.
The agent's own `logfile` is empty so diagnostics go to stderr/journald rather than
another file collector. Debug/SDK-body logging is not enabled; usage telemetry is off.

Current official agent configuration supports journald collection. A real host must
have a journald-capable agent package/schema; the historical MS9.3 install command
does not pin its version. With SELinux, AWS requires agent SELinux policy 1.1.0 or
newer. Validate installed-package compatibility before activation; do not replace
this filtered design with an indiscriminate journal export. Journal restart/cursor
behavior does not promise a full historical backfill.
See [official agent configuration](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-Configuration-File-Details.html).

The sole file source is `/var/log/cloud-init-output.log`, to
`{instance_id}/cloud-init`. This is the documented EC2/AL2023 bootstrap-output path.
MS9.3 user data remains secret-free and unchanged; future bootstrap edits must never
echo secret values, enable shell tracing around credentials, or dump secret files.
See [EC2 user-data diagnostics](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/user-data.html).

MS9.3 installs the agent; MS9.6 supplies configuration without activating it.
A real operator would place the reviewed file at a root-owned host configuration
path and use the following conceptual activation command **outside project validation**:

```text
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 -s \
  -c file:/opt/taskflow/config/amazon-cloudwatch-agent.json
```

No credential file, access key, secret key or alternate role is configured. Both host
collectors rely on the existing instance profile; Docker container IMDS isolation
remains unchanged. MS9.7 owns exact preconditions, activation/restart sequencing,
rollback and response to a failed collector. Host journal/storage capacity also
remains an operator concern; CloudWatch retention does not rotate local system logs.

## Guest metric identity and IAM

Only `mem_used_percent` and `disk_used_percent` are selected. Root `/` is the sole
disk resource, with `drop_device: true`. Both publish only an `InstanceId` rollup;
`drop_original_metrics` suppresses their original series. Thus filesystem type,
path and unstable Nitro device names are not alarm dimensions. Both alarms match
exactly `{InstanceId: application EC2 instance}` in `TaskFlow/prod`. Do not add
another disk mount without revisiting the aggregation and root alarm contract.
Collection is 60 seconds, not high resolution. CPU stays in AWS/EC2; there is no
per-core CPU, diskio, network, procstat, swap, StatsD, OTLP or tracing collector.

The existing custom telemetry IAM policy is unchanged: CreateLogStream and
PutLogEvents on only backend/nginx/host groups; PutMetricData uses Resource `*`
with the required `cloudwatch:namespace = TaskFlow/prod` condition. No broad
CloudWatchAgentServerPolicy, log wildcard or extra describe/read action is needed
for this selection. Official agent source creates streams first and only attempts
group creation when a group is missing; retention inspection is gated on a positive
retention setting. Missing groups therefore fail rather than being provisioned by
the host role. See [agent destination implementation](https://github.com/aws/amazon-cloudwatch-agent/blob/main/plugins/outputs/cloudwatchlogs/internal/pusher/target.go).

The runtime/JSON intentionally target the reference `taskflow/prod`, `eu-west-1`.
IaC project/environment/Region overrides require reviewing these static destinations
and namespace together; no alternate runtime groups are auto-created.

## Alarms and optional delivery

All alarms below are warnings/signals, not automatic remediation. A period must
breach for every evaluation period listed; averages do not prove every individual
60-second sample breached. The new warnings use three 5-minute averages.

| Alarm | Namespace / metric | Statistic and threshold | Evaluation | Missing data |
| --- | --- | --- | --- | --- |
| EC2 status | AWS/EC2 / StatusCheckFailed | Maximum > 0 | 2 × 60 s | missing |
| EC2 CPU | AWS/EC2 / CPUUtilization | Average > 80% | 3 × 300 s | missing |
| ALB healthy | AWS/ApplicationELB / HealthyHostCount | Minimum < 1 | 2 × 60 s | breaching |
| ALB 5xx | AWS/ApplicationELB / HTTPCode_ELB_5XX_Count | Sum >= 5 | 2 × 300 s | notBreaching |
| RDS CPU | AWS/RDS / CPUUtilization | Average > 80% | 3 × 300 s | missing |
| RDS storage | AWS/RDS / FreeStorageSpace | Minimum < 5 GiB | 3 × 300 s | missing |
| Host memory | TaskFlow/prod / mem_used_percent | Average >= 85% | 3 × 300 s | missing |
| Host root disk | TaskFlow/prod / disk_used_percent | Average >= 85% | 3 × 300 s | missing |

`missing` allows INSUFFICIENT_DATA when telemetry stops; it is not proof of health.
There is no separate dead-man alert or insufficient-data notification action.
Native dimensions remain unchanged: EC2 InstanceId, ALB LoadBalancer (plus TargetGroup
for healthy count), RDS DBInstanceIdentifier. ALB 5xx counts ALB-generated failures,
not every application 5xx; consult backend/Nginx logs for target failures.

Terraform `alarm_topic_arn` and CloudFormation `AlarmTopicArn` default to empty.
Their equivalent syntax validation accepts a standard commercial-partition SNS
topic ARN, not FIFO or arbitrary action ARNs. If supplied, all eight alarms send
both ALARM and OK transitions to that external topic, so operators can see recovery.
If omitted, they remain visible but silent. No SNS topic/subscription/address is
created, and the EC2 role needs no SNS permission. A real operator must verify
the topic is in the deployment Region, delivery policy permits CloudWatch, and
subscriptions/destination access are configured; syntax alone proves none of these.

Alarms do not restart containers, replace EC2, fail over RDS or page a human without
external delivery configuration. Thresholds need real workload calibration after
an independently authorized deployment; this project does not query alarms.

## Log safety, correlation and gaps

Backend logging remains stdout/stderr with bounded UUID request IDs in MDC/response
headers. Completion logs use known/redacted paths and omit query strings, headers
and bodies; auth/business logs use stable events and identifiers. Passwords, JWTs,
refresh tokens, Authorization, cookies, CSRF values and secret-file contents are
not logging arguments. Unexpected exception messages still require author review;
stack traces are not automatic secret redaction. No backend logging code changes.

Nginx retains the standard combined access format: trusted RealIP client address,
method/request target, status and response bytes, plus referrer/user-agent. It has
no Authorization, Cookie, request-body or CSRF-header fields. It includes URL query
strings/referrers and IPs, so logs can contain search/user content; secrets must never
be put in URLs. Access to logs must be restricted. Error diagnostics are not a
general redaction mechanism. No new sensitive fields or body logging are added.
Backend completion logs provide duration; Nginx timing customization is deferred.

Backend request IDs are validated UUIDs, but caller-selected UUIDs are correlation
labels, never security identities or proof of uniqueness. Nginx does not log the
backend ID today. Cross-layer correlation remains future hardening; MS6 is not
redesigned. No OpenTelemetry, X-Ray, Application Signals or dashboard is added.

Nginx sees requests after ALB listener acceptance. ALB listener-rejected requests
are absent from Nginx logs. Native health/5xx metrics do not replace ALB access logs;
enabling those logs requires explicit S3 ownership, cost and policy decisions
outside this scope. RDS export stays native with 14-day retention; no enhanced
database telemetry is enabled.

## First-response map

| Signal | First inspection |
| --- | --- |
| ALB healthy alarm | Target/backend health; remember ALB fail-open limitation |
| ALB 5xx | Nginx and backend logs, target connectivity |
| EC2 status | Instance/system status |
| EC2 CPU | Host/application load |
| Host memory | Application/host pressure |
| Host disk | Docker/cache/log/storage pressure |
| RDS CPU | Database workload |
| RDS free storage | Database growth |
| Backend errors | Backend log group, request ID |
| Edge/auth/rate-limit issues | Nginx log group; distinguish expected 429s |
| Host/bootstrap/Docker issues | Host log group and local service journals |
| PostgreSQL server issues | RDS PostgreSQL log group |
| Missing guest metrics/logs | Agent/daemon state, credentials source, Region/group/IAM/config consistency |

## Cost and verification boundary

Native EC2/ALB/RDS metrics exist independently of the agent. The two guest series
are custom metrics; deployed log ingestion/storage, custom metrics and alarms
incur charges, and optional SNS may incur messaging charges. No precise bill is
claimed. TaskFlow activates none of them and provisions no AWS infrastructure.

Focused `scripts/production/tests/test_observability.py` assertions check JSON,
collection/dimensions, log destinations, Compose, all eight alarm contracts, optional
SNS syntax/actions and IAM/retention parity. Render Compose with ephemeral reserved
inputs and `--env-file /dev/null`; do not start a container with awslogs. Terraform
fmt/validate/graph and cfn-lint run in cached network-disabled containers; existing
edge/bootstrap checks guard earlier contracts. Agent binary validation is optional;
no cached agent binary is present, so validation here is static, not a real-host
journald, IMDS, metric-delivery or notification test. Consult the installed official
agent schema on a real host before activation. MS9.7–MS9.8 remain unstarted.
