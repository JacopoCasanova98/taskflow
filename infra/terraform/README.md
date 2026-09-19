# TaskFlow Terraform root module

This single root module models the [AWS reference architecture](../../docs/AWS_ARCHITECTURE.md).
MS8.2 establishes the provider, inputs, naming/tags and foundation outputs.
MS8.3 adds networking resources only. There are no data sources or child modules.

## Execution policy and validation

TaskFlow never provisions AWS infrastructure. No AWS account, credentials,
billable resources or live AWS API calls are required. Do not run `terraform apply`
or `terraform destroy`; no plan is required or run for MS8.2–MS8.3. Public Registry and
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
MS8.4 will define ingress restrictions through Security Groups.

Both database subnets disable public IPv4 assignment and explicitly associate
with a separate database route table containing only the implicit VPC local
route. There is no Internet/NAT route for that tier. No NAT, Elastic IP,
VPC endpoints or custom NACLs are defined; VPC default NACLs remain in use.
Names derive from `local.name_prefix`; taggable networking resources add `Name`,
`Component = "networking"` and, for tier-specific resources, `Tier`.
Provider common tags are inherited. Root metadata outputs remain unchanged.

Static review confirms one VPC, four subnets across two logical AZs, one IGW,
two route tables, one public default route and four explicit associations
(13 resource instances from nine resource blocks). No other resource types exist.
Offline validation checks locked-provider resource schemas, argument types and
references; the textual dependency graph checks the routing dependencies.
These checks do not prove AWS service acceptance, available AZs or provisionability.
MS8.3 passed formatting, validation and textual graph review using Terraform
1.16.3 / AWS 6.65.0 with `--network none`, no AWS environment variables and no
credential directory. The existing provider cache was reused without initialization,
upgrade or lock-file changes. No state, plan or AWS operation was produced.

## Milestone ownership

MS8.2 owns the foundation and MS8.3 owns networking. Security (MS8.4), compute
(MS8.5), database (MS8.6), resource outputs (MS8.7), CloudFormation (MS8.8) and
final IaC verification (MS8.9) remain deferred. Future authoring must preserve
credential-free static validation; no live AWS data sources are introduced here.
MS9 deployment design remains separate and unstarted.
Security Groups belong to MS8.4, compute/ALB to MS8.5 and RDS/DB subnet groups
to MS8.6. None is defined in MS8.3.
