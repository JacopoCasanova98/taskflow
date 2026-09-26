# Repository governance — MS10.6

**MS10.6 IMPLEMENTED — hosted ci-gate and live main-ruleset verification pending.**

The contribution path is feature branch → PR to `main` → GitHub Actions →
`ci-gate` → merge. Feature branches remain development surfaces, not release or
AWS-authorized surfaces. Main protection is a separate GitHub repository policy:
a workflow file alone cannot prevent direct pushes or enforce PR merging.

## Three distinct states

| Layer | Current evidence |
| --- | --- |
| Repository implementation | Aggregate job, PR template and offline policy tests implemented |
| Desired GitHub policy | [Reference target](../ops/github/main-ruleset.reference.json), not applied |
| Verified live enforcement | Pending; earlier observation found no rulesets, not reverified here |

The JSON is **REFERENCE TARGET — NOT PROOF OF LIVE GITHUB CONFIGURATION**. It is
a versioned semantic checklist, not a GitHub API request or imported live export.
`desired_enforcement: active` describes the target only. No repository-admin
mutation, hosted gate verification or ruleset activation occurs in this pass.

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
be treated as success. Hosted execution remains necessary to verify scheduling
and matrix aggregation; local tests exercise only the contract and shell logic.

The job ID and display name are both exactly `ci-gate`. Required-check configuration
uses that job name, not `CI`, `CI / ci-gate`, a branch or matrix name. Keep this
name unique across workflows. Verify the observed check and GitHub Actions app
source after the first hosted run, rather than inventing an integration ID.
[GitHub check naming](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/troubleshooting-rules),
[unique job names](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).

## Desired main ruleset

Target exactly `refs/heads/main`, with active enforcement and no bypass actors:

- Require a pull request, with **zero required approving reviews**. The sole
  maintainer can merge a green PR without a fictional second reviewer. Do not
  add code-owner approval or last-push approval requirements. Increase approval
  count through a separate decision when collaborators join.
- Require `ci-gate`, using the verified GitHub Actions source, and strict
  up-to-date checks. If `main` advances, update the PR branch and rerun CI before
  merging; extra builds are an accepted cost of checking the current base.
- Require conversation resolution. This closes actionable discussion without
  imposing an additional approving reviewer; a PR with no threads is unaffected.
- Block force pushes and branch deletion. Leave no routine administrator/app
  bypass. Administrators retain policy-editing authority; this is not protection
  against a malicious repository owner changing the rules themselves.

No signed-commit, merge-queue, deployment or linear-history requirement is added.
TaskFlow's deliberate PR merge-commit history remains supported. Do not enable
“restrict updates,” which would unnecessarily block ordinary approved merges.
[GitHub available rules](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets).

Use branch rulesets, not paid push-rule features. GitHub documents branch rulesets
for public repositories on Free and private repositories on qualifying plans;
confirm repository visibility/feature availability when configuring the live target.
If unavailable, stop acceptance and record the limitation rather than claiming
protection. [Ruleset availability](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets).

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

## Post-push acceptance — not executed now

1. After manual commit/push, let GitHub execute CI. Record run URL, reviewed
   commit and successful `backend-quality`, `frontend-quality`,
   `docker-build (backend)`, `docker-build (frontend)` and `ci-gate` results.
2. Confirm the check's exact name/source from GitHub. Separately configure the
   main branch ruleset against the reference target: active, exact main target,
   no bypasses, PR/zero approvals/resolved conversations, required `ci-gate`,
   strict checks, force-push and deletion blocks. No policy mutation is authorized
   by the present implementation task.
3. Read back the **live** ruleset from GitHub and inspect the effective rules
   targeting main, including any overlapping policy. Record its ID/URL, active
   enforcement and every required setting; verify PR merge-box enforcement and
   that unresolved/non-green/out-of-date checks prevent merging. Do not force-push
   or delete main as a test. Confirm merge commits remain available and that no
   bypass defeats the intended ordinary contribution path.
4. Only after hosted CI and live enforcement verification, check MS10.6 complete
   and document the evidence. MS10.7–MS10.11 remain deferred.

GitHub requires adding an actual required check when selecting strict status
checks. The reference file alone cannot perform this step.
[Creating a repository ruleset](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/creating-rulesets-for-a-repository),
[viewing/managing live rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/managing-rulesets-for-a-repository).

Official GitHub documentation above verified **2026-09-26**. No live repository
configuration was inspected or changed during this implementation pass.
