# Container release contract — MS9.2

This is a reference design, not an executable release runbook. The
[zero-provisioning policy](AWS_ARCHITECTURE.md#hard-project-execution-policy)
remains authoritative: TaskFlow validation uses no AWS credentials or APIs,
registry login, ECR pull/push, registry modification, infrastructure provisioning,
production Compose startup or real release. All operator steps below describe
work outside this project's execution scope.

## One source commit, two artifacts

Frontend and backend form **one coordinated release**, built from the same
reviewed, clean Git checkout. The full Git commit SHA is the canonical source
identity; both repositories always receive `git-<full-git-sha>`. Abbreviated SHAs
are not release identities. Never deploy using `latest`, `prod`, `production`,
`stable` or `current` selectors.

An optional `vX.Y.Z` tag is a secondary, explicit product-release alias. Both
images use the same semantic version and recorded commit; versions are never
chosen independently. Add an alias to the exact published artifact, without
rebuilding it. Git SHA tag = always; semantic version tag = optional; digest =
deployment identity. All release tags are immutable and must never be overwritten.
A wrong release requires a new reviewed commit/identity or a previous immutable
release pair; rebuilding a commit does not authorize replacing its existing tag.

Production supplies `TASKFLOW_FRONTEND_IMAGE` and `TASKFLOW_BACKEND_IMAGE` as
`<repository>@sha256:<digest>`, using verified published manifest digests.
Tags support human traceability, discovery and Git correlation; digests identify
exact runtime artifacts and rollback targets. Do not substitute a local image
ID/config digest for the registry manifest digest. Compose only requires non-empty
external references; the operator/future CI eligibility gate enforces digest form
and pair consistency. No example digest is committed to runtime configuration.

## Build platform, metadata and reproducibility

Both release builds explicitly target `linux/amd64` for the approved x86_64 EC2
host, regardless of workstation architecture. A builder must support that target
natively or through emulation. Neither arm64 nor a multi-architecture manifest is
required. Preserve the existing non-root users, port/tool availability and
read-only-root compatibility specified by [the runtime contract](PRODUCTION_RUNTIME.md).

Dockerfiles deliberately remain unchanged. MS10.2 CI now applies and inspects
`org.opencontainers.image.source` and `org.opencontainers.image.revision` labels
on both locally built images. This completes the CI metadata work deferred by
MS9.2 without publishing images. No semantic version or creation-time label is
fabricated. These labels aid inspection but do not independently establish an
approved release pair. Local builds require no release arguments; no credentials,
tokens or personal paths enter image metadata. MS10.3 defines the separate
[registry delivery design and offline pair contract](REGISTRY_DELIVERY.md).

Current base images use versioned tags. Published application images are pinned
by digest for deployment, but this does **not** promise byte-for-byte reproducible
builds: base tags, build tooling and fetched dependencies can affect rebuilds.
Exact base-image digest pinning is separate supply-chain hardening. MS9.2 queries
no registry to invent/populate digest constants. The contract makes source,
platform, gates and artifact identity repeatable, not identical rebuild bytes.

## Conceptual operator sequence

**REFERENCE ONLY — DO NOT RUN AS PART OF TASKFLOW VALIDATION**

1. Check out the reviewed commit into a clean workspace; both build contexts
   must remain at that commit throughout the release.
2. Pass the existing `scripts/quality.sh` and `scripts/security-audit.sh` gates
   for that checkout. Docker packaging is not a substitute for these gates.
3. Resolve the full Git SHA and record one UTC build-start timestamp for the pair.
4. Build both images with Buildx for `linux/amd64`, loading the results locally.
5. Record release metadata and verify OCI source/revision labels as implemented by MS10.2 CI.
6. Obtain short-lived AWS identity and authenticate to the intended ECR registry.
7. Tag both already-built artifacts with their immutable Git-SHA repository tags.
8. Push frontend and backend to their respective authorized repositories.
9. Wait for successful scan completion under the applicable registry policy and
   review findings against operator/CI acceptance policy for **both** artifacts.
   Missing, failed or unreviewed scans do not establish deployment eligibility.
10. Capture and verify each resulting registry manifest digest, repository,
    source association and amd64 platform. Do not rebuild between push and capture.
11. Produce the complete release-pair record; optional version aliases must map
    to these exact artifacts. Incomplete or inconsistent pairs are ineligible.
12. A real operator would deploy using those two digest references; deployment
    procedures are MS9.7 scope and are not executed here.

**REFERENCE ONLY — DO NOT RUN AS PART OF TASKFLOW VALIDATION**

These shell examples use intentionally unresolved placeholders. `<GIT_SHA>` means
that same full reviewed commit SHA throughout. `<FRONTEND_REPOSITORY>` and
`<BACKEND_REPOSITORY>` mean full repository URIs in the intended registry, ending
in `<name-prefix>-frontend` and `<name-prefix>-backend`. They are not populated
from AWS during validation.

```sh
docker buildx build --platform linux/amd64 --load \
  --tag 'taskflow-frontend:git-<GIT_SHA>' ./frontend
docker buildx build --platform linux/amd64 --load \
  --tag 'taskflow-backend:git-<GIT_SHA>' ./backend

# Only after the independent operator establishes short-lived AWS identity:
aws ecr get-login-password --region '<AWS_REGION>' |
  docker login --username AWS --password-stdin \
    '<AWS_ACCOUNT_ID>.dkr.ecr.<AWS_REGION>.amazonaws.com'

docker tag 'taskflow-frontend:git-<GIT_SHA>' '<FRONTEND_REPOSITORY>:git-<GIT_SHA>'
docker tag 'taskflow-backend:git-<GIT_SHA>' '<BACKEND_REPOSITORY>:git-<GIT_SHA>'
docker push '<FRONTEND_REPOSITORY>:git-<GIT_SHA>'
docker push '<BACKEND_REPOSITORY>:git-<GIT_SHA>'
```

Buildx `--platform` makes the target explicit and `--load` loads the single-platform
result locally; these commands do not build-and-push implicitly. See the
[Docker Buildx reference](https://docs.docker.com/reference/cli/docker/buildx/build/).
The builds themselves may need external base images/dependencies, so none is run
as part of this documentation-only validation.

Authentication conceptually follows short-lived AWS identity → ECR
`GetAuthorizationToken` → Docker registry login → pushes to authorized repositories
only. The CLI exposes token retrieval through `aws ecr get-login-password`.
No login command belongs in an executable project script. No long-lived access
keys, credentials in `.env`, Docker auth configuration or registry tokens belong
in the repository or release record. MS10 owns future CI workload identity and
short-lived authentication design; GitHub OIDC is not implemented here.

## Existing ECR contract and eligibility

Use exactly the two existing repositories, `<name-prefix>-frontend` and
`<name-prefix>-backend`, with `IMMUTABLE` tags and AES256 encryption. No third
repository or IaC change is needed. ECR rejects overwriting immutable tags; see
[AWS tag immutability](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-tag-mutability.html).

Preserve the MS8.9 decision: repository-level scanning configuration is absent.
BASIC registry scan-on-push is an external registry-owner prerequisite by default.
IaC models it only when TaskFlow explicitly owns the regional scanning singleton;
its prefix filter does not make ownership repository-local. A push alone does
not guarantee scanning. No Enhanced/Inspector scanning is introduced.

Eligibility is: release image pushed → applicable registry scan-on-push policy →
completed scan results reviewed according to operator/CI policy → digest eligible
for deployment. Apply this to both images before approving the pair. MS10 owns
policy automation; TaskFlow does not execute this sequence during MS9. BASIC
scanning covers OS vulnerabilities and complements the application dependency
security gate; see [AWS BASIC scanning](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-scanning-basic.html).

The existing lifecycle rule expires untagged images after seven days measured
from image push time. Immutable tagged releases are **not** auto-expired by the
TaskFlow lifecycle policy. This retains rollback/history identities but can grow
storage over time; immutability is not protection against deletion. A future
operator must decide rollback/history retention before introducing any tagged
expiry policy. No new retention rule is defined here.

## Partial push and release record

ECR does not provide a multi-repository transaction for this pair. If only one
push succeeds, the release is **not deployable**. Keep the successful artifact
and tag unchanged; complete the missing artifact from the same reviewed source,
with all gates, or make a new release decision/commit. Never retag or overwrite
the successful artifact to repair the pair. An interrupted retry must reconcile
existing published identities rather than blindly repushing an immutable tag.
Production requires both verified digests from the same commit.

MS10.3 implements the [versioned schema](../ops/release/release-pair.schema.json)
and [offline validator](../scripts/release/validate_release_pair.py) for these
concepts. No actual manifest with invented digests is created:

| Field | Required meaning |
| --- | --- |
| `schema_version` | Explicit supported contract version `1.0` |
| `git_commit` | Full reviewed Git commit SHA shared by both artifacts |
| `frontend_repository` | Full frontend repository URI, without tag/digest |
| `frontend_digest` | Verified published manifest digest, `sha256:<digest>` |
| `backend_repository` | Full backend repository URI, without tag/digest |
| `backend_digest` | Verified published manifest digest, `sha256:<digest>` |
| `digest_source` | Required `ecr-registry-manifest` assertion for both digests, not proof of provenance |
| `semantic_version` | Optional immutable `vX.Y.Z` or prerelease alias, identical for both images |
| `build_timestamp` | Pair build-start time in UTC RFC 3339 format |
| `platform` | `linux/amd64` for both artifacts |

The record becomes eligible only after both image identities and scan decisions
are verified. The MS10.3 validator checks structure, not registry provenance or
scan acceptance: local image/config digests can share registry digest syntax.
[REGISTRY_DELIVERY.md](REGISTRY_DELIVERY.md) defines the separate future eligibility
evidence and artifact interface. Neither registry publication nor release-record
upload is implemented; MS10.10 retains ownership of actual GitHub Releases.

Previous verified digest pairs supply rollback identities only. MS9.7 owns target
selection, runtime-input updates, container restarts, database compatibility and
recovery. MS9.3–MS9.6 and MS9.8 retain their secret/bootstrap, DB/TLS, proxy/HTTPS,
monitoring and readiness responsibilities. No later milestone is started here.
