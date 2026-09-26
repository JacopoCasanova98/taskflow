# Registry delivery design — MS10.3

This is the authoritative registry-delivery design and offline validation contract.
**No registry operation is performed.** The [zero-AWS policy](AWS_ARCHITECTURE.md#hard-project-execution-policy)
and [MS9.2 release identity](CONTAINER_RELEASE.md) remain authoritative. No actual
release record, account, repository URL, registry digest or scan result is supplied.

## Executed capabilities and reference interfaces

| Capability | Status |
| --- | --- |
| GitHub application quality gates | Executable; hosted CI proven |
| Buildx production builds and runtime image inspection | Executable; hosted CI proven, local images only |
| Release-pair schema, validator and synthetic tests | Executable offline; structural checks only |
| Short-lived registry identity | Reference-only interface owned by MS10.5; no OIDC configuration |
| ECR authentication, publication, scan retrieval and digest retrieval | Reference-only; never executed by TaskFlow |
| Deployment consumer | Future MS10.4 interface; not implemented here |

The normal [CI workflow](../.github/workflows/ci.yml) remains unchanged: quality
gates → backend/frontend Buildx matrix → local inspection, with `push: false`.
PR/feature CI never publishes release images. Its ephemeral images and local
image IDs do not constitute a registry release.

## Conceptual release lifecycle

**REFERENCE ONLY — requires independent authorization outside project execution.**

1. Select one reviewed, clean full Git commit. Preserve that exact revision across
   both component contexts; a PR merge revision is not interchangeable with another
   commit. Pass application quality, Docker build/inspection and the existing
   MS9.2 dependency/security gates. Normal CI's four green jobs alone do not supply
   every future release eligibility decision.
2. Build both production images for `linux/amd64`, recording one UTC pair build-start
   timestamp. Preserve the gated artifacts through publication; do not rebuild
   between gating, publication and digest capture. MS10.2's OCI source/revision
   labels aid traceability but do not independently attest artifact origin.
3. Obtain the MS10.5-owned short-lived authorized registry identity. This document
   defines no role, credentials, trust policy, token or OIDC implementation.
4. Publish both artifacts to the intended existing frontend/backend ECR repositories
   using immutable `git-<full-git-sha>` tags. Reconcile pre-existing tags before any
   retry; never overwrite an immutable artifact or silently substitute another commit.
5. Obtain completed applicable BASIC scan results and review them for **both**
   images under the independent operator's vulnerability policy. Missing, failed
   or unreviewed results stop eligibility; push alone is insufficient.
6. Capture registry **manifest** digests from authorized registry evidence and verify
   their association with both repositories, expected Git tags, source revision and
   platform. Local `docker image inspect` IDs/config digests are not substitutes.
7. Produce and validate the complete pair record. Join it to source/build/registry
   and scan-review evidence before treating it as an eligible release.
8. A future authorized workflow may persist the validated record for a future
   MS10.4 deployment consumer. Neither persistence nor deployment is implemented here.

## Existing repositories and identity

Exactly two repositories remain: `<name-prefix>-frontend` and `<name-prefix>-backend`.
Terraform's `aws_ecr_repository.app["frontend"]` and `app["backend"]` expose
`ecr_repository_urls`; CloudFormation exposes the corresponding frontend/backend
repository URLs. The name prefix is supplied by the independent environment, not
hardcoded to a project/environment in the validator. Both components must share
one registry account, region and prefix. Actual inputs must be checked against
the intended IaC outputs by the future producer; offline syntax cannot identify
the authorized account or prove a repository exists.

Keep `IMMUTABLE` tags, AES256 encryption and expiry of untagged images after seven
days. BASIC scan-on-push is an external registry-owner prerequisite by default;
registry scanning ownership remains explicitly opt-in. No third repository,
lifecycle change or IaC change is introduced.

Canonical tags are derived as `git-<lowercase git_commit>` for both components,
not stored twice. An optional shared `semantic_version` is an additional immutable
alias to the exact already-published artifacts, never a rebuild. No `latest`,
`prod`, `production`, `stable` or `current` release/deployment selectors are allowed.
Production identity remains `<repository>@sha256:<registry-manifest-digest>`.

## Versioned release-pair record

The [JSON Schema](../ops/release/release-pair.schema.json) describes version `1.0`.
The [standard-library validator](../scripts/release/validate_release_pair.py)
enforces its fixed field constraints plus cross-field and calendar checks.
Generic JSON Schema validation alone does not check equal repository prefixes or
real calendar dates. Unknown fields and duplicate JSON keys are rejected.

| Field | Contract |
| --- | --- |
| `schema_version` | Required string `1.0`; unsupported versions fail closed |
| `git_commit` | Required exactly 40 ASCII hexadecimal characters; shared by both components |
| `frontend_repository`, `backend_repository` | Required private ECR URI form, without scheme/tag/digest; component suffixes and identical registry/region/prefix |
| `frontend_digest`, `backend_digest` | Required `sha256:` plus 64 hexadecimal characters, each with its repository context |
| `digest_source` | Required `ecr-registry-manifest` assertion applying to both digests; local/config provenance rejected |
| `build_timestamp` | Required valid UTC calendar date/time `YYYY-MM-DDTHH:MM:SSZ`, whole seconds, no offset or leap-second representation |
| `platform` | Required `linux/amd64`, shared by both components |
| `semantic_version` | Optional `v`-prefixed SemVer core and optional prerelease, e.g. `v1.2.3-rc.1`; no numeric leading zeros or `+` build metadata (not valid in Docker tags); maximum 128 characters |

Repository names follow the existing lowercase hyphenated IaC naming pattern,
with no namespace paths. Version 1 supports the reference architecture's commercial
ECR hostname form ending in `amazonaws.com`, not public ECR, China hostname suffixes
or arbitrary registries. It validates shape, not a live region/account catalog. Git/digest hex
is accepted in either case and lowercased only in derived output; input is not rewritten.

One top-level Git SHA and platform own both components. Component-specific commit,
platform or tag overrides are rejected. No frontend-only or backend-only record
validates. The producer must still prove both artifacts were actually built from
that commit/platform; a declaration alone cannot establish provenance.

**Offline limitation:** a Docker image ID/config digest can have exactly the same
`sha256:<64 hex>` syntax as a registry manifest digest. The validator rejects short,
bare, tagged, missing and explicitly local-origin values. It cannot detect a local
ID dishonestly asserted as registry-origin. `digest_source` is a required producer
assertion, not cryptographic evidence. Registry provenance must be independently
verified before deployment eligibility; validator success never grants that approval.

Offline invocation, using a candidate supplied by an independently authorized process:

```sh
python3 scripts/release/validate_release_pair.py /path/to/candidate.json
```

Exit 0 means **structurally valid only**; stdout contains derived `git_tag`,
`frontend_reference` and `backend_reference`. Stderr states the evidence limitation.
Invalid input exits 1 without echoing candidate values. It reads local files only:
no subprocess, AWS SDK, credential lookup, network call, registry query or upload.
No apparently real example instance is committed; artificial data exists only in tests.

## Scan eligibility and partial failures

Scan evidence remains a **separate future eligibility record**, avoiding invented
scan success fields in the pair schema. A future consumer must require completed
and reviewed evidence bound to the exact Git SHA, both repository/digest identities
and platform, with applicable policy/review decision and trusted origin. Missing
or mismatched evidence means ineligible. No scan retrieval, threshold automation,
approval record schema or deployment consumer is implemented in MS10.3.

Frontend published + backend failed, or the reverse, is an incomplete release.
Keep the successful immutable artifact unchanged. Complete the missing artifact
from the same reviewed commit with all gates, or make a new release decision.
Do not retag, overwrite or mix revisions. Reconcile actual registry identities on
an authorized retry; never assume a prior interrupted publication succeeded.

## Future artifact interface

A future authorized release workflow may persist `release-pair.json` with schema
version `1.0` as a GitHub Actions artifact associated with the full commit and
producing run. Preserve provenance and separate scan-review evidence alongside it;
consumers must revalidate the JSON and evidence association. A record file, GitHub
artifact checksum or local image ID is not a registry manifest digest.

MS10.10 owns any actual GitHub Release asset. No artifact upload, release asset,
fake pair, executable registry workflow or normal-CI publication is added here.
MS10.4 and MS10.5 remain unstarted; these are input/output boundaries only.

## Local validation

```sh
python3 -m unittest discover -s scripts/release/tests -v
```

Tests exercise artificial positive/negative records, CLI error handling and focused
ECR/Compose/MS9.2 cross-contract checks. Existing production readiness and runbook
static tests provide additional structured IaC/runtime parity checks; CI contract
tests confirm the no-push workflow remains intact. Cached tooling can run read-only
with networking disabled. No hosted ECR execution is required for this design milestone.

Verified locally on 2026-09-26 using cached `taskflow-cfn-lint:1.57.0`, with pulls
forbidden, networking disabled and the repository mounted read-only: 11 new
release-contract tests, five existing production readiness tests, five runbook
tests and four CI contract tests passed. No IaC parity defect was found. No
application suites, Docker builds, registry calls or AWS operations were run.

**MS10.3 COMPLETE — registry delivery design validated, no registry operation performed.**
