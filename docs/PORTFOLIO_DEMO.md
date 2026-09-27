# Free portfolio demo — MS10.7A

Repository preparation only; **live Render/Neon deployment and public URL are
pending**. This separate, disposable demo lets recruiters try TaskFlow. The final
professional README will follow live validation; it is not written in this step.

## Architecture and €0 boundary

Angular → Render Static Site (free global CDN) → same-origin `/api` rewrite →
Spring Boot on Render Free Web Service (Frankfurt) → Neon Free PostgreSQL.
Use provider-assigned HTTPS `onrender.com` domains, no custom domain purchase.

This is **non-production**. [AWS architecture](AWS_ARCHITECTURE.md), RDS
`verify-full`/CA checks, `TASKFLOW_DATABASE_PRODUCTION`, production Compose,
Terraform/CloudFormation, ECR and MS9/MS10 release/identity/deployment contracts
remain unchanged. Never enable the `demo` profile in AWS production. The demo
fails if combined with the production database flag; it does not relax that flag.

Use Render's free workspace option, one Free web service, one static site, and
Neon **Free**, without a payment method. Do not upgrade plans, attach disks, add
paid monitoring, provision a Render database, or buy a domain. Check account
billing/usage before creation; if signup demands a card or paid feature, stop
rather than bypass the €0 boundary. No guarantee is made about future provider
pricing or a particular account's eligibility.

Render supports free services without a card. Without a payment method, quota
exhaustion can suspend services or disable new builds instead of billing overages.
External database traffic can also trigger free-service suspension; accept downtime
rather than upgrading. Free web services sleep after 15 minutes idle and may take
about a minute to restart—not merely an instant. No keep-alive cron/ping workaround
is permitted. [Render Free](https://render.com/docs/free),
[Render no-card free hosting](https://render.com/articles/platforms-with-a-real-free-tier-for-developers-in-2026).

Neon Free includes bounded compute/storage and scale-to-zero. Keep one small demo
project, use its included resources, monitor usage and accept suspension/limits.
Do not convert to a usage-billed plan or disable scale-to-zero. The dashboard's
current Free allowance is authoritative; this repository does not provision Neon.
[Neon Free and scale-to-zero](https://neon.com/blog/building-patterns-unlocked-by-scale-to-zero),
[Neon no-card signup](https://neon.com/faster).

## Neon preparation (existing external project)

The dedicated Neon Free project already exists externally; this repository neither
creates nor inspects it. Verify its Free plan, region and PostgreSQL version
(17 is the tested major version). Verify or create database `taskflow` and a dedicated
login role `taskflow_app` with a unique password using Neon’s secure controls.
Make it owner of this disposable database and allow it to create objects in the
`public` schema. Do not reuse personal/project administrator credentials in Render.

Unlike production, this demo role runs Flyway at startup and therefore needs DDL
privileges on its own database/schema. This intentional demo tradeoff is not the
production `taskflow_migrator`/runtime-role split. Existing migrations are reused;
Hibernate stays `ddl-auto=validate`. Do not enable Flyway clean/baseline shortcuts.

Choose Neon's **direct/unpooled** endpoint for startup migrations and the small
Hikari pool. Enter the JDBC URL only in Render's backend environment. Its shape is
`jdbc:postgresql://<neon-host>:5432/taskflow?sslmode=require`; no real host or
connection string is committed. Username/password are separate variables, never
URL parameters. Optional `&channelBinding=require` is accepted (JDBC spelling),
and recommended when verified with the supplied endpoint/driver. Do not copy a
libpq `channel_binding` parameter into JDBC.

The demo-only guard requires this database, port, role and TLS URL shape and
rejects embedded credentials, duplicate options and TLS-disable overrides. It also
rejects disabled Flyway or a Hibernate mode other than `validate`. It is
not a Neon hostname allowlist. `sslmode=require` encrypts but does not authenticate
the server certificate like AWS's `verify-full` contract; use only disposable demo
data and validate channel binding before publishing the link. No AWS CA path is
used. [pgJDBC TLS semantics](https://jdbc.postgresql.org/documentation/ssl/),
[Neon connection-security guidance](https://neon.com/blog/postgres-needs-better-connection-security-defaults).

## Backend environment and runtime

[render.yaml](../render.yaml) builds the existing `backend/Dockerfile`, using
`rootDir: backend`, Docker context `.`, `plan: free`, `region: frankfurt` and
`/actuator/health`. No extra actuator endpoint is exposed; health details remain
hidden. The base application uses `${PORT:8080}`: Render supplies `PORT`, while
local Docker and AWS retain 8080 when it is absent. Do not pin a Render port.

| Variable | Backend-only value/source |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `demo` |
| `TASKFLOW_DB_URL` | Secret/platform configuration; direct JDBC TLS URL described above |
| `TASKFLOW_DB_USERNAME` | Dedicated `taskflow_app` role, supplied through Dashboard |
| `TASKFLOW_DB_PASSWORD` | Neon role password; secret, no default |
| `TASKFLOW_JWT_SECRET_BASE64` | Unique Base64 encoding of exactly 32 random bytes; secret, no default |
| `TASKFLOW_COOKIE_SECURE` | `true` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `framework` |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=50.0` reserves memory outside the heap |

Do not set `TASKFLOW_DATABASE_PRODUCTION`, AWS credentials or production CA mounts.
Generate JWT material privately with `openssl rand -base64 32`, transfer it directly
to the Render secret field, and never paste it into Git, issue/PR text, screenshots
or build logs. Do not put any backend secret into frontend build variables.
`sync: false` prompts for values on initial Blueprint creation; later secret changes
must be made in the service's Dashboard. No secret is generated by this repository.

The demo profile enables framework forwarded-header handling behind Render's HTTPS
edge and retains Secure cookies. It inherits existing HttpOnly/SameSite/CSRF
behavior. Do not expose the app outside the trusted provider proxy or disable
Secure cookies/CORS protection to mask a routing defect. The public backend URL
is still reachable; this is not private AWS ingress. Live proxy/cookie behavior
must pass the smoke test below.

Hikari uses at most two connections, zero minimum idle, a 30-second idle timeout
and no keepalive. This limits idle database use; it does not promise zero compute
consumption (real health checks/traffic may access the DB). Free memory/CPU and
cold-start readiness remain live acceptance checks, not guarantees from local CI.

## Frontend and routing

The Blueprint uses `runtime: static`, `rootDir: frontend`,
`npm ci && npm run build`, Node `22.23.1`, and publishes
`dist/taskflow/browser`. Static sites have no `plan` or `region` field. Paths are
relative to `rootDir`. Angular remains unchanged and uses same-origin `/api`.
[Render Blueprint reference](https://render.com/docs/blueprint-spec),
[monorepo path semantics](https://render.com/docs/monorepo-support).

Render does not interpolate Blueprint variables into route destinations. Routes
are deliberately omitted from the Blueprint so it does not silently replace a
manual backend destination or send API requests to the SPA. Once Render assigns
the backend HTTPS URL, configure these Dashboard **Rewrite** rules in this order:

| Priority | Source | Destination |
| --- | --- | --- |
| First | `/api/*` | `<backend HTTPS origin>/api/*` |
| Last | `/*` | `/index.html` |

Use the actual backend origin only in Render, with no trailing slash before
`/api/*`. Preserve the `/api` prefix; use Rewrite, not Redirect. The browser must
stay on the frontend origin. Never create a static resource under `/api` (existing
resources take precedence over rewrite rules). Verify API responses are not cached
across users, request methods/bodies/auth/CSRF headers and query strings survive,
and Set-Cookie reaches the frontend origin. No broad CORS or hardcoded Angular API
URL is needed. [Render rewrite syntax](https://render.com/docs/redirects-rewrites),
[route priority](https://api-docs.render.com/reference/add-route).

## Deployment procedure — pending, not executed here

1. Review and manually commit/push when authorized; require the existing green
   CI gate. Long-term both services deploy from `main` after MacroStep 10 merges.
2. Verify the existing Neon Free project and private credentials as above. No AWS operation is involved.
3. In Render, review the Blueprint preview before creating anything. Verify the
   free plan and no payment method; fill the backend secret prompts. Initial
   Blueprint creation starts builds even though automatic deploys are off.
4. Both services explicitly use `branch: main` and `autoDeployTrigger: off`.
   Keep Blueprint Auto Sync off in the Dashboard as well: service auto-deploy
   settings do not prevent configuration syncs from deploying. No previews or
   feature-branch auto-deploys are required. For initial validation before merge,
   temporarily select `feature/ci-portfolio-preparation` manually for both services
   (and the Blueprint source if necessary), with auto-deploy/Auto Sync off; restore
   all branch selections to `main` after merge. Never publish every feature push.
5. Wait for backend health and successful Flyway startup, without logging secrets.
   Configure both frontend rewrites and wait for its build. Check the smoke list.
6. Record the reviewed commit, public frontend URL and actual smoke evidence only
   after success. A URL is not fabricated here; final README work remains pending.

No Render deploy hook/token is added to GitHub Actions. Later deployments remain
manual and must follow successful CI. [Blueprint sync behavior](https://render.com/docs/infrastructure-as-code).

## Live smoke checklist

Before live deployment, run `./mvnw -B -ntp verify` from `backend/` with Docker
running (Testcontainers and local HTTP tests require Docker/socket access), then
`python3 -m unittest discover -s scripts/demo/tests -v` from the repository root
with PyYAML available. Build the existing image with
`docker build -t taskflow-backend:ms107a backend`. These checks cover the demo
configuration and offline Blueprint contract; they do not contact Neon or Render.
Run the existing CI/policy/security/release/deployment/readiness/runbook regressions
and Gitleaks before review. Run frontend quality if frontend code/config changes.

- Open HTTPS `/` and reload a nested Angular route: both serve the SPA.
- Verify backend `/actuator/health` returns healthy without sensitive details.
- From the frontend origin, request `/api/auth/csrf`; verify an API response, no
  cross-origin redirect or SPA HTML, and appropriate non-cacheable behavior.
- Register a disposable account, login, create a board/column/task, edit/delete it,
  refresh the page, refresh authentication and logout. Exercise POST/PUT/DELETE,
  CSRF enforcement and Secure/HttpOnly/SameSite cookie behavior via browser tools.
- Use a second account to verify private-data isolation and no shared response cache.
- Let hosting sleep naturally; verify wake/retry behavior without scheduled pings.
- Confirm Free plans, no payment method, no paid resource and usage within limits.

If external rewriting fails authenticated mutations or cookie forwarding, do not
publish the URL or weaken security. Record the provider limitation for a separate
design decision; repository preparation is not evidence of live proxy correctness.

## Limitations and teardown

Public signup can attract abuse. Use disposable data/passwords, never sensitive or
production information; accounts/data may be reset. No SLA, HA, production DDL
separation, durable backup guarantee or paid monitoring is claimed. Keep logs
private and avoid recording tokens/credentials. Availability may stop at free
quotas or because of provider abuse controls; do not pay to work around this.

To tear down, disable any deploy/Blueprint sync first, delete the two demo services
and Blueprint in Render, then delete the dedicated Neon demo project and revoke its
credentials. Check both dashboards for remaining demo resources; delete only these
resources, never production/reference infrastructure. No teardown runs here.

Provider documentation checked **2026-09-27**. Repository/static tests and local
backend verification do not prove live provider eligibility, proxy behavior or
Neon connectivity. This repository work creates no account, service, secret or public
deployment; the pre-existing external Neon project remains separate.
