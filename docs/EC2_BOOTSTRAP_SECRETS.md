# EC2 bootstrap and secret delivery — MS9.3

This is a reference implementation under the authoritative
[zero-provisioning policy](AWS_ARCHITECTURE.md#hard-project-execution-policy).
No AWS credentials, API calls, IMDS requests, ECR interaction, RDS access,
secret population, infrastructure operation or production startup is authorized
for TaskFlow validation. Script tests use fakes in network-disabled containers.

## Bootstrap is not deployment

Terraform and CloudFormation contain equivalent, secret-free AL2023 user data.
It prepares Docker/SSM, non-secret application directories, runtime tmpfiles and
the persistent metadata guard. It never retrieves secrets, pulls application
images or starts Compose. This avoids boot-time secret-availability races and
secret values entering user data or cloud-init logs. The reference helper is
installed from a reviewed project checkout by an independent operator; user data
does not download or execute it. MS9.7 owns that installation and exact startup
sequencing after the external operator has populated the two application secrets.

AL2023 [ships AWS CLI v2](https://docs.aws.amazon.com/linux/al2023/ug/awscli2.html);
its [standard/minimal AMI package comparison](https://docs.aws.amazon.com/linux/al2023/ug/image-comparison.html)
also includes jq. Bootstrap verifies tool availability and installs missing
`awscli-2`, `jq`, `python3`, `iptables-nft`, `nftables` or `util-linux` through the
OS package manager. Python's standard library provides strict Base64 validation
and Linux atomic directory exchange; flock serializes materializers. No ad-hoc
AWS CLI installer or arbitrary binary download is embedded in bootstrap.
CLI v1 fails the version check. CloudWatch configuration remains MS9.6 work.

## Secret schemas and identity

The application DB SecretString is a JSON object with exactly two string fields:
`username` and `password`. MS9.4 requires username `taskflow_app`; migrator/master
identities are rejected. Both values must be non-blank and contain no control characters.
The JWT SecretString is the raw canonical Base64 encoding of exactly 32 bytes,
not a JSON object. The helper unwraps the AWS CLI JSON output before validation.
No hostname, JDBC URL, master DB credentials or AWS credentials belong in either
payload. The [MS9.4 database contract](RDS_DATABASE_OPERATIONS.md) defines the
endpoint/TLS/CA and separate role bootstrap. Master credentials remain with the
independent privileged operator; migration credentials exist only in a protected
ephemeral file for the controlled one-shot step. The backend materializer retrieves
neither. No additional secret metadata or EC2 access grant is introduced.

The helper accepts exactly two distinct **non-secret** identifiers (name or ARN):
application DB secret first, JWT secret second. Both calls request `AWSCURRENT`.
The host AWS CLI uses the EC2 instance role implicitly; operators must not configure
profiles, environment access keys or shared credential files to override it.
Region configuration is non-secret (the reference region is `eu-west-1`).

Existing IAM grants GetSecretValue/DescribeSecret only for these two application
secrets. It does not grant access to the RDS-managed master secret, writes,
`secretsmanager:*` or KMS wildcard permissions. Default AWS-managed Secrets Manager
encryption needs no customer-key grant. IAM, secret resources and DB resources
are unchanged. Other existing host permissions are not application-container
permissions; the metadata guard prevents containers obtaining the host role.

## Protected runtime files

| Host path | Owner / mode |
| --- | --- |
| `/run/taskflow` | `root:root`, `0700` |
| `/run/taskflow/secrets` | `root:root`, `0700` |
| `spring.datasource.username` within secrets | numeric `10001:10001`, `0400` |
| `spring.datasource.password` within secrets | numeric `10001:10001`, `0400` |
| `taskflow.security.jwt.secret-base64` within secrets | numeric `10001:10001`, `0400` |

`/run` is ephemeral tmpfs on the reference Linux host; a real operator must verify
that mount prerequisite. Reboot removes material and requires reacquisition.
No plaintext is intentionally stored in `/opt/taskflow`, `/etc/taskflow`, user
data, logs, IaC or Git. Root retains administrative control. The rootful Docker
daemon resolves each source path through the root-only parent directories and
bind-mounts individual files; backend UID/GID 10001 reads those mounted inodes.
User-namespace remapping/rootless Docker would require a separate ownership design.

Compose grants exactly these three file-backed secrets to backend under
`/run/secrets/`, with identical Spring property filenames. Frontend gets none.
Compose's file-backed implementation uses bind mounts; do not rely on its
uid/gid/mode remapping. Permissions come from the host files, not YAML declarations.
See [Compose secrets](https://docs.docker.com/reference/compose-file/services/#secrets).
These mounts are not Swarm encrypted secret storage and remain accessible to
host root/Docker administrators.

`TASKFLOW_SECRET_DIR` optionally overrides the **path**, defaulting to
`/run/taskflow/secrets`; use only an ephemeral fixture directory for static tests.
The helper's `TASKFLOW_RUNTIME_DIR` override is for isolated offline testing;
production uses `/run/taskflow`. Neither variable carries secret content.

## Materialization and failure boundary

[scripts/production/materialize-secrets.sh](../scripts/production/materialize-secrets.sh)
is reference-only, executable by root on the independent operator's Linux host.
It disables shell tracing, uses strict Bash and umask 077, verifies CLI v2/tools,
locks the runtime directory, and retrieves two SecretStrings into a private
staging directory on the same filesystem. AWS pager/auto-prompt are disabled.
AWS stderr is protected and discarded; no response or value is printed. Secret
contents never appear in arguments or environment variables; only identifiers
and paths do. Do not run the helper under an external tracing/debug wrapper.
[AWS GetSecretValue guidance](https://docs.aws.amazon.com/cli/latest/reference/secretsmanager/get-secret-value.html)
warns about sensitive CLI use and logged request parameters; identifiers are not
secret payloads. No debug or TLS-verification bypass is used.

Real jq parses the DB JSON and validates field types, schema and non-blank values.
Python strictly verifies canonical Base64/32-byte JWT material. Only after all
retrieval, parsing, validation, ownership and mode operations succeed are the
three final files published. Initial publication is a directory rename; replacing
an existing directory uses Linux `renameat2(RENAME_EXCHANGE)`. This swaps the
complete generation atomically, preserving the required real directory path.
Unsupported exchange fails closed; there is no sequential-file fallback.
Handled errors/signals remove staging material and leave the old generation
unchanged. SIGKILL/power loss cannot run cleanup; a protected staging generation
may remain until root cleanup or reboot, but no partial final generation is exposed.

Atomic publication does not serialize Docker opening three bind-mount paths.
The operator must not run Compose/recreate concurrently with materialization;
MS9.7 owns coordination, successful-exit gating and cleanup after interrupted
runs. Existing bind mounts retain old inodes after exchange, even if source paths
change. After rotation: update AWSCURRENT externally → run materializer again →
**recreate backend** to remount all three files and reread Spring configuration.
A plain process/container restart is not sufficient to guarantee new bind mounts.
There is no polling daemon, application AWS SDK or continuous rotation mechanism.
Two reads per controlled startup/deployment/reload event provide runtime caching.

## Spring configuration

Spring Boot 3.5.16 imports `optional:configtree:/run/secrets/` in the existing
application configuration, with no new profile or custom secret client/entrypoint.
Imported properties override the YAML's TASKFLOW environment placeholders. Local
Compose still uses the existing environment-variable contract when files are
absent. Normal higher-precedence direct Spring environment/system properties
remain supported by Spring; production Compose supplies no secret overrides.
See [Spring configuration trees](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html#features.external-config.files.configtree).

A small datasource configuration guard rejects unresolved/blank username/password
before datasource initialization, using a generic error without values. Existing
JWT configuration rejects missing, invalid or wrong-length keys. `optional:`
allows local development without the directory; it does not create credential
defaults. The materializer additionally validates production files before mounting.
Focused tests bind the real application YAML/configtree with no secret environment
inputs, verify local fallback and tree precedence, and reject missing/empty files.

## Container metadata isolation

Keep host IMDS enabled, tokens required, hop limit 2 and metadata tags disabled.
The host needs IMDS for instance-role credentials; containers do not. Security
Groups/NACLs are not the link-local enforcement boundary. The guard blocks
forwarded IPv4 traffic to `169.254.169.254/32` in `DOCKER-USER`, without touching
host OUTPUT. It covers the shared Docker bridge, including custom bridge names.
See [AWS local firewall guidance](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/instance-metadata-limiting-access.html)
and [Docker's iptables filtering point](https://docs.docker.com/engine/network/firewall-iptables/).

A Docker systemd drop-in runs a pre-start pass that prepares the user chain,
forwarding jump and deny rule before automatically restored containers can run.
Its post-start pass verifies Docker's chain/jump and ensures the rule exists.
Both passes are idempotent; failures fail Docker startup visibly. The files and
drop-in persist across reboot and run on every Docker service start, not only
first boot. Bootstrap restart is host preparation, not application deployment.
MS9.7 must verify the guard before any application startup.

This requires Docker's **iptables firewall backend**, including the iptables-nft
compatibility frontend. Native Docker nftables is unsupported: its `docker-bridges`
tables cause rejection even if stale iptables chains remain. No Docker-owned
nftables table is modified. Changing daemon backend/flags or disabling firewall
management invalidates the host contract and requires a new verified guard design;
post-start chain failures must never be ignored. Arbitrary later firewall flushes
are outside reboot persistence and require operator remediation before workload use.
There is no IPv6 in this architecture. Enabling it requires additional isolation
for `fd00:ec2::254`; do not assume the IPv4 rule covers it.

## Offline evidence and remaining work

Materializer tests use temporary directories, a PATH-injected fake AWS executable,
real jq and a network-disabled Linux container. They cover successful initial and
replacement publication, filenames/content, numeric ownership/modes, malformed
JSON, missing/empty DB fields, invalid JWT forms/lengths, both retrieval failures,
no leaked output, staging cleanup and unchanged final generations on failure.
Bootstrap tests compare both scripts exactly and fake firewall creation/checks,
idempotence, missing-chain failure and native nftables rejection. No host firewall,
IMDS or real AWS command is exercised. Compose is parsed only with fixtures.

Validation passed: 479 backend tests (including seven focused configuration
cases), four materializer test methods covering the success/failure matrix,
fake-firewall/parity tests, static Compose checks and offline Gitleaks 8.30.1.
Terraform 1.16.3 fmt/validate/graph and cfn-lint 1.57.0 passed offline and
credential-free; they do not prove live AL2023 bootstrap or EC2 packet behavior.
MS9.4 subsequently defines the database contract linked above. MS9.5 proxy/HTTPS,
MS9.6 monitoring, MS9.7 deployment/recovery coordination and MS9.8 readiness
remain unstarted.
