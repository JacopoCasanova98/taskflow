# TaskFlow Terraform root module

This single root module models the [AWS reference architecture](../../docs/AWS_ARCHITECTURE.md).
MS8.2 establishes the provider, inputs, naming/tags and foundation outputs.
MS8.3 adds networking; MS8.4 adds three Security Groups and their dedicated rules.
MS8.5 adds compute, runtime IAM, ECR, ALB and observability definitions.
MS8.6 adds private RDS, application secret metadata and scoped secret reads.
There are no data sources or child modules.

## Execution policy and validation

TaskFlow never provisions AWS infrastructure. No AWS account, credentials,
billable resources or live AWS API calls are required. Do not run `terraform apply`
or `terraform destroy`; no plan is required or run for MacroStep 8. Public Registry and
HashiCorp release downloads are permitted.

With Terraform installed, run from the repository root:

```sh
cd infra/terraform
terraform init -backend=false
terraform fmt -check -recursive
terraform validate
```

Use `terraform fmt -recursive` when editing. Initialization installs the provider;
validation checks configuration against its local schema without AWS authentication.
Do not add fake credentials, developer profiles or provider skip flags.

If the CLI is absent and Docker is available, use the official versioned image
without a system-wide installation. From this directory, replace `terraform`
in the commands above with:

```sh
docker run --rm -v "$PWD:/workspace" -w /workspace hashicorp/terraform:1.16.3
```

Do not forward AWS environment variables or mount credentials. After provider
installation, add `--network none` before `-v` for formatting and validation.

## Versions and dependency locking

Stable releases verified on 2026-09-19: [Terraform 1.16.3](https://releases.hashicorp.com/terraform/1.16.3/)
and [hashicorp/aws 6.65.0](https://releases.hashicorp.com/terraform-provider-aws/6.65.0/).
`required_version = "~> 1.16.0"` admits Terraform 1.16 patch releases only;
the AWS constraint `~> 6.65.0` admits 6.65 patch releases only. Minor/major upgrades
require deliberate review. The generated `.terraform.lock.hcl` records the exact
provider selection and package hashes; retain it in Git and never edit it manually.

Validated with Terraform **1.16.3** (`linux_amd64` official container) and AWS
provider **6.65.0**. `init -backend=false`, `fmt -recursive`,
`fmt -check -recursive` and `validate` passed. Validation ran with networking
disabled, no AWS environment variables and no AWS credential directory; no state
was created. No plan, apply, destroy or AWS CLI command was executed.

For macOS developers (Apple Silicon/Intel) and Linux containers/CI (ARM64/x86_64),
refresh package verification with:

```sh
terraform providers lock \
  -platform=darwin_arm64 \
  -platform=darwin_amd64 \
  -platform=linux_amd64 \
  -platform=linux_arm64
```

Origin-registry initialization supplies signed package checksums (`zh:`); explicit
platform locking also records platform package-content hashes (`h1:`), avoiding
lock-file churn across these environments. See [HashiCorp's lock command](https://developer.hashicorp.com/terraform/cli/commands/providers/lock).
The four-platform command passed: all packages were signed by HashiCorp.
Initialization recorded 16 `zh:` hashes and the Linux x86_64 `h1:` hash;
platform locking added the other three `h1:` hashes without changing version.

## Inputs, names, tags and outputs

| Input | Default | Purpose |
| --- | --- | --- |
| `project_name` | `taskflow` | Naming and Project tag |
| `environment` | `prod` | Production-oriented reference model; no deployed stack |
| `aws_region` | `eu-west-1` | Ireland reference region, configurable in one place |

Name inputs accept lowercase letters, digits and separating hyphens, starting
with a letter. Region validation checks syntax only, not live availability.
`local.name_prefix` is `<project_name>-<environment>`; later resource names follow
`taskflow-<environment>-<resource>` with default inputs. Resource-specific length
limits belong to the owning milestones.

Provider `default_tags` carries `Project = var.project_name` (default `taskflow`,
the variable-derived form of the architecture's TaskFlow label),
`Environment = var.environment` and `ManagedBy = "Terraform"`.
Later resources may add specific tags such as `Component` and `Name`.
Outputs `reference_region`, `environment` and `name_prefix` expose only non-sensitive
foundation metadata. MS8.7 adds the curated resource interface documented below.

## State

The active configuration uses Terraform's default **local state** model: there
is no backend block or remote dependency. The validation workflow does not create
state. Any state is local, potentially sensitive and must never enter Git.
The local `.gitignore` excludes `.terraform/`, state/backup files, crash logs and
the reserved `*.tfplan` artifact convention; it does not exclude the dependency lock.

For an independently operated production system, recommend a private S3 bucket
with encryption, versioning, public access blocked and least-privilege access to
the state and lock objects. Use S3 native locking with `use_lockfile = true`.
[DynamoDB locking is deprecated](https://developer.hashicorp.com/terraform/language/backend/s3);
do not introduce a lock table. No bucket, KMS key or state control plane is created
by TaskFlow.

**REFERENCE ONLY — NOT ACTIVE IN TASKFLOW.** Do not copy this into the active
configuration or initialize against it:

```hcl
terraform {
  backend "s3" {
    bucket       = "<state-bucket>"
    key          = "taskflow/prod/terraform.tfstate"
    region       = "eu-west-1"
    encrypt      = true
    use_lockfile = true
  }
}
```

## Networking (MS8.3)

`networking.tf` defines VPC `10.42.0.0/16` with DNS support and DNS hostnames
enabled, and the following reference topology:

| Logical AZ | Public subnet | Isolated database subnet |
| --- | --- | --- |
| a | `10.42.0.0/24` | `10.42.10.0/24` |
| b | `10.42.1.0/24` | `10.42.11.0/24` |

`vpc_cidr`, `public_subnet_cidrs` and `database_subnet_cidrs` are inputs with
IPv4 CIDR syntax validation. Both maps require exactly `a` and `b`. Overrides
must preserve non-overlapping subnets contained in the VPC; validation does not
implement overlap/containment checks. AZ names derive from `aws_region` plus
`a`/`b` (defaults `eu-west-1a`/`eu-west-1b`). Letters are account-relative logical
labels, not claims about physical AZ identity or live regional availability.

One Internet Gateway attaches to the VPC. Both public subnets share an explicit
public route table with one separate `aws_route`: `0.0.0.0/0` to that gateway.
Public subnets enable automatic public IPv4 assignment for the future application
host's egress; assignment alone does not make the application reachable.
Future ALB placement spans both public subnets; the single host uses one.
MS8.4 defines ingress restrictions through Security Groups.

Both database subnets disable public IPv4 assignment and explicitly associate
with a separate database route table containing only the implicit VPC local
route. There is no Internet/NAT route for that tier. No NAT, Elastic IP,
VPC endpoints or custom NACLs are defined; VPC default NACLs remain in use.
Names derive from `local.name_prefix`; taggable networking resources add `Name`,
`Component = "networking"` and, for tier-specific resources, `Tier`.
Provider common tags are inherited. Root metadata outputs remain unchanged.

Static review confirms one VPC, four subnets across two logical AZs, one IGW,
two route tables, one public default route and four explicit associations
(13 networking resource instances from nine resource blocks).
Offline validation checks locked-provider resource schemas, argument types and
references; the textual dependency graph checks the routing dependencies.
These checks do not prove AWS service acceptance, available AZs or provisionability.
MS8.3 passed formatting, validation and textual graph review using Terraform
1.16.3 / AWS 6.65.0 with `--network none`, no AWS environment variables and no
credential directory. The existing provider cache was reused without initialization,
upgrade or lock-file changes. No state, plan or AWS operation was produced.

## Security (MS8.4)

`security.tf` defines `aws_security_group.alb`, `.app` and `.db` in the VPC.
Names use `${local.name_prefix}-alb-sg`, `-app-sg` and `-db-sg`; tags add
Name, Component=security and Tier=edge/application/database to provider common tags.
All rules use dedicated ingress/egress resources, never inline or legacy rules.

| Group | Direction | TCP port | Peer |
| --- | --- | --- | --- |
| ALB | ingress | 80 | `0.0.0.0/0`, future redirect-only HTTP listener |
| ALB | ingress | 443 | `0.0.0.0/0`, public application entry |
| ALB | egress | 8080 | App SG |
| App | ingress | 8080 | ALB SG only, frontend Nginx |
| App | egress | 5432 | DB SG |
| App | egress | 443 | `0.0.0.0/0`, HTTPS services/bootstrap |
| DB | ingress | 5432 | App SG only |
| DB | egress | none | No initiated outbound traffic |

TaskFlow-owned peers use `referenced_security_group_id`, not subnet/VPC CIDRs.
The provider removes AWS-created default allow-all egress when creating managed
groups; all intended outbound paths are explicit. No allow-all rule is restored.
Security Groups are stateful: database responses to app-established PostgreSQL
connections and application responses to ALB-established connections need no
extra egress or ephemeral-port return rules.

Application public HTTPS egress supports future ECR pulls, SSM, Secrets Manager,
CloudWatch and HTTPS host/bootstrap dependencies in the approved public-egress,
no-NAT/no-endpoint design. It is not destination-restricted to AWS; no speculative
HTTP egress is added. There is no SSH, key pair, self rule, ICMP, IPv6 or separate
backend ingress. Spring Boot remains Docker-internal; host port 8080 is Nginx.
The reference administration strategy is Session Manager over outbound HTTPS;
SSM IAM permissions and instance profile belong to MS8.5, not this milestone.

Static review confirms three groups, four ingress and three egress rules.
Terraform 1.16.3 / AWS 6.65.0 formatting, validation and dependency-graph review
passed with `--network none`, no AWS environment variables and no credential
directory. Existing provider cache/lock were reused without initialization or
upgrade. This verifies schema/types/references, not live AWS behavior. No AWS
resources were created and no state, plan, apply or AWS API operation occurred.

## Compute and entry point (MS8.5)

One `aws_instance.app` uses public subnet a and App SG, with explicit public IPv4
for HTTPS egress in the no-NAT design. Only ALB SG can reach host Nginx on 8080;
no SSH/key pair, direct backend exposure, second host or autoscaling is added.
Single-host downtime is accepted. IMDS endpoint is enabled, IMDSv2 required,
hop limit 2 and metadata tags disabled. Hop limit 2 supports container hosts but
does not isolate containers from metadata: MS9 must implement host-level metadata
blocking before running application containers, which must not rely on IMDS.

| Input | Default / contract |
| --- | --- |
| `ec2_ami_id` | Required, no default; regional Amazon Linux 2023 x86_64 with SSM support |
| `ec2_instance_type` | `t3.medium`; x86_64 compatibility remains an operator check |
| `root_volume_size_gib` | 30; integer, encrypted gp3, delete on termination |
| `acm_certificate_arn` | Required, no default; externally managed certificate in the ALB region |

AMI/ARN validation checks syntax only. A real operator outside this project would
verify AMI owner, architecture, region, current release and volume requirements,
instance availability, and certificate issuance/domain/region before deployment.
No real input values, lookup or account are needed by `terraform validate`.
MS8.1 assigns ALB here but does not explicitly assign ACM/DNS creation to MS8.5:
the certificate is an external boundary; no ACM/Route 53 resource is added.
DNS/certificate lifecycle integration remains a later explicit ownership decision.

The root volume uses default AWS EBS encryption, with no custom KMS or extra disk.
`templates/user-data.sh.tftpl` is AL2023-specific host preparation: fail-fast Bash,
dnf installs Docker/SSM/CloudWatch Agent, systemd enables Docker and SSM, and
root-owned directories use 0750 (config 0700). No application starts, Compose,
ECR authentication, migration or sensitive data are included. Package selection
follows the chosen AL2023 image's repositories; bootstrap has not been executed.
User-data changes replace the host. Explicit dependencies wait for routing,
HTTPS egress and SSM policy because bootstrap needs them on first boot.

### Runtime IAM and ECR

The instance profile uses a role trusted only by `ec2.amazonaws.com`.
`AmazonSSMManagedInstanceCore` supplies SSM administration, not SSM full access.
Custom ECR permissions allow only BatchCheckLayerAvailability, GetDownloadUrlForLayer
and BatchGetImage on the two repository ARNs; GetAuthorizationToken uses the
required wildcard resource. No ECR push exists; MS8.6 adds the separate scoped
application-secret policy documented below.

Custom telemetry permissions allow CreateLogStream/PutLogEvents only under the
three pre-created log groups, plus PutMetricData restricted to namespace
`TaskFlow/<environment>` (that API requires wildcard Resource).
This avoids the broader managed CloudWatch agent policy; no Parameter Store writes,
log-group creation, retention changes or EC2 discovery are granted.
MS9 agent configuration must use these existing groups and namespace, without
EC2 tag/volume discovery or remote Parameter Store configuration. The mandated
SSM core managed policy retains its AWS-defined SSM read/channel scope.

Exactly frontend/backend private repositories use immutable tags
and AES256 ECR-managed encryption. Lifecycle policies expire only untagged images
after seven days; tagged active/rollback releases are retained. Nothing was pushed.

MS8.9 removed deprecated repository-level scanning settings in both IaC forms.
BASIC scan-on-push is a **regional registry prerequisite**, not a repository
property. `manage_ecr_registry_scanning=false` is the safe default: this application
stack does not take over an existing registry's scanning configuration.
An independent registry owner must supply BASIC scan-on-push for the reserved
`<project>-<environment>-*` prefix before any hypothetical release workflow.
The default configuration alone does not establish scanning.

Only when this stack is explicitly chosen as the sole owner of the entire
regional scanning configuration may that input be true. The optional resource
uses BASIC/SCAN_ON_PUSH and a WILDCARD prefix filter, never a catch-all or paid
Enhanced scanning. This still replaces the registry-wide configuration:
[unmatched repositories become manual-scan](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-scanning-filters.html).
Narrow filters are not an ownership boundary. Other environments/stacks must not
independently manage the same singleton. Shared registries should leave this
option off and have their existing owner incorporate the TaskFlow prefix into
its complete policy. Nothing is queried, applied or scanned by TaskFlow.

### ALB and health contract

One Internet-facing IPv4 ALB spans both public subnets using ALB SG; invalid
headers are dropped and deletion protection is enabled. One instance target group
registers the host on HTTP 8080. Port 80 redirects with HTTP 301 to HTTPS 443;
HTTPS forwards to that target using the required external certificate.

TLS policy `ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09` follows
[current AWS guidance](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html)
checked on 2026-09-19: TLS 1.2/1.3 with hybrid post-quantum support.
The high-priority HTTPS rule returns 404 for `/internal/*`, `/actuator` and
`/actuator/*`. HTTP requests only redirect; they never reach the target directly.

Target health probes bypass listeners and use `/internal/health` on HTTP 8080,
200 matcher, 30-second interval, 5-second timeout and 2/3 healthy/unhealthy
thresholds. MS9 must implement Nginx proxying this path to backend
`/actuator/health`; it does not work in the current runtime. No Nginx change was
made, and no healthy target or working deployment is claimed.

### Observability and verification

Log groups `/<project>/<environment>/{backend,nginx,host}` retain 14 days.
MS9.6 supplies host-only CloudWatch Agent configuration and production Docker
awslogs; neither is activated here. Two guest alarms use the configured memory/root
disk metrics. See [observability](../../docs/OBSERVABILITY.md).

| Native metric alarm | Threshold / window | Missing data |
| --- | --- | --- |
| EC2 StatusCheckFailed | >0 for two 60-second periods | missing |
| EC2 CPUUtilization | average >80% for three 5-minute periods | missing |
| ALB HealthyHostCount | minimum <1 for two 60-second periods | breaching |
| ALB HTTPCode_ELB_5XX_Count | sum >=5 for two 5-minute periods | not breaching |

Dimensions reference the actual instance/ALB/target group. The 5xx alarm measures
ALB-generated errors, not target-generated errors. `alarm_topic_arn` defaults to
empty; an optional external standard SNS topic enables ALARM/OK notifications for
all eight alarms. No SNS topic/subscription is created. Same-Region consistency,
topic delivery policy and threshold calibration remain operator checks.

Static inventory adds 23 instances: one EC2, five IAM resources, two repositories
and two lifecycle policies, six ALB/target/listener resources, three log groups
and four alarms. Database/secret additions are recorded under MS8.6; curated
resource outputs are recorded under MS8.7. MS9 owns release/runtime work.

Terraform 1.16.3 / AWS 6.65.0 formatting, validation and textual graph review
passed offline with no AWS environment variables or credential directory.
Required deployment inputs remained unset; provider cache/lock were reused.
Shell syntax passed; bootstrap was not executed. These are schema/reference and
static checks, not proof of AWS service acceptance or bootstrap/runtime success.

## Database and credentials (MS8.6)

`database.tf` defines one RDS PostgreSQL instance with `db_engine_version = "17"`
and `db_instance_class = "db.t4g.micro"` defaults. These are reference assumptions:
the major-only version permits RDS minor selection; actual regional minor/class/AZ
orderability is an external real-operator check, never a project API query.
Database `taskflow` and administrative username `taskflowadmin` are non-sensitive
constants. The DB subnet group contains only database-a/b, while the Single-AZ
instance uses logical AZ a (an account-relative label). Two subnet-group AZs do
not provide HA. Only DB SG is attached, TCP 5432, publicly_accessible=false;
no public route or compute dependency is added.

Storage is fixed 20 GiB encrypted gp3 with default AWS-managed encryption.
No custom KMS, provisioned IOPS, storage autoscaling, replica, proxy or Aurora.
Minor upgrades are automatic; major upgrades are disabled and ordinary changes
wait for maintenance (`apply_immediately=false`).

### Backups and lifecycle

Seven-day automated backups support PITR within the retained recovery window;
they do not provide HA, zero data loss or instant recovery. Single-AZ outages
remain possible. Deletion protection is enabled, final snapshots are required,
and the reference final identifier is `<prefix>-db-final`.
A real operator would need to deliberately disable deletion protection and
choose a fresh final-snapshot identifier if one already exists; a fixed name
cannot be reused across repeated deletion cycles. TaskFlow never performs these
operations. Snapshot tags are copied.

`delete_automated_backups=false` avoids immediate backup removal on instance
deletion; retained backups expire according to the retention policy in effect
at deletion. Final snapshots remain until separately deleted. These behaviors
were checked on 2026-09-19 against the
[locked provider documentation](https://github.com/hashicorp/terraform-provider-aws/blob/v6.65.0/website/docs/r/db_instance.html.markdown)
and [AWS retained-backup documentation](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.Retaining.html).
Native RDS backups suffice; no AWS Backup resources are defined.

### Credential and schema authority

`manage_master_user_password=true` delegates master generation/management to
RDS and Secrets Manager. Terraform supplies no master password and never reads
the secret value. No apply means no credential is actually generated.
The EC2 role has no master-secret access.

Two application-owned Secrets Manager resources define metadata only:
`<prefix>/database/application` (future dedicated-role username/password) and
`<prefix>/jwt/signing` (future TASKFLOW_JWT_SECRET_BASE64, decoding to 32 bytes).
Both use normal service encryption and seven-day deletion recovery. No custom
key, secret version, random provider, secret input or example value exists.
The app role gets only GetSecretValue and DescribeSecret on those two exact ARN
references, with no writes, wildcard secrets, master read or RDS management.
IAM database authentication is explicitly disabled.

Reference operational flow, not executed by TaskFlow: RDS manages the master;
an independent operator would use that identity for controlled bootstrap of a
dedicated `taskflow` PostgreSQL role with minimum required grants, then securely
populate its credentials into the application secret. Application/Flyway use
that role. Terraform creates neither SQL roles nor tables; no SQL provider,
provisioner or user-data migration exists. Flyway alone owns application-schema
migrations and Hibernate remains `validate`. MS9 may document the procedure.

Default RDS PostgreSQL 17 parameters are retained: AWS documents
[`rds.force_ssl=1` for PostgreSQL 15+](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).
MS9 JDBC must still use `sslmode=verify-full` with the RDS CA bundle; server-side
SSL enforcement does not replace client certificate/hostname verification.
No custom parameter group or CA material is stored in Terraform.

### Database observability and validation

Only the PostgreSQL log type is exported to the pre-created
`/aws/rds/instance/<prefix>-db/postgresql` log group with 14-day retention.
RDS explicitly depends on that group to avoid unmanaged initial retention.
This is native [RDS PostgreSQL log export](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_LogAccess.Concepts.PostgreSQL.html),
not EC2 CloudWatch Agent forwarding or app-role log access.

Two AWS/RDS alarms use DBInstanceIdentifier: average CPU >80% and minimum
FreeStorageSpace <5 GiB (5,368,709,120 bytes, 25% of the 20 GiB allocation), each
for three five-minute periods. Missing data remains missing; notifications default
to silent, with optional external SNS ALARM/OK actions. Connections are deferred
pending capacity/baseline evidence.
Enhanced Monitoring and Performance Insights are disabled; Database Insights
stays in [default Standard mode](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_DatabaseInsights.html).
No Advanced tier, extended telemetry retention or extra monitoring role is enabled.
Native metrics/logs are sufficient here; real operational logging/alarm costs
would still apply outside the project's zero-provisioning model.

Eight additions: subnet group, RDS instance, two secret metadata resources,
one scoped IAM policy, one log group and two alarms. No secret value is supplied,
read or generated by Terraform; metadata/ARN references are not credential values.
MS8.6 added no DB or secret outputs, including the administrative master-secret ARN.
Offline validation requires neither deployment inputs nor secret population.
MS8.6 formatting, validation and graph review passed with Terraform 1.16.3 /
AWS 6.65.0, networking disabled, no AWS environment variables or credential
directory, and the existing unresolved AMI/certificate inputs. Provider lock and
outputs are unchanged. No state, AWS API operation, plan, apply or destroy occurred.

## Root-module outputs (MS8.7)

`outputs.tf` is a curated, non-sensitive interface for hypothetical operators,
external integrations and later CloudFormation parity. No concrete resource
values exist: Terraform is never applied and this project has no state.

| Classification | Outputs / stable keys |
| --- | --- |
| Configuration metadata | `reference_region`, `environment`, `name_prefix` |
| Public entry infrastructure | `alb_dns_name`, `alb_zone_id` |
| Operational resource identifiers | `app_instance_id`, `vpc_id` |
| Operational resource identifiers | `public_subnet_ids`, `database_subnet_ids`: a/b maps |
| Operational resource identifiers | `security_group_ids`: alb/app/db object |
| Release integration identifiers | `ecr_repository_urls`: frontend/backend map |
| Private infrastructure endpoint | `database_endpoint`: hostname only |

The ALB's AWS-generated hostname is the canonical infrastructure entry, not the
final TaskFlow domain; its zone ID supports a hypothetical Route 53 alias.
The instance ID supports SSM targeting/diagnostics. EC2 public IP is deliberately
absent because it exists for egress, not direct application entry. ECR URLs support
conceptual external build/release integration without exposing authentication.
The private RDS hostname remains subject to database network/security boundaries;
it is infrastructure metadata, not a credential.

No credential values, master-secret information, application secret ARNs or
unnecessary IAM/log metadata are exposed. Database identifier is omitted because
no external consumer currently needs it; internal alarms reference it directly.
Port 5432 and AMI/certificate/type/storage inputs are not echoed. All outputs
use the default non-sensitive classification; none accesses secret attributes.
Resource/data/module inventory and provider lock remain unchanged. Formatting
and validation use the existing offline, credential-free workflow with required
AMI/certificate inputs unset. No resources or state are created.

## Milestone ownership

MS8.2 owns the foundation, MS8.3 networking, MS8.4 Security Groups and MS8.5 compute.
MS8.6 owns database/secret metadata and scoped access; MS8.7 owns curated outputs.
CloudFormation (MS8.8) and final IaC verification (MS8.9) are complete.
Future authoring must preserve
credential-free static validation; no live AWS data sources are introduced here.
MS9 deployment design remains separate and unstarted.

## Final static audit (MS8.9)

Verified on 2026-09-19 with unchanged Terraform 1.16.3, AWS provider 6.65.0,
cfn-lint 1.57.0 and Gitleaks 8.30.1. Offline `fmt -check -recursive`,
`validate`, `validate -json` and `graph` passed; JSON reports valid=true,
zero errors and zero warnings. No initialization, upgrade or lock change was
needed. Required AMI/certificate inputs stayed unset. Containers used
`--network none` with explicit absence checks for AWS environment and credentials.

Inventory is 47 resource blocks (including one default-off registry-scanning
resource), twelve outputs and no data sources/child modules. Static source
assertions checked security/network/compute/database invariants and compared
literal properties, alarm thresholds/dimensions and bootstrap with CloudFormation.
Graph review confirmed actual references and justified bootstrap-routing/SSM
and RDS-log-group dependencies; no artificial dependencies were added.
Both bootstrap representations passed Bash syntax checks without execution.

The [final parity matrix](../cloudformation/README.md#final-parity-audit-ms89)
records matches and intentional representation differences. Gitleaks passed over
all versioned infrastructure and the AWS/ADR/architecture/roadmap documents.
No secret values or account-specific inputs were found. State/plan files are
absent; cache is ignored and the dependency lock remains tracked and unchanged.
The documented S3 backend is reference-only.

The original live-plan/stack-diff expectation is superseded by static acceptance:
no AWS account, credentials, API, Terraform plan/apply/destroy, CloudFormation
stack/change set or state is required or produced. Validation proves local
schema/reference consistency, not live orderability, provisionability or runtime
success. **MS8.1–MS8.9 COMPLETE — MACROSTEP 8 COMPLETE.** MS9 Deployment Design &
Production Readiness is **NOT STARTED** and remains non-provisioning.

## Bootstrap extension (MS9.3)

The secret-free AL2023 bootstrap now verifies CLI v2/jq/Python/firewall tools,
creates root-only `/run/taskflow` directories through tmpfiles, and installs
Docker pre/post-start metadata guards. The iptables backend is required; native
Docker nftables is unsupported. Terraform and CloudFormation scripts match.
Secret retrieval remains deployment-time reference behavior, never user data;
IAM and secret/ECR/DB resources are unchanged. See the
[bootstrap and secrets contract](../../docs/EC2_BOOTSTRAP_SECRETS.md).

## MS9.5 edge contract

The required public hostname input has no default and must match the external
ACM certificate and Route 53 A alias. HTTPS defaults to 404, priorities 1/2 block
operational/documentation paths, and priority 10 forwards only the approved host.
ALB XFF append mode is explicit. ACM/DNS remain external; no provisioning occurs.
Nginx trusts the reference ALB subnet CIDRs; any subnet override requires reviewing
and rebuilding that image allowlist. See [edge security](../../docs/EDGE_SECURITY.md).
