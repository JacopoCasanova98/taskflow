# TaskFlow CloudFormation alternative

`taskflow.yaml` is the MS8.8 alternative representation of the completed
[Terraform root module](../terraform/README.md), which remains the primary
implementation of the [approved architecture](../../docs/AWS_ARCHITECTURE.md).
They express the same reference stack and must never concurrently manage the
same live resources.

**TaskFlow never provisions either implementation.** No AWS account, credentials,
stack, change set, resource, registry authentication or secret value is required.
Do not deploy this template or call the AWS `validate-template` API. Acceptance
is local/static; no concrete output values or working runtime are claimed.

## Inputs and topology

| Parameters | Defaults / contract |
| --- | --- |
| `ProjectName`, `Environment` | `taskflow`, `prod`; modelled environment only |
| `VpcCidr` | `10.42.0.0/16`, DNS support and hostnames enabled |
| `PublicSubnetACidr`, `PublicSubnetBCidr` | `10.42.0.0/24`, `10.42.1.0/24` |
| `DatabaseSubnetACidr`, `DatabaseSubnetBCidr` | `10.42.10.0/24`, `10.42.11.0/24` |
| `Ec2AmiId` | Required, no default; regional AL2023 x86_64 image with SSM support |
| `Ec2InstanceType`, `RootVolumeSizeGiB` | `t3.medium`, 30 GiB encrypted gp3 |
| `RootDeviceName` | `/dev/xvda`; verify against the chosen AMI before any independent real deployment |
| `AcmCertificateArn` | Required, no default; externally managed certificate in the ALB region |
| `DbEngineVersion`, `DbInstanceClass` | `17`, `db.t4g.micro`; orderability is not queried |

Region comes from `AWS::Region`, with validation targeted at Ireland
(`eu-west-1`). AZ labels append a/b to that region. Letters are account-relative,
not physical-AZ guarantees; linting cannot establish availability or capacity.
CIDR overrides must preserve containment and non-overlap. AMI owner, architecture,
root-device mapping, integer volume sizing and certificate validity remain real
operator checks. The typed AMI parameter does not cause offline cfn-lint to query
AWS. AWS documents [AMI-dependent root device names](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/device_naming.html);
the AL2023-oriented default is not a universal device-name guarantee.

Both public subnets share the sole Internet default route through the IGW.
Both database subnets use a separate local-only route table and disable public
IPv4 mapping. There is no NAT, endpoint, custom NACL, IPv6 or extra host.
Names derive from `<ProjectName>-<Environment>`; supported resource properties
explicitly carry Project, Environment and ManagedBy=CloudFormation tags.

## Security and runtime boundaries

| Boundary | Allowed TCP traffic |
| --- | --- |
| Internet → ALB | 80 (redirect only), 443 |
| ALB → App | 8080, SG reference in both directions |
| App → Database | 5432, SG reference in both directions |
| App → Internet | 443 for public HTTPS services/bootstrap |
| Database initiated external egress | None |

Each base SG includes AWS's documented `127.0.0.1/32`, protocol `-1` sentinel.
It suppresses the automatically created allow-all egress rule without adding a
routable path. All seven actual ingress/egress rules are standalone resources;
cross-SG references cannot create a group-definition cycle. This is a
[CloudFormation implementation detail](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-resource-ec2-securitygroup.html),
not an architectural egress permission. SGs are stateful; no ephemeral return
rules, SSH, ICMP, self rules, backend host ingress or public DB access are added.

One EC2 host in public subnet a uses App SG, an EC2-only role and instance profile.
The primary network interface explicitly assigns public IPv4 for egress; it
does not make the host the public application entry. Session Manager replaces
SSH through AmazonSSMManagedInstanceCore. IMDSv2 is required, endpoint enabled,
hop limit 2 and metadata tags disabled. MS9 still owns container metadata isolation.
Root storage is gp3, encrypted and deleted with the instance.

IAM permits ECR pull only from frontend/backend repositories, log-stream creation
and writes only under the three application log groups, and reads only from the
two application-owned secrets. The only wildcard IAM resources are
GetAuthorizationToken and PutMetricData, whose APIs require them; metric writes
are constrained to `TaskFlow/<environment>`. No master-secret access, secret
writes, ECR push, RDS administration or broad full-access policy is granted.
CloudFormation LogGroup.Arn already includes the stream wildcard suffix.

Embedded user data matches Terraform host preparation: fail-fast Bash installs
Docker, SSM and CloudWatch agents; enables Docker/SSM; creates root-owned
directories (0750, config 0700). It contains no application deployment, image
pull/login, migration or secret. CloudWatch collection configuration remains MS9.

The IPv4 Internet-facing ALB spans both public subnets, drops invalid headers and
has deletion protection. HTTP redirects to HTTPS; the certificate is external,
with TLS policy `ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09`. The single instance
target uses HTTP 8080 and `/internal/health`, matcher 200, interval/timeout 30/5
seconds and healthy/unhealthy thresholds 2/3. Public HTTPS requests to
`/internal/*`, `/actuator` and `/actuator/*` receive 404. Health probes bypass
listeners. Nginx health proxying is an unimplemented MS9 runtime contract.
No ACM or Route 53 resources are defined: the final Terraform boundary supersedes
earlier architecture prose about possible DNS/certificate definitions.

## Database, secrets and observability

One private Single-AZ PostgreSQL 17 instance uses logical AZ a, only DB SG, and
the two isolated database subnets. Storage is fixed 20 GiB encrypted gp3, without
custom KMS, IOPS tuning or autoscaling. Seven-day automated backups support PITR;
they do not provide HA or guarantee zero data loss. Deletion protection, snapshot
lifecycle policies and retained automated backups preserve recovery intent.
Minor upgrades are automatic; major upgrades, immediate changes, IAM DB auth,
Performance Insights and Enhanced Monitoring are disabled. Database Insights
remains Standard.

RDS manages the master credential; IaC never supplies or reads its value.
Application DB and JWT secrets contain metadata only, using default service
encryption. Their values are neither generated nor populated. The future JWT
value must decode to the application's required 32-byte key. An independent
operator would bootstrap the dedicated application DB role and populate its
secret outside IaC. No SQL runs here; Flyway remains schema authority and
Hibernate remains validation-only. MS9 owns JDBC `sslmode=verify-full` and CA
bundle/runtime integration.

Four log groups retain 14 days: backend, nginx, host and the native PostgreSQL
export. RDS explicitly depends on its pre-created log destination. Six native
alarms mirror Terraform exactly:

| Signal | Threshold and evaluation |
| --- | --- |
| EC2 StatusCheckFailed | Maximum >0, 2 × 60s; missing data stays missing |
| EC2 CPUUtilization | Average >80%, 3 × 300s; missing |
| ALB HealthyHostCount | Minimum <1, 2 × 60s; missing is breaching |
| ALB HTTPCode_ELB_5XX_Count | Sum ≥5, 2 × 300s; missing is not breaching |
| RDS CPUUtilization | Average >80%, 3 × 300s; missing |
| RDS FreeStorageSpace | Minimum <5 GiB (one quarter of 20 GiB), 3 × 300s; missing |

Dimensions reference the instance ID, ALB/target-group full names and DB
identifier. No notification actions, SNS/email or custom agent metric is claimed.

## Parity and intentional representation differences

| Terraform concept | CloudFormation representation / difference |
| --- | --- |
| Variables and provider region | Parameters; `AWS::Region` replaces `aws_region` |
| Provider default tags | Explicit common tags on resources whose schema supports Tags; InstanceProfile has no Tags property |
| VPC/network | Same topology; separate VPCGatewayAttachment and required gateway dependencies |
| Dedicated SG rules | Standalone ingress/egress resources plus non-routable sentinels to suppress default egress |
| EC2 public IPv4 | Primary NetworkInterfaces owns subnet/SG/public-IP settings; no conflicting top-level settings |
| Root block device | Additional RootDeviceName parameter; instance tags propagate to root volume instead of Terraform's distinct app-root Name |
| User-data templatefile | Identical embedded Base64 script; no unnecessary Sub because it has no substitutions |
| EC2 user-data update | Native CloudFormation EBS-backed instance update stops/restarts; unlike Terraform replace-on-change, it does not enforce replacement or rerun bootstrap. Independent operator replacement strategy remains MS9 |
| IAM role/profile/policies | Role embeds managed-policy attachment and three inline policies; permissions match |
| ECR repositories/lifecycle | Lifecycle JSON is a repository property, not a separate resource; immutable tags and seven-day untagged cleanup match |
| ALB/target attachment | TargetGroup embeds Targets; listeners, health checks and public health-path block match |
| RDS lifecycle | DeletionPolicy/UpdateReplacePolicy Snapshot replace Terraform's named final snapshot; deletion protection and DeleteAutomatedBackups=false match |
| Application secret recovery | Retain on deletion/replacement preserves metadata; not an exact seven-day recovery window |
| Observability | Same four log groups, retention, six alarms, thresholds and dimensions |
| Output maps | Explicit flat outputs preserve each logical key without transforms/serialization |

CloudFormation [retention and snapshot policies](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-attribute-deletionpolicy.html)
and [replacement policies](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-attribute-updatereplacepolicy.html)
do not bypass RDS deletion protection. A real operator must deliberately resolve
that safeguard before deletion. Retained automated backups expire with retention;
final snapshots persist until separately deleted. CloudFormation chooses snapshot
identifiers; it does not reproduce Terraform's fixed final-snapshot name.
Retained application secrets require separate operator lifecycle handling.
[Omitting both secret-value properties](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-resource-secretsmanager-secret.html)
defines empty secret metadata, not a generated application credential.

Repository scan-on-push intentionally matches Terraform.
[AWS marks repository scanning configuration as deprecated](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-resource-ecr-repository.html)
in favor of registry configuration. MS8.9 should review both implementations
together; MS8.8 does not independently redesign scanning.

## Non-sensitive output interface

Foundation outputs are ReferenceRegion, Environment and NamePrefix.
AlbDnsName is the canonical infrastructure hostname (not the final domain);
AlbZoneId supports hypothetical alias integration. AppInstanceId supports SSM/
diagnostics, and VpcId identifies the network boundary.

PublicSubnetAId/BId and DatabaseSubnetAId/BId correspond to Terraform's a/b maps.
AlbSecurityGroupId, AppSecurityGroupId and DatabaseSecurityGroupId correspond to
its SG object. FrontendEcrRepositoryUrl and BackendEcrRepositoryUrl correspond to
its release-integration map. DatabaseEndpoint is a private hostname, not a
credential. These are 17 flat outputs for Terraform's twelve structured outputs.

No EC2 public IP, credential value, master-secret information, application-secret
ARN, IAM document, log-group list or redundant deployment input is exposed.
Nothing exists to resolve these references because neither implementation runs.

## Local/static validation

Stable [cfn-lint 1.57.0](https://github.com/aws-cloudformation/cfn-lint/releases/tag/v1.57.0)
was verified against GitHub release metadata and [PyPI](https://pypi.org/project/cfn-lint/1.57.0/)
on 2026-09-19 (release date 2026-09-17). It validates CloudFormation YAML intrinsics,
resource schemas and additional rules. No host Python/Homebrew installation is
needed. Prepare an isolated local image with public downloads only:

```sh
docker build -t taskflow-cfn-lint:1.57.0 - <<'EOF'
FROM python:3.13-slim
RUN pip install --no-cache-dir cfn-lint==1.57.0
ENTRYPOINT ["cfn-lint"]
EOF
```

Then from the repository root, use the cached image/schemas offline:

```sh
docker run --rm --network none \
  -v "$PWD/infra/cloudformation:/work:ro" \
  taskflow-cfn-lint:1.57.0 --regions eu-west-1 --template /work/taskflow.yaml
```

Do not forward AWS environment variables or mount credential directories.
Required AMI/certificate inputs remain unset. Do not refresh schemas during the
offline check or use AWS validate-template. The image is local tooling only,
not a repository/application Docker artifact.

MS8.8 validation passed with cfn-lint 1.57.0, no warnings/errors, networking
disabled and explicit checks confirming no AWS environment or /root/.aws.
Intrinsic-aware parsing, static security/parity review and Bash syntax checks
also passed. These checks do not prove service acceptance, orderability, bootstrap
success or runtime health. Terraform remains unchanged. MS8.9 final verification
and MS9 runtime/deployment design remain unstarted.
