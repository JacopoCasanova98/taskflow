# ADR 002: AWS reference deployment architecture

## Context

TaskFlow has verified Java/Spring Boot and Angular/Nginx containers, PostgreSQL,
Flyway-owned schema and same-origin JWT/refresh-cookie/XSRF authentication.
The portfolio demonstrates AWS networking, security and both IaC tools through
a reference architecture that would be operable by one developer. TaskFlow will
never provision or host AWS infrastructure; credentials and spend are not required.
Low traffic does not require HA, Kubernetes or zero-downtime deployment. Decision date: 2026-09-18.

## Considered options

- ALB + EC2 running application containers with Compose + RDS PostgreSQL.
- ALB + ECS Fargate + RDS: removes host maintenance but adds task/service/IAM and
  networking changes, including the existing containers' shared port 8080 issue.
- Direct EC2/Compose with container PostgreSQL: cheaper but places database
  backups, patching, TLS and application recovery on one host/operator.
- ECS on EC2 + RDS: adds scheduling and capacity management while retaining host
  maintenance; little benefit for the initial single application host.

## Decision

Select the AWS reference architecture that TaskFlow's IaC models, deployment-ready
in principle but never applied by this project: **eu-west-1**, public ALB with ACM HTTPS, one EC2 application host and
private **RDS PostgreSQL 17 Single-AZ**. A two-AZ VPC provides public ALB subnets
and isolated DB subnets. EC2 uses public-IP egress with inbound frontend traffic
only from ALB SG; no SSH, backend host port, public DB or NAT Gateway.

Use ECR for release images, Secrets Manager for DB/JWT values, an instance role,
SSM Session Manager for administration and CloudWatch for logs/alarms. The final
IaC uses an external regional ACM certificate input; Route 53 and certificate
lifecycle remain external/MS9 design boundaries, with
`taskflow.example.com` as the placeholder. No real domain, zone or certificate
is required or purchased. Preserve
frontend `/api` proxying and Docker backend DNS. Production restores Secure
cookies, trusted proxy headers, DB TLS and deployment-owned auth rate limiting.

Ireland and Milan both cover the required services. Ireland avoids opt-in setup;
no measured Italy-latency/residency requirement justifies preferring Milan.
Region remains an input. No regional price advantage is asserted without a quote.

Terraform is the primary implementation; CloudFormation reproduces the same core
as un-applied portfolio artifacts. Local/static verification without AWS
authentication is the acceptance model; no apply, stack or change set is executed.
A real operator outside this project would choose one owner for live resources.

## Consequences

If provisioned, managed TLS and database lifecycle would improve the public deployment boundary while
existing application containers remain useful. Host OS/Docker patching and runtime
bootstrap remain operator duties. Flyway stays the sole application-schema owner.
RDS administrative credentials are distinct from the application's schema role.
No AWS resources are provisioned by this project; Terraform and CloudFormation
are portfolio artifacts representing this architecture. No IaC implementation is
created by this decision. State, DNS, secrets and billing resources remain
non-provisioned; validation needs neither a real backend nor secret population.
The estimated $90–130/month applies only if provisioned; actual project AWS
infrastructure spend is $0.

## Accepted trade-offs

One host and Single-AZ database are single points of failure despite multi-AZ
networking. Deployments/maintenance may interrupt service. Public-IP egress relies
on restrictive ingress SGs and trusted host administration. ALB has a meaningful
fixed cost; internal ALB-to-Nginx HTTP and initially shared migration/runtime DB
role are accepted within the restricted trust boundary. Short logs and manual
rotation require deliberate operations. This is not an HA or hard-budget system.

## Future evolution

A real operator outside this project could add redundant compute/ECS and Multi-AZ RDS when recovery or traffic needs justify
them; private egress/endpoints, end-to-end TLS, separate DB roles and WAF follow
specific security requirements. Revisit regional placement if measured latency,
residency or verified pricing changes the trade-off.

The [AWS architecture analysis](../AWS_ARCHITECTURE.md) contains the diagram,
dated sources, cost allowances, exact boundaries and MS9 design handoff. The
architectural rationale does not depend on a fixed monthly quote. MS8.1 is
complete; implemented IaC and final static-verification status are recorded in
[ARCHITECTURE.md](../ARCHITECTURE.md). MS8.9 modernizes ECR scanning in both forms
with default-off regional ownership; shared registries keep their existing owner.
MS9 Deployment Design & Production Readiness remains unstarted and non-provisioning.
