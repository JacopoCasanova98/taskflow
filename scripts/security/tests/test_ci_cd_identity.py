"""Offline policy mutations and cross-contract checks; no cloud credentials."""
import ast
from contextlib import redirect_stderr, redirect_stdout
from copy import deepcopy
import io
import json
from pathlib import Path
import re
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/security"))
from validate_ci_cd_identity import DEFAULT_CONTRACT, ValidationError, load_contract, main, validate


def read(path):
    return (ROOT / path).read_text()


class IdentityTest(unittest.TestCase):
    def setUp(self):
        self.contract = load_contract(DEFAULT_CONTRACT)

    def reject(self, path, value):
        candidate = deepcopy(self.contract)
        target = candidate
        for key in path[:-1]:
            target = target[key]
        target[path[-1]] = value
        with self.assertRaises(ValidationError):
            validate(candidate)

    def test_valid_contract_and_order_independence(self):
        validate(self.contract)
        self.contract["identities"]["registry_publisher"]["allowed"].reverse()
        validate(self.contract)

    def test_trust_identity(self):
        for key, values in {
            "schema_version": ["2.0", 1], "issuer": ["https://attacker.invalid"],
            "audience": ["other-service"], "repository": ["other/taskflow", "JacopoCasanova98/*", "*/*"],
            "authorized_subjects": [["repo:JacopoCasanova98/*"], ["repo:*/*"], [],
                                    ["repo:JacopoCasanova98/taskflow:pull_request"]],
            "trust_operator": ["StringLike"], "credential_model": ["long-lived-access-keys"],
        }.items():
            for value in values:
                with self.subTest(field=key, value=value):
                    self.reject([key], value)

    def test_publisher_privilege_escalation(self):
        role = self.contract["identities"]["registry_publisher"]
        for capability in role["forbidden"] + ["unknown_power"]:
            with self.subTest(capability=capability):
                self.reject(["identities", "registry_publisher", "allowed"], role["allowed"] + [capability])
        self.reject(["identities", "registry_publisher", "forbidden"], [])

    def test_runtime_push_and_identity_collapse(self):
        self.reject(["identities", "runtime_host", "allowed"],
                    self.contract["identities"]["runtime_host"]["allowed"] + ["ecr_push_taskflow_repositories"])
        for role in ("runtime_host", "deployment_operator", "infrastructure_provisioner"):
            self.reject(["identities", role, "role"], "<REGISTRY_PUBLISHER_ROLE_NAME>")

    def test_deployment_remains_blocked(self):
        for capability in ("ssm_remote_shell", "ssm_deployment_interface", "ssm:SendCommand"):
            self.reject(["identities", "deployment_operator", "allowed"], [capability])
        self.reject(["identities", "deployment_operator", "state"], "enabled")

    def test_ordinary_ci_cannot_federate(self):
        self.reject(["ordinary_ci", "aws_federation"], True)
        self.reject(["ordinary_ci", "permissions"], {"contents": "read", "id-token": "write"})
        self.reject(["ordinary_ci", "aws_federation"], 0)  # Boolean type is strict.

    def test_future_workflow_protection(self):
        for trigger in ("pull_request", "pull_request_target", "push"):
            self.reject(["future_publisher_workflow", "trigger"], trigger)
        self.reject(["future_publisher_workflow", "ref"], "refs/heads/feature/test")
        self.reject(["future_publisher_workflow", "enabled"], True)
        self.reject(["future_publisher_workflow", "required_controls"], [])
        self.reject(["future_publisher_workflow", "permissions"], {"contents": "write", "id-token": "write"})

    def test_repository_and_action_scope(self):
        self.reject(["publisher_authorization", "repository_resources"], ["*"])
        self.reject(["publisher_authorization", "repository_actions"], ["ecr:*"])
        actions = self.contract["publisher_authorization"]["repository_actions"]
        self.reject(["publisher_authorization", "repository_actions"], actions + ["ecr:DeleteRepository"])
        self.reject(["publisher_authorization", "repository_actions"], actions + [actions[0]])

    def test_secrets_and_session(self):
        self.reject(["secrets", "github_payloads"], ["application_password"])
        self.reject(["secrets", "migrator"], "github-secret")
        self.reject(["session", "credential_persistence"], True)
        self.reject(["session", "requested_seconds"], 43200)
        self.reject(["secrets", "application_flow"], list(reversed(self.contract["secrets"]["application_flow"])))

    def test_closed_structure(self):
        for value in (None, [], "value", {"extra": "value"}):
            with self.assertRaises(ValidationError):
                validate(value)
        self.reject(["password"], "synthetic-do-not-echo")
        for key in self.contract:
            candidate = deepcopy(self.contract)
            del candidate[key]
            with self.assertRaises(ValidationError):
                validate(candidate)

    def test_cli_safe_diagnostics_and_duplicate_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "candidate.json"
            for value in ('{"password":"synthetic-do-not-echo"}', '{"schema_version":"1.0","schema_version":"1.0"}', '{'):
                path.write_text(value)
                out, err = io.StringIO(), io.StringIO()
                with redirect_stdout(out), redirect_stderr(err):
                    self.assertEqual(main([str(path)]), 1)
                self.assertEqual(out.getvalue(), "")
                self.assertNotIn("synthetic-do-not-echo", err.getvalue())
            path.write_text(json.dumps(self.contract))
            with redirect_stdout(io.StringIO()):
                self.assertEqual(main([str(path)]), 0)

    def test_validator_has_only_data_handling_imports(self):
        tree = ast.parse(read("scripts/security/validate_ci_cd_identity.py"))
        imports = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imports.update(alias.name for alias in node.names)
            elif isinstance(node, ast.ImportFrom):
                imports.add(node.module)
            elif isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
                self.assertNotIn(node.func.id, {"exec", "eval", "__import__", "compile"})
        self.assertEqual(imports, {"argparse", "json", "pathlib", "sys"})

    def test_existing_runtime_iam_scope(self):
        tf = read("infra/terraform/iam.tf")
        cf = read("infra/cloudformation/taskflow.yaml").split("  AppRole:", 1)[1].split("  AppInstanceProfile:", 1)[0]
        expected = {"sts:AssumeRole", "ecr:GetAuthorizationToken", "ecr:BatchCheckLayerAvailability",
                    "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "secretsmanager:GetSecretValue",
                    "secretsmanager:DescribeSecret", "logs:CreateLogStream", "logs:PutLogEvents",
                    "cloudwatch:PutMetricData"}
        for source in (tf, cf):
            actions = set(re.findall(r'\b(?:sts|ecr|secretsmanager|logs|cloudwatch|ssm|iam|ec2|rds):[A-Z][A-Za-z*]+', source))
            self.assertEqual(actions, expected)
            self.assertIn("AmazonSSMManagedInstanceCore", source)
            self.assertIn("ec2.amazonaws.com", source)
        self.assertIn("aws_secretsmanager_secret.app_database.arn", tf)
        self.assertIn("aws_secretsmanager_secret.jwt_signing.arn", tf)
        self.assertIn("for repository in aws_ecr_repository.app : repository.arn", tf)
        for name in ("ApplicationDatabaseSecret", "JwtSigningSecret", "FrontendRepository.Arn", "BackendRepository.Arn"):
            self.assertIn(name, cf)
        self.assertNotRegex(tf + cf, r'MasterUserSecret|migrator|secretsmanager:\*|ecr:\*')

    def test_current_ci_and_document_interfaces(self):
        ci = read(".github/workflows/ci.yml")
        self.assertRegex(ci, r'(?m)^permissions:\n  contents: read\n')
        self.assertNotRegex(ci, r'id-token:|packages:|aws-actions/|docker/login-action|secrets\.|role-to-assume|environment:')
        self.assertIn("push: false", ci)
        for path in ("docs/REGISTRY_DELIVERY.md", "docs/DEPLOYMENT_AUTOMATION.md"):
            self.assertIn("CI_CD_SECURITY.md", read(path))


if __name__ == "__main__":
    unittest.main()
