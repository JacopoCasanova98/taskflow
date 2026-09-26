"""Offline MS10.6 policy checks; cached PyYAML, no GitHub API or policy mutation.

The bounded checker validates TaskFlow's desired policy, not GitHub's live state.
The gate's actual Bash body is exercised with synthetic dependency results.
"""
from copy import deepcopy
from itertools import product
import json
from pathlib import Path
import subprocess
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[3]
DEPENDENCIES = {"backend-quality", "frontend-quality", "docker-build"}
RESULT_ENV = {
    "BACKEND_RESULT": "${{ needs.backend-quality.result }}",
    "FRONTEND_RESULT": "${{ needs.frontend-quality.result }}",
    "DOCKER_RESULT": "${{ needs.docker-build.result }}",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_policy(workflow, reference):
    """Focused repository policy assertions; not a workflow/ruleset interpreter."""
    require(workflow["on"] == {
        "pull_request": {"branches": ["main"]},
        "push": {"branches": ["main", "feature/**"]},
        "workflow_dispatch": None,
    }, "unexpected CI triggers")
    require(workflow["permissions"] == {"contents": "read"}, "unexpected workflow permissions")
    jobs = workflow["jobs"]
    require(set(jobs) == DEPENDENCIES | {"ci-gate"}, "unexpected CI jobs")
    for job in jobs.values():
        require("permissions" not in job and "environment" not in job,
                "job cannot expand authority")
        require(not job.get("continue-on-error", False), "upstream failures cannot be ignored")
        for step in job["steps"]:
            require(not step.get("continue-on-error", False), "step failures cannot be ignored")
            action = step.get("uses", "").lower()
            require(not action.startswith(("aws-actions/", "docker/login-action")), "privileged action forbidden")
    text = json.dumps(workflow).lower()
    require(not any(token in text for token in ("id-token", "packages", "secrets.", "role-to-assume")),
            "ordinary CI must remain credential-free")
    gate = jobs["ci-gate"]
    require(set(gate) == {"name", "needs", "if", "runs-on", "timeout-minutes", "steps"}, "unexpected gate fields")
    require(gate["name"] == "ci-gate", "unstable check name")
    require(isinstance(gate["needs"], list) and len(gate["needs"]) == 3
            and set(gate["needs"]) == DEPENDENCIES, "missing gate dependency")
    require(gate["if"] == "${{ always() }}", "gate must run after failed dependencies")
    require(gate["runs-on"] == "ubuntu-24.04" and gate["timeout-minutes"] == 2, "unexpected gate runner/budget")
    require(len(gate["steps"]) == 1, "gate must only aggregate")
    step = gate["steps"][0]
    require(set(step) == {"name", "shell", "env", "run"}, "unexpected gate step")
    require(step["shell"] == "bash" and step["env"] == RESULT_ENV, "results must use safe environment mapping")
    require("${{" not in step["run"], "no expression interpolation in shell")

    expected = {
        "schema_version": "1.0",
        "status": "REFERENCE TARGET — NOT PROOF OF LIVE GITHUB CONFIGURATION",
        "repository": "JacopoCasanova98/taskflow", "target": "refs/heads/main",
        "desired_enforcement": "active", "bypass_actors": [],
        "pull_request": {"required": True, "required_approving_review_count": 0,
                         "require_code_owner_review": False, "require_last_push_approval": False,
                         "require_conversation_resolution": True},
        "status_checks": {"required": ["ci-gate"], "strict_up_to_date": True,
                          "expected_source": "GitHub Actions; verify live app identity before configuration"},
        "block_force_pushes": True, "block_deletion": True,
        "require_linear_history": False, "require_signed_commits": False,
        "require_merge_queue": False, "require_deployments": False, "preserve_merge_commits": True,
    }
    # Canonical JSON comparison keeps booleans distinct from integers; field order is irrelevant.
    require(json.dumps(reference, sort_keys=True) == json.dumps(expected, sort_keys=True),
            "reference policy differs from reviewed solo-main target")


def gate_exit(script, results):
    # Only the repository-owned aggregate shell is executed, in the offline test container.
    # Inputs are synthetic result strings, never a candidate GitHub workflow or network command.
    return subprocess.run(["/bin/bash", "--noprofile", "--norc", "-e", "-o", "pipefail", "-c", script],
                          env=dict(zip(RESULT_ENV, results)), capture_output=True, text=True,
                          timeout=3).returncode


def validate_gate_results(script):
    for results in product(("success", "failure", "cancelled", "skipped", ""), repeat=3):
        expected_success = all(result == "success" for result in results)
        require((gate_exit(script, results) == 0) == expected_success,
                "gate must fail unless every dependency succeeded")


class RepositoryPolicyTest(unittest.TestCase):
    def setUp(self):
        self.workflow = yaml.safe_load((ROOT / ".github/workflows/ci.yml").read_text())
        self.reference = json.loads((ROOT / "ops/github/main-ruleset.reference.json").read_text())

    def test_repository_target(self):
        validate_policy(self.workflow, self.reference)

    def test_gate_actual_shell_failure_semantics(self):
        validate_gate_results(self.workflow["jobs"]["ci-gate"]["steps"][0]["run"])

    def test_gate_rejects_missing_dependency_and_unsafe_condition(self):
        for dependency in DEPENDENCIES:
            candidate = deepcopy(self.workflow)
            candidate["jobs"]["ci-gate"]["needs"].remove(dependency)
            with self.assertRaises(ValueError):
                validate_policy(candidate, self.reference)
        for condition in ("${{ success() }}", "${{ !cancelled() }}", None):
            candidate = deepcopy(self.workflow)
            candidate["jobs"]["ci-gate"]["if"] = condition
            with self.assertRaises(ValueError):
                validate_policy(candidate, self.reference)

    def test_bad_gate_scripts_fail_behavior_check(self):
        script = self.workflow["jobs"]["ci-gate"]["steps"][0]["run"]
        for bad in ("exit 0", script.replace("exit 1", "exit 0"),
                    script.replace(' "$DOCKER_RESULT"', ''),
                    script.replace('"$result" != "success"', '"$result" = "failure"')):
            with self.assertRaises(ValueError):
                validate_gate_results(bad)

    def test_reference_rejects_weakened_or_blocking_policy(self):
        mutations = [
            ("target", "refs/heads/feature/test"), ("desired_enforcement", "disabled"),
            ("bypass_actors", ["administrator"]), ("block_force_pushes", False), ("block_deletion", False),
            ("require_linear_history", True), ("require_signed_commits", True),
            ("require_merge_queue", True), ("require_deployments", True),
        ]
        for key, value in mutations:
            candidate = deepcopy(self.reference)
            candidate[key] = value
            with self.subTest(field=key), self.assertRaises(ValueError):
                validate_policy(self.workflow, candidate)
        for group, field, value in (
            ("pull_request", "required", False), ("pull_request", "required_approving_review_count", 1),
            ("pull_request", "require_last_push_approval", True),
            ("status_checks", "required", ["docker-build"]), ("status_checks", "strict_up_to_date", False),
        ):
            candidate = deepcopy(self.reference)
            candidate[group][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_policy(self.workflow, candidate)

    def test_forbidden_trigger_permissions_and_action(self):
        candidates = []
        candidate = deepcopy(self.workflow)
        candidate["on"]["pull_request_target"] = None
        candidates.append(candidate)
        for permission in ("id-token", "packages"):
            candidate = deepcopy(self.workflow)
            candidate["permissions"][permission] = "write"
            candidates.append(candidate)
        candidate = deepcopy(self.workflow)
        candidate["jobs"]["backend-quality"]["steps"].append({"uses": "aws-actions/forbidden@" + "a" * 40})
        candidates.append(candidate)
        candidate = deepcopy(self.workflow)
        candidate["jobs"]["docker-build"]["continue-on-error"] = True
        candidates.append(candidate)
        candidate = deepcopy(self.workflow)
        candidate["jobs"]["ci-gate"]["name"] = "ci-gate-${{ github.ref }}"
        candidates.append(candidate)
        for candidate in candidates:
            with self.assertRaises(ValueError):
                validate_policy(candidate, self.reference)

    def test_check_name_is_unique_and_template_present(self):
        count = 0
        for path in (ROOT / ".github/workflows").glob("*.y*ml"):
            workflow = yaml.safe_load(path.read_text())
            count += sum(job.get("name", key) == "ci-gate" for key, job in workflow["jobs"].items())
        self.assertEqual(count, 1)
        template = (ROOT / ".github/pull_request_template.md").read_text()
        for heading in ("Summary", "Scope", "Validation", "Security / operations impact", "Checklist"):
            self.assertIn("## " + heading, template)


if __name__ == "__main__":
    unittest.main(verbosity=2)
