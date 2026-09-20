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
| `ManageEcrRegistryScanning` | `false`; opt in only for sole regional registry-scanning ownership |

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

MS8.9 removed repository-level scan configuration from both implementations
because [AWS deprecates it](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-resource-ecr-repository.html).
The optional RegistryScanningConfiguration uses BASIC/SCAN_ON_PUSH with the
reserved `<ProjectName>-<Environment>-*` prefix. It is default-off in both forms:
registry scanning is account/region-wide and replaces the entire configuration.
[Unmatched repositories become manual-scan](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-scanning-filters.html);
filters limit scan selection, not ownership or side effects.

With the default, BASIC scan-on-push is an external registry-owner prerequisite;
the application stack alone does not establish scanning. Only an independent
operator granting this stack sole regional ownership should enable the option.
Shared registries must retain their existing owner and incorporate the TaskFlow
prefix into that owner's full policy. Multiple stacks/environments must not
compete for this singleton. No Enhanced/Inspector scanning or runtime IAM change
is introduced. Immutable tags, AES256 and seven-day untagged cleanup are unchanged.

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
success or runtime health. Terraform was unchanged during MS8.8; the coordinated
MS8.9 ECR correction and final verification are recorded below.

## Final parity audit (MS8.9)

Audited from committed MS8.8 baseline `4332b2b` on 2026-09-19. Classification
applies to final code, including the coordinated scanning correction; no DEFECT
remains open in the audited scope.

| Category | Classification | Evidence / representation |
| --- | --- | --- |
| Region model | INTENTIONAL REPRESENTATION DIFFERENCE | Terraform provider input / AWS::Region; Ireland validation |
| Naming | MATCH | Project-environment prefix and resource suffixes |
| Tags | INTENTIONAL REPRESENTATION DIFFERENCE | Default / explicit tags, tool-specific ManagedBy; profile schema lacks Tags |
| VPC | MATCH | 10.42.0.0/16; DNS support/hostnames |
| Public subnets | MATCH | 10.42.0.0/24 and 10.42.1.0/24; AZ a/b; public mapping |
| Database subnets | MATCH | 10.42.10.0/24 and 10.42.11.0/24; AZ a/b; mapping off |
| IGW | INTENTIONAL REPRESENTATION DIFFERENCE | Inline attachment / explicit GatewayAttachment |
| Public route | MATCH | One 0.0.0.0/0 IGW route, public associations only |
| Database isolation | MATCH | Local-only table; no NAT, endpoints or custom NACL |
| ALB SG, App SG, DB SG | MATCH | Three VPC trust boundaries; no SSH/backend/public DB ingress |
| Security rules/default egress | INTENTIONAL REPRESENTATION DIFFERENCE | Same seven routable rules; CFN-only non-routable sentinels |
| EC2 | MATCH | One AL2023 x86_64 input, t3.medium default, public-a, App SG/profile |
| Public egress IP | INTENTIONAL REPRESENTATION DIFFERENCE | Instance attribute / primary NetworkInterfaces; no direct entry |
| IMDS | MATCH | Enabled, v2 required, hop 2, metadata tags disabled |
| Root EBS | INTENTIONAL REPRESENTATION DIFFERENCE | Same encrypted 30 GiB gp3/delete policy; CFN device input and propagated tags |
| User data | INTENTIONAL REPRESENTATION DIFFERENCE | Same normalized script; templatefile / embedded Base64; native update behavior differs |
| IAM trust | MATCH | EC2-only role and instance profile |
| SSM | MATCH | AmazonSSMManagedInstanceCore; no SSH |
| ECR pull IAM | MATCH | Three pull actions on two repos; authorization-token wildcard only |
| Telemetry IAM | MATCH | Exact app log streams; namespace-constrained metric writes |
| Secret-read IAM | MATCH | GetSecretValue/DescribeSecret on app DB/JWT only; no master read |
| ECR repositories | MATCH | Frontend/backend, immutable, AES256 |
| ECR lifecycle | INTENTIONAL REPRESENTATION DIFFERENCE | Separate resources / repository property; untagged seven-day expiry |
| ECR scanning | MATCH | Deprecated properties removed; same default-off ownership guard and BASIC prefix rule |
| ALB | MATCH | Public IPv4, both public subnets, ALB SG, invalid-header drop/deletion protection |
| Target group/attachment | INTENTIONAL REPRESENTATION DIFFERENCE | Same instance HTTP 8080; separate attachment / embedded Targets |
| Health check | MATCH | /internal/health, 200, interval 30, timeout 5, thresholds 2/3 |
| HTTP listener | MATCH | 301 redirect 80 → HTTPS 443 |
| HTTPS listener | MATCH | Same external ACM input and TLS13-1-2-Res-PQ-2025-09 policy |
| Internal health block | MATCH | Priority 1; /internal/*, /actuator, /actuator/* → 404 |
| Application DB/JWT metadata | INTENTIONAL REPRESENTATION DIFFERENCE | Same empty secret definitions; TF recovery seven days / CFN Retain |
| DB subnet group | MATCH | Database-a/b only |
| RDS PostgreSQL | MATCH | 17, db.t4g.micro, taskflow, private Single-AZ a, DB SG, 5432 |
| Master credential model | MATCH | Service-managed taskflowadmin; no password input/value/read |
| DB storage | MATCH | Fixed encrypted 20 GiB gp3; no custom KMS |
| Backups | MATCH | Seven-day PITR window; retain automated-backup intent; not HA |
| Deletion safety | INTENTIONAL REPRESENTATION DIFFERENCE | Protection in both; named final snapshot / Snapshot lifecycle policies |
| DB maintenance/monitoring | MATCH | Minor auto, major/immediate/IAM-auth/PI/Enhanced Monitoring off; Standard Insights |
| Log groups | MATCH | Backend/nginx/host/PostgreSQL, 14 days; DB waits for log destination |
| EC2 alarms | MATCH | Status failure and CPU; thresholds/windows/dimensions verified |
| ALB alarms | MATCH | Healthy hosts and ALB-generated 5xx; thresholds/windows/dimensions verified |
| RDS alarms | MATCH | CPU and <5 GiB storage; thresholds/windows/dimensions verified |
| Outputs | INTENTIONAL REPRESENTATION DIFFERENCE | Twelve structured / seventeen flat; same non-sensitive interface |

Final inventory: **49 resource declarations**, of which registry scanning is
conditional (48 with default ownership), **15 parameters** and **17 outputs**.
Type counts: VPC 1, Subnet 4, InternetGateway 1, VPCGatewayAttachment 1,
RouteTable 2, Route 1, SubnetRouteTableAssociation 4, SecurityGroup 3,
SecurityGroupIngress 4, SecurityGroupEgress 3, Instance 1, Role 1,
InstanceProfile 1, ECR Repository 2, RegistryScanningConfiguration 1 conditional,
LoadBalancer 1, TargetGroup 1, Listener 2, ListenerRule 1, Secret 2,
DBSubnetGroup 1, DBInstance 1, LogGroup 4, Alarm 6.
Terraform has 47 resource blocks and twelve outputs; counts differ by modelling.

Pinned cfn-lint 1.57.0 passed with zero errors/warnings for eu-west-1, both with
the source default and a temporary copy changing only scanning's default to true.
AMI/certificate inputs stayed unset. Intrinsic-aware assertions checked all
security boundaries, output exclusions and lifecycle settings; Terraform source
assertions compared defaults, resource properties and all alarm thresholds/
dimensions. Normalized bootstraps match and both pass Bash syntax checks.
Terraform 1.16.3 / AWS 6.65.0 fmt, validate (including JSON) and graph passed.
Gitleaks 8.30.1 passed for all versioned infra and the architecture/ADR/roadmap
scope. No credential values or account-specific inputs were found.

All execution checks used cached tools, `--network none`, no AWS environment or
credential directory. No rule suppressions or tool/provider upgrades were needed.
Current AWS docs still support the SG sentinel and selected TLS policy. Explicit
Standard Database Insights and disabled advanced telemetry avoid relying on
changing service defaults. No additional relevant deprecation required changes.
The original live plan/stack-diff expectation is superseded by local/static checks.
No AWS API, stack/change set, validate-template, state or resource was produced.
**MS8.9 COMPLETE — MACROSTEP 8 COMPLETE. MS9 NOT STARTED.**

## Bootstrap extension (MS9.3)

The secret-free AL2023 bootstrap now verifies CLI v2/jq/Python/firewall tools,
creates root-only `/run/taskflow` directories through tmpfiles, and installs
Docker pre/post-start metadata guards. The iptables backend is required; native
Docker nftables is unsupported. Terraform and CloudFormation scripts match.
Secret retrieval remains deployment-time reference behavior, never user data;
IAM and secret/ECR/DB resources are unchanged. See the
[bootstrap and secrets contract](../../docs/EC2_BOOTSTRAP_SECRETS.md).
