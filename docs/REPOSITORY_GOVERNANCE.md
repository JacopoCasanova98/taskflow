# Repository governance — MS10.6

**MS10.6 COMPLETE — hosted ci-gate and live main ruleset verified.**

The contribution path is feature branch → PR to `main` → GitHub Actions →
`ci-gate` → merge. Feature branches remain development surfaces, not release or
AWS-authorized surfaces. Main protection is a separate GitHub repository policy:
a workflow file alone cannot prevent direct pushes or enforce PR merging.

## Three distinct states

| Layer | Current evidence |
| --- | --- |
| Repository implementation | Aggregate job, PR template and offline policy tests implemented |
| Desired GitHub policy | [Reference target](../ops/github/main-ruleset.reference.json), retained as the repository-owned contract |
| Verified live enforcement | Active GitHub ruleset `24046584`, `Taskflow main Ruleset`, protects the default branch `main` |

The JSON is **REFERENCE TARGET — NOT PROOF OF LIVE GITHUB CONFIGURATION**. It is
a versioned semantic checklist, not a GitHub API request or imported live export.
`desired_enforcement: active` describes the target only. Live enforcement was
activated separately in GitHub and verified before this documentation close-out.
The evidence below records that state; this edit performs no repository-admin mutation.

## Stable aggregate gate

[CI](../.github/workflows/ci.yml) retains the existing quality jobs and Docker
matrix unchanged. `ci-gate` depends directly on `backend-quality`,
`frontend-quality` and the whole `docker-build` matrix. It uses `if: always()` so
an upstream failure or skip does not simply skip the required gate. Its sole
Bash step accepts only three `success` results passed through environment
variables. Failure, cancellation, skip, empty or unexpected results fail closed.
[GitHub dependency/conditional semantics](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-jobs).

Both Docker matrix executions must succeed. `fail-fast: false` and absence of
`continue-on-error` preserve visibility and failure propagation. The gate uses
Ubuntu 24.04 with a two-minute timeout; no checkout, setup action, test rerun or
Docker operation is needed. A cancelled workflow or unavailable runner may never
produce a completed gate; that must remain non-green and block acceptance, not
be treated as success. Hosted run #4 successfully executed the complete graph;
local tests separately exercise failure combinations in the contract and shell.

The job ID and display name are both exactly `ci-gate`. Required-check configuration
uses that job name, not `CI`, `CI / ci-gate`, a branch or matrix name. Keep this
name unique across workflows. Run #4 and the live ruleset confirm `ci-gate` as
the required check. No source-app integration ID is asserted by this close-out.
[GitHub check naming](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/troubleshooting-rules),
[unique job names](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).

## Desired policy and active main ruleset

The reference targets `refs/heads/main`. The live ruleset uses `~DEFAULT_BRANCH`,
which currently resolves to `main`; it follows any future default-branch change,
so such a change requires policy review. Active enforcement has no bypass actors:

- Require a pull request, with **zero required approving reviews**. The sole
  maintainer can merge a green PR without a fictional second reviewer. Do not
  add code-owner approval or last-push approval requirements. Increase approval
  count through a separate decision when collaborators join.
- Require `ci-gate` and strict
  up-to-date checks. If `main` advances, update the PR branch and rerun CI before
  merging; extra builds are an accepted cost of checking the current base.
- Require conversation resolution. This closes actionable discussion without
  imposing an additional approving reviewer; a PR with no threads is unaffected.
- Block force pushes and branch deletion. Leave no routine administrator/app
  bypass. Administrators retain policy-editing authority; this is not protection
  against a malicious repository owner changing the rules themselves.

No signed-commit, merge-queue, deployment or linear-history requirement is added.
Live allowed merge methods are `merge`, `squash` and `rebase`, preserving
TaskFlow's deliberate PR merge-commit history. Do not enable
“restrict updates,” which would unnecessarily block ordinary approved merges.
[GitHub available rules](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets).

Use branch rulesets, not paid push-rule features. GitHub documents branch rulesets
for public repositories on Free and private repositories on qualifying plans;
the verified live branch ruleset is active for this repository. [Ruleset availability](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets).

## Security and local checks

MS10.5 remains authoritative: ordinary PR/branch CI has only `contents: read`,
no secrets, OIDC, AWS credentials, registry publication or deployment. Triggers
remain PR → main, push → main/feature/**, and manual dispatch. No
`pull_request_target` or privileged workflow is introduced. Governance cannot
replace review of changes to the workflow itself: zero approvals is an explicit
solo-maintainer tradeoff, not independent human review.

[PR template](../.github/pull_request_template.md) asks for scope, appropriate
validation and relevant documentation/security/operations impact. Explain
inapplicable checks rather than running unrelated suites.

```text
python3 -m unittest scripts.ci.test_ci_workflow -v
python3 -m unittest discover -s scripts/ci/tests -v
```

The policy suite uses cached PyYAML and synthetic mutations, not live GitHub APIs.
It checks dependencies, `always()`, stable naming, authority, triggers and the
reference target. The actual gate shell is exercised across every combination
of success/failure/cancelled/skipped/empty results; intentionally weakened shell
variants must fail these checks. CI, identity, release-pair, deployment-plan,
readiness and runbook regressions remain required locally. These tests do not
emulate GitHub Actions or prove remote policy enforcement.

Local validation passed: seven repository-policy tests (including 125 synthetic
result combinations), four CI checks, 14 identity tests, 11 release-pair tests,
13 deployment-plan tests, five readiness checks and five runbook checks. Cached
`taskflow-cfn-lint:1.57.0` ran with no pulls, networking disabled and the repository
mounted read-only. `actionlint` is unavailable locally; no tool was downloaded.

## Hosted CI and live enforcement evidence

The verified GitHub evidence supplied for this documentation close-out is:

- Workflow `CI`, push run **#4**, [run 36259778449](https://github.com/JacopoCasanova98/taskflow/actions/runs/36259778449),
  commit `f898a6b5b6dafdd6ae9576efb5383d0a097368b7`, conclusion `success`.
- `backend-quality`, `frontend-quality`, `docker-build (backend)`,
  `docker-build (frontend)` and `ci-gate`: **SUCCESS**.
- Gate step `Require every upstream gate to succeed`: **SUCCESS**.

| Live ruleset field | Verified value |
| --- | --- |
| ID / name | `24046584` / `Taskflow main Ruleset` |
| Target / source | `branch` / `JacopoCasanova98/taskflow` |
| Enforcement | `active` |
| Target condition | `~DEFAULT_BRANCH`, resolving to `main` |
| Pull request | Required; approving reviews = `0` |
| Review-thread resolution | Required (`true`) |
| Required status check | `ci-gate` |
| Strict/up-to-date policy | `strict_required_status_checks_policy = true` |
| Deletion / non-fast-forward | Blocked / blocked |
| Bypass actors / current-user bypass | None / `never` |
| Allowed merge methods | `merge`, `squash`, `rebase` |

The earlier no-ruleset observation is superseded by this verified live activation.
The reference JSON does not configure GitHub and remains unchanged. Its exact-main
selector and the live default-branch selector currently protect the same branch.
This close-out records successful hosted execution and live configuration; it
claims no destructive push/deletion test or new GitHub admin operation.

MS10.6 is complete. See the [roadmap](ROADMAP.md) for current milestone status.
Official GitHub documentation cited above was verified **2026-09-26**.
