# Production runtime contract — MS9.1–MS9.4

TaskFlow models how the reference EC2 runtime would operate; it never deploys it.
No AWS account, credentials, API access or recurring hosting cost is required.
This is a container configuration contract, not a deployment runbook or a claim
that production runtime security is complete.

## Topology and Compose ownership

`compose.yaml` remains the verified local frontend/backend/PostgreSQL stack,
with loopback publication and local generated credentials. The standalone
`compose.production.yaml` contains only frontend and backend; do not merge it
with the local file. PostgreSQL is external RDS.

The reference request path is ALB → EC2 IPv4 port 8080 → frontend Nginx port
8080 → `backend:8080` on one Compose bridge network. Frontend publishes
`0.0.0.0:8080:8080` so the ALB can reach the host ENI. The MS8 App security
group permits that ingress only from the ALB security group. This binding relies
on that boundary; the file must not be started on an unprotected public host.
Backend has no host-published port. There is no direct Internet-to-Spring path.

Both services share the `application` bridge with Docker DNS and dynamic
addresses. It is not an internal-only network: backend connections to RDS leave
through the host and remain subject to the approved App SG egress rules.
There is no PostgreSQL container, host networking or extra data network.

Angular retains relative `/api`, proxied by Nginx to backend. No browser-visible
backend hostname, `TASKFLOW_API_URL` or new CORS configuration is introduced;
same-origin refresh-cookie and XSRF behavior is preserved.

## Required inputs

MS9.3 replaces MS9.1's intermediate environment-secret model with file-backed
secrets. Exactly three non-secret runtime inputs reject unset or empty values:

| Input | Classification and ownership |
| --- | --- |
| `TASKFLOW_FRONTEND_IMAGE` | Non-secret digest-pinned image reference; MS9.2 |
| `TASKFLOW_BACKEND_IMAGE` | Non-secret digest-pinned image reference; MS9.2 |
| `TASKFLOW_DB_URL` | Non-secret verify-full RDS JDBC URL; see MS9.4 database contract |

`TASKFLOW_SECRET_DIR` is an optional non-secret path, defaulting to
`/run/taskflow/secrets`. Backend alone receives three file-backed Compose secrets:
`spring.datasource.username`, `spring.datasource.password` and
`taskflow.security.jwt.secret-base64` under `/run/secrets/`.
Production no longer interpolates DB username/password or JWT values into its
environment. The directories are root:root 0700; files are numeric 10001:10001
0400 for the existing backend UID. File-backed mounts preserve host permissions.
Spring's optional configtree import maps these names directly to properties;
the username is fixed by policy to `taskflow_app`, and local development retains
its existing environment inputs. Missing/blank DB
credentials and missing/invalid JWT material fail configuration validation.

The [bootstrap and secret-delivery contract](EC2_BOOTSTRAP_SECRETS.md) defines
AWSCURRENT retrieval, protected ephemeral staging, atomic publication and
recreation after rotation. User data never retrieves values. No plaintext is
stored in the repository, persistent app directories or IaC. Frontend gets no
secrets. Root/Docker administrators retain access to mounted files.

Production image variables must resolve to `<repository>@sha256:<digest>` for
both artifacts of the same reviewed release. Tags remain traceability/release
aliases. The [container release contract](CONTAINER_RELEASE.md) defines the
MS9.2 image policy; Compose itself only checks that these inputs are non-empty.

`TASKFLOW_COOKIE_SECURE` is fixed to `"true"` in production Compose, even if the
shell supplies false. No AWS credentials or committed production env file is
used. Secret values are file-backed; do not print secret files or retrieval responses.

No redundant Spring production profile is introduced. Base configuration
already externalizes database/JWT settings, defaults Secure cookies to true,
and keeps Hibernate validation-only with Flyway as schema authority.
A dedicated profile is warranted only when later requirements introduce actual
profile-specific behavior.
The required database URL has no fallback. MS9.4 requires the actual RDS endpoint,
port 5432/database `taskflow`, `sslmode=verify-full` and
`sslrootcert=/opt/taskflow/trust/rds-ca-bundle.pem`; no embedded credentials or
TLS override parameters. Backend alone mounts the public regional CA bundle
read-only at that path. `TASKFLOW_RDS_CA_FILE` optionally overrides the host
source; a missing source is not auto-created. This is public trust, not a secret.

Production fixes `SPRING_FLYWAY_ENABLED=false` and
`TASKFLOW_DATABASE_PRODUCTION=true`. A successful one-shot migration as
`taskflow_migrator` must precede backend startup; runtime uses `taskflow_app`
with Hibernate `validate`. The guard rejects other roles, automatic schema
changes, a nonconforming URL or missing CA file. Local Flyway remains automatic.
See [RDS database operations](RDS_DATABASE_OPERATIONS.md) for role bootstrap,
CA acquisition/rotation, isolated migrator credentials and the failure boundary.

## Hardening, restart and health

The existing images run as non-root users. Both services use a read-only root
filesystem, writable `/tmp` tmpfs, and `no-new-privileges:true`, with no host
directory or Docker socket mount and no added capabilities/privileged mode.
Only the three backend secret files and the public read-only CA file are mounted
from the host.
The MS9.2 release contract preserves these image requirements.

`unless-stopped` supports restarting existing containers after daemon/host
restart; it is neither HA nor a deployment orchestrator. An unhealthy status
alone does not trigger that restart policy. Frontend initially waits for backend
health. Backend probes `/actuator/health` and requires status UP; frontend probes
its local Nginx root response. Timing matches the verified local Compose checks.

Container health is not ALB-integrated health. Nginx does not yet implement the
reference `/internal/health` backend proxy. MS9.5 owns that path and its public
blocking, HTTPS/proxy trust, forwarded-header handling and auth protections.
Existing Nginx forwarded-header behavior is unchanged and is not claimed ready
for the ALB → Nginx → Spring trust chain. MS9.3 defines persistent container
IMDS isolation through the Docker iptables user chain; host IMDS remains available.

## Local/static verification

From the repository root, with the three required inputs and an ephemeral secret fixture directory:

```bash
docker compose --env-file /dev/null -f compose.production.yaml config --quiet
```

The explicit empty env file avoids loading local development `.env`. Do not
print a fully rendered configuration containing real secrets. MS9.1 used only
ephemeral, non-secret validation placeholders and reserved `.invalid` image/DB
hostnames; these are not usable runtime inputs and are not stored in this file.
No production Compose pull or startup is part of validation.

MS9.1 originally validated six environment inputs. MS9.3 revalidates the three
remaining inputs and backend-only secret mounts without rendering secret values.
Docker Compose accepted the model. Checks verified both services, the
single bridge, port boundary, hardening, forced Secure cookies and rejection of
each missing or empty required input. Cached non-root images passed isolated checks with
networking disabled, read-only filesystems, tmpfs and no-new-privileges: Nginx
configuration validation and Java/tool availability. These checks do not prove
RDS connectivity, application startup, ALB health or deployment success.

## Remaining milestone ownership

| Milestone | Deferred work |
| --- | --- |
| MS9.5 | Reverse proxy, HTTPS/DNS, trusted headers and integrated health/security |
| MS9.6 | CloudWatch Agent configuration and operational logging/monitoring |
| MS9.7 | Deployment, rollback and recovery runbooks |
| MS9.8 | Static smoke-test plan and readiness review |

These remain design tasks under the zero-provisioning policy. MS9.1 introduces
no release execution, secret population, runtime deployment or AWS operation.
