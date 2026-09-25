# CI/CD

## GitHub Actions baseline (MS10.1)

[CI](../.github/workflows/ci.yml) runs on pull requests targeting `main`, pushes
to `main` or `feature/**`, and manual dispatch. Superseded runs are cancelled per
workflow and PR number or branch ref; separate branches do not cancel each other.
The two jobs are independent:

| Job | Gate | Environment | Timeout |
| --- | --- | --- | --- |
| `backend-quality` | `./mvnw -B -ntp verify` in `backend` | Temurin Java 21, Ubuntu 24.04 | 30 minutes |
| `frontend-quality` | `npm ci`, then `npm run quality` in `frontend` | Node 22.23.1, Ubuntu 24.04 | 20 minutes |

The Maven Wrapper remains authoritative, including PostgreSQL Testcontainers,
JaCoCo thresholds and SpotBugs/FindSecBugs. The official
[Ubuntu 24.04 runner image](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
includes Docker client/server; `docker info` checks daemon accessibility before
verification. No PostgreSQL service container or alternate database is introduced.
Actual hosted Testcontainers behavior remains pending the first run.

Frontend quality owns formatting, lint, coverage tests and production bundle
build. Node 22.23.1 matches the local baseline and satisfies installed Angular 22.1
engine requirements (`^22.22.3 || ^24.15.0 || >=26.0.0`); see also
[Angular compatibility](https://angular.dev/reference/versions). The repository
records npm 10.9.8 in `packageManager`; setup-node uses the selected Node release's
bundled npm, and `npm ci` installs the lockfile without updating it. No separate
global npm installation or CI-only quality standard is introduced.

[`scripts/quality.sh`](../scripts/quality.sh) remains the local aggregate gate
(with frontend dependencies already installed). Timeouts allow cold dependency
caches and hosted-runner variation; they are bounds, not duration guarantees.

## Permissions, dependencies and boundaries

Workflow permissions are only `contents: read`; checkout does not persist Git
credentials. No repository secrets, variables, AWS credentials or OIDC role are
required. Official setup actions cache Maven/npm dependencies using
`backend/pom.xml` and `frontend/package-lock.json`. Build outputs, coverage,
Docker layers and application images are not cached.

Only official actions are used, pinned to full upstream commits. Release tags
and commit targets were checked against the official repositories on 2026-09-25:

| Action | Verified release | Commit |
| --- | --- | --- |
| checkout | [v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1) | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| setup-java | [v6.0.1](https://github.com/actions/setup-java/releases/tag/v6.0.1) | `de7274f081f381c8f8158605e0321c36c376e2e6` |
| setup-node | [v7.0.0](https://github.com/actions/setup-node/releases/tag/v7.0.0) | `820762786026740c76f36085b0efc47a31fe5020` |

CI performs no container image build, registry publishing, deployment or AWS
operation. Testcontainers starts disposable local test containers only.
`security-audit.sh`, OWASP Dependency-Check and `npm audit` are excluded from this
baseline because their vulnerability-feed automation needs separate decisions.
Their existing local security contract is unchanged.

## Validation and status

[`test_ci_workflow.py`](../scripts/ci/test_ci_workflow.py) checks YAML with PyYAML,
exact events/permissions, independent jobs, runtime/cache inputs, full action
pins and allowed commands. It is a static contract, not a GitHub Actions emulator.
Use `python3 scripts/ci/test_ci_workflow.py` where PyYAML is available. For this
milestone the existing cached `taskflow-cfn-lint:1.57.0` image supplies Python and
PyYAML with networking disabled, pulls forbidden and the repository read-only.
Actionlint is unavailable locally; static parsing cannot prove hosted execution.

Reproduce the cached static check from the repository root:

```bash
docker run --rm --pull never --network none \
  --mount "type=bind,source=$PWD,target=/repo,readonly" --workdir /repo \
  --entrypoint python taskflow-cfn-lint:1.57.0 scripts/ci/test_ci_workflow.py
```

**MS10.1 IMPLEMENTED — first GitHub-hosted run pending.** Both hosted jobs must
succeed after an independently authorized commit/push before MS10.1 is complete.
No workflow has been dispatched as part of local implementation.

## Later ownership

MS10.2 owns Docker CI; MS10.3 registry delivery design; MS10.4 deployment design
and dry-run contracts; MS10.5 CI/CD identity/secrets; MS10.6 branch/PR quality
gates and further quality/security automation decisions. These are unimplemented.
GitHub CI is real executable automation. AWS registry delivery and deployment
remain reference/design only under the zero-AWS policy.
