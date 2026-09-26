# CI/CD identity and secrets security — MS10.5

This is TaskFlow's authoritative **offline security design**, verified against the
official sources below on **2026-09-26**. The [reference contract](../ops/identity/github-oidc-contract.json)
is version `1.0`, **REFERENCE ONLY — NON-APPLIED**. It is a repository security
contract, not an IAM policy, credential store or deployable stack.

MS10.1/2 execute hosted quality and local Docker build/inspection CI. MS10.3/4/5
provide offline release, deployment-planning and security contracts. No AWS
identity, OIDC provider, GitHub environment or privileged workflow is configured
by this milestone. No token, secret, registry operation or deployment is performed.

## Threat model and identity separation

The primary attack path is untrusted PR code → privileged workflow/token → cloud
compromise. Ordinary PR/feature CI must never receive AWS federation. Compromise
of a publisher must not grant host access, application secrets or infrastructure
administration. Publishing malicious code remains a significant power: future
release approval must bind reviewed source, gated artifacts and digest evidence.

| Identity | Responsibility | Boundary |
| --- | --- | --- |
| Runtime host | Existing EC2 IAM model: image pull, exact app DB/JWT secret reads, SSM agent, telemetry | No image push, IAM/infrastructure mutation, master or migrator secret reads |
| Registry publisher | Future short-lived ECR publication and release/scan evidence | Exactly two repositories; no host, database, secret or infrastructure access |
| Deployment/operator | Independent live operator today; future automated identity disabled | No generic remote shell grant; constrained interface and migration credential path unresolved |
| Infrastructure provisioner | Independently authorized Terraform/CloudFormation ownership | Separate from all application delivery identities; permission design outside this milestone |

Distinct role placeholders in the contract cannot be collapsed. Empty provisioner
capabilities mean **no authorization granted here**, not a proposed complete
infrastructure policy. Existing EC2 IAM is unchanged in both IaC implementations.
Its `AmazonSSMManagedInstanceCore` attachment supports the agent; it is not a
GitHub caller's authorization to administer the host.

The runtime role has `ecr:GetAuthorizationToken` plus pull actions on only the
frontend/backend repositories. `secretsmanager:GetSecretValue` and
`secretsmanager:DescribeSecret` target only the app DB and JWT secret resources.
Log writes target pre-created application log groups; metric writes retain the
TaskFlow environment namespace condition. See [bootstrap secrets](EC2_BOOTSTRAP_SECRETS.md)
and [AWS architecture](AWS_ARCHITECTURE.md).

## Future federation and protected release surface

Future authentication is GitHub Actions OIDC → STS `AssumeRoleWithWebIdentity` →
short-lived role credentials. The issuer is `https://token.actions.githubusercontent.com`.
The requested and trusted audience must be `sts.amazonaws.com`. Future AWS trust
must use `StringEquals` on both `token.actions.githubusercontent.com:aud` and
`token.actions.githubusercontent.com:sub`, with the exact provider in
`<AWS_ACCOUNT_ID>`. A repository wildcard is prohibited. [GitHub AWS OIDC guidance](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws),
[AWS IAM GitHub trust conditions](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-idp_oidc.html#idp_oidc_Create_GitHub).

The reference publisher subject is
`repo:JacopoCasanova98/taskflow:environment:release`. It is a **name-based design
value**, not an observed token claim. GitHub now also documents immutable-ID
subjects for newer/opted-in repositories and rename/transfer cases. Before any
future enablement, independently verify the repository's effective subject
format and review an exact contract update if IDs are required. Never broaden
trust to make a mismatch pass. No token is requested here. Environment subjects
do not also encode the branch; source restrictions must be enforced separately.
[GitHub OIDC claims and subject formats](https://docs.github.com/en/actions/reference/security/oidc).

The proposed separate publisher workflow requires `workflow_dispatch` on
`refs/heads/main`, the protected `release` environment, independent approval,
restricted environment sources, and reviewed full-SHA source/artifact gates.
Never check out arbitrary user-supplied refs or execute untrusted artifacts after
obtaining authority. `pull_request`, `pull_request_target`, `feature/**` and forks
are prohibited federation sources. A dispatch event alone is not authorization.
No such workflow or environment exists as part of this change.

Environment review availability varies by repository visibility/plan. Before
live enablement, verify required review/source controls are available and cannot
be bypassed under the agreed policy; prevent self-approval where supported.
If the required protection cannot be enforced, leave federation disabled pending
an independently reviewed alternative. MS10.6 branch/PR gate implementation
remains deferred. [GitHub environment protection](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments).

Ordinary `.github/workflows/ci.yml` stays `contents: read`, with no OIDC, AWS
action, secrets, registry login or deployment job. The future publisher would
need only `contents: read` and job-scoped `id-token: write`; the latter permits
requesting a JWT, not AWS resource access. AWS trust and permission policies still
control authorization. No `contents: write`, `packages: write`, `actions: write`
or `security-events: write` is justified here. Future reusable/third-party
actions must use verified full commit SHAs; choose and verify releases at actual
implementation time. No action or executable reference workflow is added.
[GitHub OIDC workflow permissions](https://docs.github.com/en/actions/reference/security/oidc#workflow-permissions-for-the-requesting-the-oidc-token).

## Publisher authorization

The semantic capability groups map to this bounded future action set:

| Group | AWS actions | Resource scope |
| --- | --- | --- |
| `ecr_auth` | `ecr:GetAuthorizationToken` | `*` |
| `ecr_push_taskflow_repositories` | `ecr:BatchCheckLayerAvailability`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`, `ecr:PutImage`, `ecr:BatchGetImage` | Both exact repository ARNs |
| `ecr_read_release_metadata` | `ecr:DescribeImages` (manifest reading also uses `BatchGetImage`) | Both exact repository ARNs |
| `ecr_read_scan_results` | `ecr:DescribeImageScanFindings` | Both exact repository ARNs |

The push set follows [AWS's repository-scoped push policy](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-push-iam.html).
The [ECR authorization reference](https://docs.aws.amazon.com/service-authorization/latest/reference/list_ecr.html)
identifies repository-scoped actions; `GetAuthorizationToken` has no resource-level
scope and therefore requires `Resource: "*"`. This does not grant arbitrary
repository operations. The token only authenticates the principal's existing
authority. [ECR token API](https://docs.aws.amazon.com/AmazonECR/latest/APIReference/API_GetAuthorizationToken.html).

The only repository resources are:

- `arn:aws:ecr:eu-west-1:<AWS_ACCOUNT_ID>:repository/<NAME_PREFIX>-frontend`
- `arn:aws:ecr:eu-west-1:<AWS_ACCOUNT_ID>:repository/<NAME_PREFIX>-backend`

These are placeholders, never existing account evidence. Resolve them against
the independently managed IaC outputs only in a future authorized process.
No create/delete repository, lifecycle, tag-mutability or registry-scanning
configuration action is granted. IMMUTABLE tags, AES256, seven-day untagged
expiry and external BASIC scan-on-push ownership remain unchanged.

`DescribeImages` provides registry metadata/digests; scan status and findings
must use `DescribeImageScanFindings`, including pagination. Neither a digest nor
a completed scan alone means vulnerability acceptance. Both components need the
MS10.3 eligibility review. [Image metadata API](https://docs.aws.amazon.com/AmazonECR/latest/APIReference/API_DescribeImages.html),
[scan findings API](https://docs.aws.amazon.com/AmazonECR/latest/APIReference/API_DescribeImageScanFindings.html).

The publisher has no Secrets Manager reads, SSM commands/sessions, EC2 mutation,
RDS access, IAM mutation, CloudFormation/infrastructure mutation, Terraform state
access, Route 53, ACM or CloudWatch administration. Capability additions fail
closed in the offline validator. This contract is not a substitute for reviewing
all effective live identity/resource policies and attachments before enablement.

## Deployment and secret boundaries

[MS10.4](DEPLOYMENT_AUTOMATION.md) defines what a future executor would do;
MS10.5 defines its identity boundary. `ssm:SendCommand` with `AWS-RunShellScript`
would permit arbitrary host commands and is rejected as an automated deployment
interface. No custom constrained SSM document exists in this design, and none is
implemented here. A future interface must constrain both exact target/environment
and reviewed operations, inputs, digest pair and audit evidence; document scoping
alone is insufficient if parameters allow arbitrary commands or secret reads.
Automated deployment authority remains disabled until this interface and migration
credential delivery are independently designed and reviewed. MS9.7's independently
authorized live operator remains the operational boundary; MS10.4 stays complete.

Application secrets flow only through:

`Secrets Manager → EC2 instance role → materialize-secrets.sh → protected /run/taskflow/secrets → backend configtree`

DB app password and JWT signing material never enter GitHub Actions. Existing
protected host file ownership/modes and backend recreation after materialization
remain unchanged. Publisher and future application deployer cannot read payloads.
The `taskflow_migrator` credential remains independently controlled and unavailable
to the long-running backend. Unattended D5 needs a separate controlled credential
path; it is not silently made a GitHub secret. RDS-managed master credentials
remain solely an independently authorized database-administration responsibility:
never runtime host, publisher or GitHub CI/CD. See [RDS operations](RDS_DATABASE_OPERATIONS.md).

AWS region, role ARN and repository metadata are normally configuration identifiers
(future variables), not secret payloads. None is currently required in GitHub.
AWS access-key repository secrets, static CI IAM users, personal/shared developer
keys and committed `.env` credentials are prohibited. No repository/environment
secret is added.

## Session, audit and redaction contract

Future publisher sessions request 900 seconds with a separately configured role
maximum of 3600 seconds. These are design settings, not observed configuration.
AWS permits requests from 900 seconds up to the role maximum (configurable from
one to twelve hours); the API default is 3600 seconds. The request is not itself
an IAM-enforced 15-minute maximum. A longer publishing requirement needs review,
not persistent credentials. [STS web-identity duration rules](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRoleWithWebIdentity.html).

Use a purpose-specific session name traceable to GitHub run ID/attempt and retain
non-secret reviewed SHA/release evidence for audit correlation. No credential file,
OIDC token or registry login state may persist in artifacts/cache or subsequent
jobs. ECR tokens have their own lifetime; a short STS request must not be treated
as proof of equally short registry-token validity. Use an isolated ephemeral job
and discard its authentication state after publication, including failure paths.

Never print AWS credentials, registry authorization tokens, `SecretString`, DB/JWT
or migration passwords. `set -x`/shell tracing around credentials and debug dumps
are prohibited. Masking is defense in depth, not permission to log secrets.
Release-pair JSON and deployment plans remain non-secret and cannot contain
secret values. No live audit evidence or successful assumption is fabricated.

## Offline validation and remaining prerequisites

Run with standard-library Python:

```text
python3 scripts/security/validate_ci_cd_identity.py
python3 -m unittest discover -s scripts/security/tests -v
```

The validator only reads JSON and checks a closed v1 policy. Unknown fields,
duplicate keys/capabilities, incorrect types, trust broadening, role collapse,
forbidden grants, credential persistence and secret-flow changes fail safely
without echoing candidate values. It has no process/network/cloud imports and
cannot apply the contract. Tests mutate the security boundaries and check existing
runtime IAM/CI and document interfaces. Existing readiness parity tests check the
exact application-secret resources in Terraform/CloudFormation.

Local success proves consistency of the design only. Before future live work,
independent authorization must resolve actual account/repository identities,
effective subject format, environment protection, exact trust/permission policies,
workflow/action reviews, session cleanup/audit and registry eligibility. Deployment
additionally requires the constrained host interface and migrator credential path.
No IAM role/provider, STS session, registry operation, SSM execution, secret
retrieval, deployment or Terraform/CloudFormation operation occurs in MS10.5.

Validation evidence: 14 identity/security tests, 11 release-pair tests, 13
deployment-plan tests, four CI checks, five readiness checks and five runbook
checks passed using cached tooling, read-only repository mounts and networking
disabled. No application suite or production runtime was executed.
