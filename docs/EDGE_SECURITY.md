# Edge security — MS9.5

This is a locally verified reference contract, not a deployed edge or an operational
runbook. No AWS credentials, API calls, DNS resolution, certificate request or
public-cloud test is part of validation.

## Request and TLS boundary

Browser HTTPS → ALB :443 → HTTP EC2/Nginx :8080 → HTTP `backend:8080`.
TLS terminates at ALB. The unencrypted internal leg is an accepted reference
tradeoff protected by VPC and Security Groups, not end-to-end encryption. The
HTTP listener issues a 301 to HTTPS; Nginx/Spring do not add a redirect loop.
The audited `ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09` remains unchanged.

EC2's public IPv4 supports outbound traffic without NAT. Its application SG
accepts 8080 only from the ALB SG; that port is not Internet ingress. Backend
has no published host port, SSH is absent, and no new port is introduced.

## Host, certificate and DNS contract

Terraform `public_hostname` / CloudFormation `PublicHostname` is required,
non-secret, and has no default. Use a lowercase DNS hostname, such as the reserved
example `taskflow.example.com`: no scheme, path, port, wildcard or trailing dot.
Validation respects ALB's 128-character host-condition limit and alphabetic TLD.
HTTPS rules execute in this order:

1. Priority 1: `/internal/*`, `/actuator`, `/actuator/*` → fixed 404.
2. Priority 2: `/swagger-ui*`, `/v3/api-docs*` → fixed 404.
3. Priority 10: configured host → application target group.
4. Default → fixed 404, including arbitrary hosts and direct ALB DNS requests.

The separate documentation rule respects ALB's maximum three comparisons per
condition. Health probes go directly to targets, independently of listener rules.
See [AWS listener conditions](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/rule-condition-types.html).

ACM and Route 53 remain external and unprovisioned. An independent operator must
verify the certificate covers the public hostname, is issued/usable by ALB and
is in the ALB Region (`eu-west-1`). The HTTPS listener references that certificate.
ACM-issued integrated certificates support managed renewal subject to eligibility;
imported certificates require independent renewal/expiry handling. Check certificate
health before a real release; monitoring implementation belongs to MS9.6.
See [ACM renewal](https://docs.aws.amazon.com/acm/latest/userguide/managed-renewal.html).

The external DNS contract is a Route 53 **A alias** from that hostname to the
ALB DNS name and canonical hosted-zone ID. This IPv4 design has no AAAA record.
No domain/zone/record/certificate is created by TaskFlow. Live SAN coverage,
Region, issuance, listener reference, alias target and host-rule consistency
remain real-operator preflight checks; static tests cannot prove them.
See [Route 53 alias routing](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-to-elb-load-balancer.html).

## Trusted forwarding

The immutable frontend image contains the complete Nginx configuration. There
is no host-mounted edge configuration or production-only template generation.
The shared configuration trusts only original peers in `10.42.0.0/24` and
`10.42.1.0/24`, the approved ALB subnets. The SG remains essential: other workloads
in those subnets must not acquire access to host 8080. Changing subnet inputs
requires reviewing/rebuilding this image's allowlist; broad trust is not a fallback.
Linux Docker bridge publication must preserve the ALB source address; no host-mode
proxy that replaces it with a bridge gateway is supported by this contract.

Both IaC definitions explicitly select ALB XFF **append** mode. Nginx RealIP uses
recursive resolution to select the last non-trusted address, then overwrites
upstream XFF and X-Real-IP with this one client address. It never forwards the
arbitrary client-supplied prefix. Original peer checks use `$realip_remote_addr`,
not the rewritten client address. Do not authorize users by these IP headers.
See [ALB forwarding](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/x-forwarded-headers.html)
and [Nginx RealIP](https://nginx.org/en/docs/http/ngx_http_realip_module.html).

Only an approved peer with the exact `https` / `443` pair produces upstream
HTTPS/443 and HSTS. Other inputs fall back to Nginx's actual scheme/listen port;
comma-separated or arbitrary protocol strings cannot promote HTTP to HTTPS.
Host and X-Forwarded-Host are overwritten from Nginx's parsed Host, whose public
allowlist is enforced by ALB. `Forwarded` and X-Forwarded-Prefix are removed.
Direct local HTTP ignores supplied forwarding metadata.

Production Compose enables Spring Boot `NATIVE` forwarding with explicit host
and port header names. Tomcat accepts the sanitized metadata from its private
Docker peer; backend isolation means frontend is the only ingress proxy. Its
native internal-proxy defaults cover the private bridge. Do not publish backend
or attach untrusted containers to this bridge. Local Compose leaves forwarding
processing disabled and remains direct HTTP. No new Spring profile is needed.
See [Spring Boot 3.5 proxy support](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html).

## Health and operational paths

Exact `/internal/health` from an approved ALB peer proxies backend `/actuator/health`.
Backend UP returns 200; backend DOWN/non-200 or unavailable backend cannot produce
a successful probe. Actuator `show-details: never` remains unchanged. The health
proxy removes request cookies/authorization and does not forward a request body.
Container probes retain their existing local paths; target health is backend-aware.

Nginx returns 404 for other internal/Actuator/Swagger/OpenAPI paths, including SPA
fallback attempts. Untrusted direct requests to the exact health path also get
404. Public ALB traffic cannot reach it because of priority 1. These are different
controls: a trusted target probe is not equivalent to public listener traffic.
OpenAPI remains enabled on the development backend and in existing tests; the
Nginx edge does not serve developer documentation.

Health is an availability signal, not access control. ALB can **fail open** when
all registered targets are unhealthy. Listener rules, SGs, Nginx routing and the
unpublished backend remain security boundaries even then.
See [AWS target health behavior](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/target-group-health-checks.html).

## Browser and abuse protections

- HSTS is `max-age=31536000` only for trusted external HTTPS. No includeSubDomains
  or preload commitment; local HTTP emits no HSTS.
- Edge owns one value for nosniff, DENY and strict-origin-when-cross-origin, and
  hides duplicate upstream copies (including HSTS) for API responses.
- Actual Angular output uses generated scripts and component/runtime styles.
  A strict CSP needs tested nonce/hash/build integration; no permissive placeholder
  CSP or unsafe-eval is introduced. CSP remains a future hardening consideration.
- `/api/auth/login` and `/api/auth/register` share a per-sanitized-client-IP
  30 requests/minute zone with burst 20, nodelay, and 429 on overflow. This allows
  short interactive bursts but needs real NAT/traffic tuning. It is neither
  account lockout nor DDoS protection. Direct local HTTP is exempt. Refresh,
  logout, CSRF bootstrap, CRUD, health and assets are not limited. No WAF added.
- Explicit 1 MiB request-body cap accommodates current JSON payloads (task
  descriptions are capped at 4,000 characters); there is no upload feature.
- API proxy caching remains off; upstream cache controls and Set-Cookie survive.
  Existing hashed-asset caching and SPA routing remain intact. Stock Nginx
  proxy connect/send/read timeouts remain 60 seconds for this synchronous API;
  no speculative WebSocket support or timeout tuning is introduced.

Authentication is unchanged: short-lived Bearer access tokens, HttpOnly refresh
cookie at `/api/auth`, SameSite=Strict, and Secure forced in production. The
XSRF-TOKEN cookie is intentionally JavaScript-readable, SameSite=Strict and Secure
in production; unsafe requests still require the CSRF header. Nginx preserves
cookies and X-XSRF-TOKEN. Same-origin `/api` needs no production CORS policy;
no wildcard CORS is added.

## Local evidence and limits

Cached Nginx 1.30.5 reports `http_realip_module`; `nginx -t` accepts RealIP and
limit_req directives and isolated requests exercise both. `test_edge.sh` builds
no infrastructure: it uses an internal-only disposable Docker network, no published
ports and cached images with pulls forbidden. Actual trusted/untrusted source
addresses exercise HTTPS/port/host normalization, spoofed XFF prefixes, local HTTP,
SPA/API behavior, duplicate headers, cookie/CSRF preservation, 413, 429, health
200/503 and direct operational-path 404s. ALB rules are separately reviewed and
asserted statically, not represented as a deployed ALB. Focused embedded Tomcat
tests verify native HTTPS, secure flag, port, host/client IP and local HTTP.
The Tomcat test uses a real embedded application context and Boot's embedded
server customizer. A mock servlet context is treated as a WAR deployment and
excludes that customizer, causing a false HTTP/localhost result even with NATIVE
configured. Fixing this test harness required no production security change.

Final recovery verification: 38 focused backend tests and the full 487-test backend
suite pass with zero failures, errors or skips. The earlier 593 frontend tests and
production image build were not rerun because frontend/Nginx files were unchanged
during recovery; all three image configuration hashes match the worktree. Edge tests
also reject malformed forwarded ports and check both login and registration 429s.
Offline Terraform fmt/validate/graph, eu-west-1 cfn-lint, edge parity assertions and
cached read-only Gitleaks pass. This evidence proves the local reference behavior,
not a live ALB, certificate, DNS alias or AWS deployment.

No AWS/RDS/ACM/DNS/ECR action, real domain resolution or production Compose startup
is authorized. MS9.6 owns logging/monitoring configuration; MS9.7 owns deployment,
rollback and recovery sequencing; MS9.8 owns the final smoke/readiness review.
