# Production runtime contract — MS9.1

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

All six inputs reject unset or empty values. Compose contains no defaults or
credential values and performs no image build.

| Input | Classification and ownership |
| --- | --- |
| `TASKFLOW_FRONTEND_IMAGE` | Non-secret image reference; MS9.2 defines release/tag/digest policy |
| `TASKFLOW_BACKEND_IMAGE` | Non-secret image reference; MS9.2 defines release/tag/digest policy |
| `TASKFLOW_DB_URL` | Connection configuration; MS9.4 defines RDS hostname, TLS parameters and CA handling; never embed credentials in the URL |
| `TASKFLOW_DB_USERNAME` | Application-role identifier; prepared with credential configuration in MS9.3/MS9.4 |
| `TASKFLOW_DB_PASSWORD` | Secret; secure materialization belongs to MS9.3 |
| `TASKFLOW_JWT_SECRET_BASE64` | Secret; existing contract requires a Base64-encoded 32-byte signing key; materialization belongs to MS9.3 |

`TASKFLOW_COOKIE_SECURE` is fixed to `"true"` in production Compose, even if the
shell supplies false. No AWS credentials or committed production env file is
used. Runtime environment variables are a consumer interface, not a secret
storage solution: container inspection and fully rendered Compose output can
expose them. MS9.3 owns secure retrieval, host permissions and materialization.

No redundant Spring production profile is introduced. Base configuration
already externalizes database/JWT settings, defaults Secure cookies to true,
and keeps Hibernate validation-only with Flyway as schema authority.
A dedicated profile is warranted only when later requirements introduce actual
profile-specific behavior.
The required database URL has no fallback; MS9.4 owns `sslmode=verify-full`,
the RDS CA bundle, application-role bootstrap and migration sequencing.

## Hardening, restart and health

The existing images run as non-root users. Both services use a read-only root
filesystem, writable `/tmp` tmpfs, and `no-new-privileges:true`, with no host
filesystem or Docker socket mount and no added capabilities/privileged mode.
MS9.2 must preserve this image contract when selecting release images.

`unless-stopped` supports restarting existing containers after daemon/host
restart; it is neither HA nor a deployment orchestrator. An unhealthy status
alone does not trigger that restart policy. Frontend initially waits for backend
health. Backend probes `/actuator/health` and requires status UP; frontend probes
its local Nginx root response. Timing matches the verified local Compose checks.

Container health is not ALB-integrated health. Nginx does not yet implement the
reference `/internal/health` backend proxy. MS9.5 owns that path and its public
blocking, HTTPS/proxy trust, forwarded-header handling and auth protections.
Existing Nginx forwarded-header behavior is unchanged and is not claimed ready
for the ALB → Nginx → Spring trust chain. Container metadata isolation also
remains deferred; no-new-privileges does not implement it.

## Local/static verification

From the repository root, with the six inputs supplied solely for parsing:

```bash
docker compose --env-file /dev/null -f compose.production.yaml config --quiet
```

The explicit empty env file avoids loading local development `.env`. Do not
print a fully rendered configuration containing real secrets. MS9.1 used only
ephemeral, non-secret validation placeholders and reserved `.invalid` image/DB
hostnames; these are not usable runtime inputs and are not stored in this file.
No production Compose pull or startup is part of validation.

Docker Compose 5.3.1 accepted the model. Checks verified both services, the
single bridge, port boundary, hardening, forced Secure cookies and rejection of
each missing or empty required input. Cached non-root images passed isolated checks with
networking disabled, read-only filesystems, tmpfs and no-new-privileges: Nginx
configuration validation and Java/tool availability. These checks do not prove
RDS connectivity, application startup, ALB health or deployment success.

## Remaining milestone ownership

| Milestone | Deferred work |
| --- | --- |
| MS9.2 | Image release/tagging and conceptual ECR workflow |
| MS9.3 | Host bootstrap, secret retrieval/materialization and metadata isolation |
| MS9.4 | RDS TLS/CA, database-role bootstrap and migrations |
| MS9.5 | Reverse proxy, HTTPS/DNS, trusted headers and integrated health/security |
| MS9.6 | CloudWatch Agent configuration and operational logging/monitoring |
| MS9.7 | Deployment, rollback and recovery runbooks |
| MS9.8 | Static smoke-test plan and readiness review |

These remain design tasks under the zero-provisioning policy. MS9.1 introduces
no release execution, secret population, runtime deployment or AWS operation.
