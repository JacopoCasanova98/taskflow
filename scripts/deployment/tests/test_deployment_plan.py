"""Offline artificial fixtures only; no actual host, account, release or deployment."""

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
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/deployment"))
sys.path.insert(0, str(ROOT / "scripts/release/tests"))
import render_deployment_plan as planner
from test_release_pair import synthetic_pair


def synthetic_intent():
    # Never resolve this artificial RDS-shaped hostname; example.invalid is reserved.
    return {
        "schema_version": "1.0", "environment": "prod", "region": "eu-west-1",
        "compose_project": "taskflow", "compose_file": "compose.production.yaml",
        "database_url": "jdbc:postgresql://synthetic.invalid.eu-west-1.rds.amazonaws.com:5432/taskflow"
                        "?sslmode=verify-full&sslrootcert=/opt/taskflow/trust/rds-ca-bundle.pem",
        "database_app_username": "taskflow_app",
        "database_secret_id": "taskflow-prod/database/application",
        "jwt_secret_id": "taskflow-prod/jwt/signing",
        "rds_ca_path": "/opt/taskflow/trust/rds-ca-bundle.pem",
        "cloudwatch_agent_config_path": "/opt/taskflow/config/amazon-cloudwatch-agent.json",
        "public_hostname": "taskflow.example.invalid", "release_pair": synthetic_pair(),
    }


class DeploymentPlanTest(unittest.TestCase):
    def test_deterministic_dry_run_and_digest_references(self):
        intent = synthetic_intent()
        original = deepcopy(intent)
        result = planner.render_plan(intent)
        self.assertEqual(result, planner.render_plan(deepcopy(intent)))
        self.assertEqual(intent, original)
        self.assertEqual(result["plan_schema_version"], "1.0")
        self.assertEqual(result["execution_mode"], "dry-run")
        self.assertIs(result["live_operations_performed"], False)
        self.assertIs(result["deployment_evidence"], False)
        self.assertEqual(result["platform"], "linux/amd64")
        self.assertEqual([g["id"] for g in result["gates"]], ["D1", "D2", "D3", "D4", "D5", "D6", "D7"])
        for component in ("frontend", "backend"):
            pair = intent["release_pair"]
            expected = pair[component + "_repository"] + "@" + pair[component + "_digest"]
            self.assertEqual(result[component + "_image"], expected)
            self.assertEqual(result["runtime_inputs"]["TASKFLOW_" + component.upper() + "_IMAGE"], expected)
        for gate in result["gates"]:
            self.assertTrue(gate["live_execution_required"])
            self.assertEqual(gate["status"], "not_executed")
        self.assertNotIn(str(ROOT), json.dumps(result))

    def test_release_validator_reused_and_errors_propagate(self):
        intent = synthetic_intent()
        with patch.object(planner, "validate_release_pair", wraps=planner.validate_release_pair) as validate:
            planner.render_plan(intent)
            validate.assert_called_once_with(intent["release_pair"])
        for field, value in (("git_commit", "short"), ("platform", "linux/arm64"),
                             ("frontend_digest", "latest"), ("digest_source", "docker-image-id")):
            with self.subTest(field=field):
                candidate = synthetic_intent()
                candidate["release_pair"][field] = value
                with self.assertRaises(planner.ValidationError):
                    planner.render_plan(candidate)
        for field in ("frontend_repository", "backend_digest"):
            candidate = synthetic_intent()
            del candidate["release_pair"][field]
            with self.assertRaises(planner.ValidationError):
                planner.render_plan(candidate)

    def test_fixed_architecture_and_paths_fail_closed(self):
        cases = {"schema_version": "2.0", "environment": "dev", "region": "eu-west-2",
                 "compose_project": "another", "compose_file": "compose.yaml",
                 "database_app_username": "taskflowadmin", "rds_ca_path": "/tmp/ca.pem",
                 "cloudwatch_agent_config_path": "/tmp/agent.json", "public_hostname": "https://example.invalid"}
        for field, value in cases.items():
            with self.subTest(field=field):
                intent = synthetic_intent()
                intent[field] = value
                with self.assertRaises(planner.ValidationError):
                    planner.render_plan(intent)
        intent = synthetic_intent()
        for component in ("frontend", "backend"):
            key = component + "_repository"
            intent["release_pair"][key] = intent["release_pair"][key].replace("eu-west-1", "eu-west-2")
        with self.assertRaises(planner.ValidationError):
            planner.render_plan(intent)

    def test_missing_fields_and_invalid_types(self):
        for field in synthetic_intent():
            with self.subTest(field=field):
                candidate = synthetic_intent()
                del candidate[field]
                with self.assertRaises(planner.ValidationError):
                    planner.render_plan(candidate)
                candidate = synthetic_intent()
                candidate[field] = None
                with self.assertRaises(planner.ValidationError):
                    planner.render_plan(candidate)
        for intent in ([], None, "intent"):
            with self.assertRaises(planner.ValidationError):
                planner.render_plan(intent)

    def test_secret_fields_and_runtime_overrides_rejected(self):
        fields = ("password", "database_password", "jwt_secret", "jwt_signing_secret",
                  "aws_access_key_id", "aws_secret_access_key", "aws_session_token",
                  "registry_password", "registry_token", "migration_password", "master_password",
                  "AWS_ACCESS_KEY_ID", "TASKFLOW_JWT_SECRET_BASE64", "frontend_image", "backend_image",
                  "gates", "skip_migration", "schema_compatible")
        for field in fields:
            with self.subTest(field=field):
                intent = synthetic_intent()
                intent[field] = "synthetic-sensitive-marker"
                with self.assertRaises(planner.ValidationError):
                    planner.render_plan(intent)
        for field in ("database_secret_id", "jwt_secret_id", "public_hostname"):
            for value in ('{"password":"synthetic"}', "synthetic-payload==", "AWS_ACCESS_KEY_ID=synthetic"):
                intent = synthetic_intent()
                intent[field] = value
                with self.assertRaises(planner.ValidationError):
                    planner.render_plan(intent)

    def test_database_tls_userinfo_and_query_constraints(self):
        url = synthetic_intent()["database_url"]
        invalid = [url.replace("verify-full", "require"), url.replace("5432", "5433"),
                   url.replace("/taskflow?", "/other?"), url.replace("eu-west-1", "eu-west-2"),
                   url.replace("//synthetic", "//user:synthetic-password@synthetic"),
                   url + "&password=synthetic", url + "&user=taskflow_migrator",
                   url + "&sslmode=verify-full", url + "&sslfactory=override", url + "#",
                   url.replace("/opt/taskflow/trust", "/tmp"), url + "\n", url + "&",
                   url.replace("synthetic.invalid.eu-west-1.rds.amazonaws.com", "127.0.0.1"),
                   url.replace("sslrootcert", "%73slmode")]
        for value in invalid:
            with self.subTest(url=value):
                intent = synthetic_intent()
                intent["database_url"] = value
                with self.assertRaises(planner.ValidationError):
                    planner.render_plan(intent)
        # Query ordering/encoding accepted by the existing runtime contract.
        intent = synthetic_intent()
        intent["database_url"] = url.split("?", 1)[0] + "?sslrootcert=%2Fopt%2Ftaskflow%2Ftrust%2Frds-ca-bundle.pem&sslmode=verify-full"
        planner.render_plan(intent)

    def test_gate_mutation_is_rejected(self):
        gates = planner.render_plan(synthetic_intent())["gates"]
        variants = [gates[:4] + gates[5:], gates + [gates[-1]], list(reversed(gates))]
        broken_dependency = deepcopy(gates)
        broken_dependency[5]["requires"] = []
        variants.append(broken_dependency)
        for variant in variants:
            with self.subTest(ids=[g["id"] for g in variant]):
                with patch.object(planner, "build_gates", return_value=variant):
                    with self.assertRaises(planner.ValidationError):
                        planner.render_plan(synthetic_intent())

    def test_materialization_migration_and_acceptance_boundaries(self):
        plan = planner.render_plan(synthetic_intent())
        gates = {g["id"]: g for g in plan["gates"]}
        d3 = gates["D3"]["contract"]
        self.assertEqual(d3["secret_directory"], "/run/taskflow/secrets")
        self.assertEqual(d3["rotation_requires"], "recreate_backend_not_restart")
        self.assertEqual(d3["secret_identifiers_in_order"],
                         ["taskflow-prod/database/application", "taskflow-prod/jwt/signing"])
        d5 = gates["D5"]["contract"]
        self.assertTrue(d5["mandatory"])
        self.assertEqual(d5["backend_image"], plan["backend_image"])
        self.assertEqual(d5["database_role"], "taskflow_migrator")
        self.assertEqual(d5["user"], "10001:10001")
        self.assertTrue(d5["read_only_root"])
        self.assertFalse(d5["app_jwt_master_secrets_allowed"])
        self.assertEqual(d5["failure"], "stop_before_D6_no_blind_retry")
        self.assertIn("successful_migration_and_compatible_schema", gates["D6"]["requires"])
        self.assertFalse(gates["D6"]["contract"]["runtime_flyway_enabled"])
        self.assertEqual(gates["D6"]["contract"]["hibernate"], "validate")
        self.assertEqual(gates["D7"]["contract"]["mixed_pair"], "fail_acceptance")
        self.assertIn("both_expected_running_digests", gates["D7"]["contract"]["checks"])
        self.assertEqual(gates["D1"]["contract"]["unknown_compatibility"], "stop")

    def test_cli_determinism_and_no_secret_echo(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "synthetic-intent.json"
            path.write_text(json.dumps(synthetic_intent()))
            outputs = []
            for _ in range(2):
                out, err = io.StringIO(), io.StringIO()
                with redirect_stdout(out), redirect_stderr(err):
                    self.assertEqual(planner.main([str(path)]), 0)
                outputs.append(out.getvalue())
                self.assertEqual(err.getvalue(), "")
            self.assertEqual(outputs[0], outputs[1])
            self.assertEqual(json.loads(outputs[0])["execution_mode"], "dry-run")
            candidate = synthetic_intent()
            candidate["password"] = "DO-NOT-ECHO-SYNTHETIC"
            for raw in (json.dumps(candidate), '{"region":"x","region":"y"}', '{invalid'):
                path.write_text(raw)
                out, err = io.StringIO(), io.StringIO()
                with redirect_stdout(out), redirect_stderr(err):
                    self.assertEqual(planner.main([str(path)]), 1)
                self.assertEqual(out.getvalue(), "")
                self.assertNotIn("DO-NOT-ECHO", err.getvalue())


class CrossContractTest(unittest.TestCase):
    def test_runbook_order_and_titles(self):
        runbook = (ROOT / "docs/DEPLOYMENT_RUNBOOK.md").read_text()
        rows = re.findall(r'^\| (D[1-7]) \| (.*?) \|', runbook, re.M)
        plan = planner.render_plan(synthetic_intent())
        self.assertEqual([(g["id"], g["purpose"]) for g in plan["gates"]], rows)

    def test_production_contracts(self):
        compose = (ROOT / "compose.production.yaml").read_text()
        for component in ("FRONTEND", "BACKEND"):
            self.assertIn('image: ${TASKFLOW_' + component + '_IMAGE:?', compose)
        for term in ('SPRING_FLYWAY_ENABLED: "false"', '/run/taskflow/secrets', planner.CA_PATH):
            self.assertIn(term, compose)
        contracts = {
            "PRODUCTION_RUNTIME.md": ["taskflow_app", "Hibernate", "validate", "<repository>@sha256:<digest>"],
            "RDS_DATABASE_OPERATIONS.md": ["taskflow_migrator", "taskflow_app", "verify-full", planner.CA_PATH],
            "EC2_BOOTSTRAP_SECRETS.md": ["/run/taskflow/secrets", "recreate backend", "old inodes"],
            "OBSERVABILITY.md": ["/opt/taskflow/config/amazon-cloudwatch-agent.json", "eight alarms", "external topic"],
            "REGISTRY_DELIVERY.md": ["registry manifest digest", "separate future eligibility record"],
        }
        for name, terms in contracts.items():
            text = (ROOT / "docs" / name).read_text()
            for term in terms:
                with self.subTest(file=name, term=term):
                    self.assertIn(term, text)

    def test_schema_constants_and_reference(self):
        schema = json.loads(planner.SCHEMA_PATH.read_text())
        intent = synthetic_intent()
        self.assertFalse(schema["additionalProperties"])
        self.assertEqual(set(schema["required"]), set(intent))
        self.assertEqual(set(schema["properties"]), set(intent))
        self.assertEqual(schema["properties"]["release_pair"]["$ref"], "../release/release-pair.schema.json")
        for field, rule in schema["properties"].items():
            if "const" in rule:
                self.assertEqual(intent[field], rule["const"])
        secrets = (ROOT / "infra/terraform/secrets.tf").read_text()
        self.assertIn('${local.name_prefix}/database/application', secrets)
        self.assertIn('${local.name_prefix}/jwt/signing', secrets)

    def test_no_process_or_network_capability_in_planner_or_reused_validator(self):
        allowed = {"argparse", "json", "pathlib", "re", "sys", "urllib.parse", "datetime", "validate_release_pair"}
        forbidden_calls = {"exec", "eval", "compile", "__import__", "system", "popen", "spawn", "connect",
                           "urlopen", "request", "run", "Popen", "write_text", "write_bytes"}
        for file in ("scripts/deployment/render_deployment_plan.py", "scripts/release/validate_release_pair.py"):
            tree = ast.parse((ROOT / file).read_text())
            for node in ast.walk(tree):
                if isinstance(node, ast.Import):
                    self.assertTrue(all(alias.name in allowed for alias in node.names))
                elif isinstance(node, ast.ImportFrom):
                    self.assertIn(node.module, allowed)
                elif isinstance(node, ast.Call):
                    name = node.func.id if isinstance(node.func, ast.Name) else getattr(node.func, "attr", "")
                    self.assertNotIn(name, forbidden_calls)


if __name__ == "__main__":
    unittest.main(verbosity=2)
