# TaskFlow Terraform root module

This single root module models the [AWS reference architecture](../../docs/AWS_ARCHITECTURE.md).
MS8.2 establishes the provider, inputs, naming/tags and foundation outputs.
MS8.3 adds networking; MS8.4 adds three Security Groups and their dedicated rules.
MS8.5 adds compute, runtime IAM, ECR, ALB and observability definitions.
There are no data sources or child modules.

## Execution policy and validation

TaskFlow never provisions AWS infrastructure. No AWS account, credentials,
billable resources or live AWS API calls are required. Do not run `terraform apply`
or `terraform destroy`; no plan is required or run for MS8.2–MS8.5. Public Registry and
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
foundation metadata. Resource IDs, ALB DNS and database references belong to MS8.7.

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
required wildcard resource. No ECR push or secret-read grants exist.

Custom telemetry permissions allow CreateLogStream/PutLogEvents only under the
three pre-created log groups, plus PutMetricData restricted to namespace
`TaskFlow/<environment>` (that API requires wildcard Resource).
This avoids the broader managed CloudWatch agent policy; no Parameter Store writes,
log-group creation, retention changes or EC2 discovery are granted.
MS9 agent configuration must use these existing groups and namespace, without
EC2 tag/volume discovery or remote Parameter Store configuration. The mandated
SSM core managed policy retains its AWS-defined SSM read/channel scope.

Exactly frontend/backend private repositories use immutable tags, scan-on-push
and AES256 ECR-managed encryption. Lifecycle policies expire only untagged images
after seven days; tagged active/rollback releases are retained. Nothing was pushed.

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
CloudWatch Agent is installed only; collection/forwarding configuration belongs
to MS9. No disk/memory alarm pretends that a custom metric already exists.

| Native metric alarm | Threshold / window | Missing data |
| --- | --- | --- |
| EC2 StatusCheckFailed | >0 for two 60-second periods | missing |
| EC2 CPUUtilization | average >80% for three 5-minute periods | missing |
| ALB HealthyHostCount | minimum <1 for two 60-second periods | breaching |
| ALB HTTPCode_ELB_5XX_Count | sum >=5 for two 5-minute periods | not breaching |

Dimensions reference the actual instance/ALB/target group. The 5xx alarm measures
ALB-generated errors, not target-generated errors. Alarm action arrays are empty:
no SNS topic/subscription or notification delivery is claimed. MS9/operator design
owns notification integration and threshold calibration.

Static inventory adds 23 instances: one EC2, five IAM resources, two repositories
and two lifecycle policies, six ALB/target/listener resources, three log groups
and four alarms. RDS, DB subnet groups, secret resources and live data sources
remain absent. Secrets and exact scoped read grants wait for MS8.6; ALB DNS,
EC2 IDs, ECR URLs and IAM outputs wait for MS8.7. MS9 owns release/runtime work.

Terraform 1.16.3 / AWS 6.65.0 formatting, validation and textual graph review
passed offline with no AWS environment variables or credential directory.
Required deployment inputs remained unset; provider cache/lock were reused.
Shell syntax passed; bootstrap was not executed. These are schema/reference and
static checks, not proof of AWS service acceptance or bootstrap/runtime success.

## Milestone ownership

MS8.2 owns the foundation, MS8.3 networking, MS8.4 Security Groups and MS8.5 compute.
Database (MS8.6), resource outputs (MS8.7), CloudFormation (MS8.8) and
final IaC verification (MS8.9) remain deferred. Future authoring must preserve
credential-free static validation; no live AWS data sources are introduced here.
MS9 deployment design remains separate and unstarted.
RDS/DB subnet groups and secret metadata/scoped access belong to MS8.6.
