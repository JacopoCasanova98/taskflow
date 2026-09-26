"""CI structure and image-validator contracts; PyYAML required, Docker calls mocked."""

from pathlib import Path
from contextlib import redirect_stdout
from copy import deepcopy
import io
import json
import re
import unittest
from unittest.mock import patch

import yaml


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/ci.yml"


class CiWorkflowContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text()
        cls.workflow = yaml.safe_load(cls.text)

    def test_events_and_authority(self):
        workflow = self.workflow
        self.assertEqual(workflow["on"], {
            "pull_request": {"branches": ["main"]},
            "push": {"branches": ["main", "feature/**"]},
            "workflow_dispatch": None,
        })
        self.assertEqual(workflow["permissions"], {"contents": "read"})
        self.assertEqual(workflow["concurrency"], {
            "group": "${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}",
            "cancel-in-progress": True,
        })
        self.assertNotRegex(self.text, r"(?i)secrets\.|vars\.|id-token|pull_request_target")

    def test_independent_quality_jobs(self):
        jobs = self.workflow["jobs"]
        self.assertEqual(set(jobs), {"backend-quality", "frontend-quality", "docker-build"})
        expected = {
            "backend-quality": ("backend", ["docker info", "./mvnw -B -ntp verify"],
                "actions/setup-java", {"distribution": "temurin", "java-version": "21",
                    "cache": "maven", "cache-dependency-path": "backend/pom.xml"}),
            "frontend-quality": ("frontend", ["npm ci", "npm run quality"],
                "actions/setup-node", {"node-version": "22.23.1", "cache": "npm",
                    "cache-dependency-path": "frontend/package-lock.json"}),
        }
        for name in expected:
            job = jobs[name]
            with self.subTest(job=name):
                directory, commands, setup, inputs = expected[name]
                self.assertEqual(set(job), {"runs-on", "timeout-minutes", "defaults", "steps"})
                self.assertEqual(job["runs-on"], "ubuntu-24.04")
                self.assertTrue(15 <= job["timeout-minutes"] <= 30)
                self.assertEqual(job["defaults"], {"run": {"working-directory": directory}})
                steps = job["steps"]
                self.assertEqual(len(steps), 4)
                self.assertEqual([s["run"] for s in steps if "run" in s], commands)
                for step, action in zip(steps[:2], ["actions/checkout", setup]):
                    self.assertRegex(step["uses"], "^" + re.escape(action) + r"@[0-9a-f]{40}$")
                    self.assertEqual(set(step), {"uses", "with"})
                self.assertEqual(steps[0]["with"], {"persist-credentials": False})
                self.assertEqual(steps[1]["with"], inputs)
                for step in steps[2:]:
                    self.assertEqual(set(step), {"name", "run"})
        # Exact command/step contracts preserve the application quality gates.
        self.assertEqual(set(self.workflow), {"name", "on", "permissions", "concurrency", "jobs"})

    def test_docker_build_contract(self):
        job = self.workflow["jobs"]["docker-build"]
        self.assertEqual(set(job), {"name", "needs", "runs-on", "timeout-minutes", "strategy", "steps"})
        self.assertEqual(job["name"], "docker-build (${{ matrix.component }})")
        self.assertEqual(set(job["needs"]), {"backend-quality", "frontend-quality"})
        self.assertEqual(job["runs-on"], "ubuntu-24.04")
        self.assertTrue(15 <= job["timeout-minutes"] <= 30)
        self.assertEqual(job["strategy"], {
            "fail-fast": False,
            "matrix": {"include": [
                {"component": "backend", "context": "./backend"},
                {"component": "frontend", "context": "./frontend"},
            ]},
        })
        steps = job["steps"]
        self.assertEqual(len(steps), 4)
        actions = ["actions/checkout", "docker/setup-buildx-action", "docker/build-push-action"]
        for step, action in zip(steps[:3], actions):
            self.assertRegex(step["uses"], "^" + re.escape(action) + r"@[0-9a-f]{40}$")
        self.assertEqual(set(steps[0]), {"uses", "with"})
        self.assertEqual(steps[0]["with"], {"persist-credentials": False})
        self.assertEqual(set(steps[1]), {"name", "uses"})
        self.assertEqual(set(steps[2]), {"name", "uses", "env", "with"})
        self.assertEqual(steps[2]["env"], {"DOCKER_BUILD_RECORD_UPLOAD": "false"})
        self.assertEqual(steps[2]["with"], {
            "context": "${{ matrix.context }}",
            "file": "${{ matrix.context }}/Dockerfile",
            "platforms": "linux/amd64", "push": False, "load": True, "provenance": False,
            "tags": "taskflow-${{ matrix.component }}:git-${{ github.sha }}",
            "labels": "org.opencontainers.image.source=${{ github.server_url }}/${{ github.repository }}\n"
                      "org.opencontainers.image.revision=${{ github.sha }}\n",
            "cache-from": "type=gha,scope=taskflow-${{ matrix.component }}",
            "cache-to": "type=gha,mode=max,scope=taskflow-${{ matrix.component }}",
        })
        inspection = steps[3]
        self.assertEqual(set(inspection), {"name", "env", "run"})
        self.assertEqual(inspection["env"], {
            "COMPONENT": "${{ matrix.component }}",
            "IMAGE": "taskflow-${{ matrix.component }}:git-${{ github.sha }}",
            "SOURCE_REVISION": "${{ github.sha }}",
            "SOURCE_URL": "${{ github.server_url }}/${{ github.repository }}",
        })
        for contract in ('"docker", "image", "inspect"', '"amd64"', '"linux"',
                         '"10001:10001"', '"nginx"', '"8080/tcp"', '"/app/app.jar"',
                         '"TASKFLOW_DB_PASSWORD"', '"TASKFLOW_JWT_SECRET_BASE64"',
                         '"AWS_ACCESS_KEY_ID"', '"AWS_SECRET_ACCESS_KEY"', '"AWS_SESSION_TOKEN"'):
            self.assertIn(contract, inspection["run"])
        self.assertNotRegex(self.text, r"(?i)docker/login-action|ghcr\.io|\.dkr\.ecr\.|type=registry|"
                            r"docker push|aws-actions/|terraform|cloudformation|docker compose")

    def test_image_inspection_rejects_contract_violations(self):
        # Exercise the embedded validator, not GitHub Actions execution semantics.
        run = self.workflow["jobs"]["docker-build"]["steps"][-1]["run"]
        code = compile(run.split("\n", 1)[1].rsplit("PYTHON", 1)[0], "image-inspection", "exec")
        revision = "a" * 40
        source = "https://github.com/example/taskflow"
        for component, user in (("backend", "10001:10001"), ("frontend", "nginx")):
            valid = {"Os": "linux", "Architecture": "amd64", "Config": {
                "User": user, "ExposedPorts": {"8080/tcp": {}},
                "Entrypoint": ["java", "-jar", "/app/app.jar"], "Env": [],
                "Labels": {"org.opencontainers.image.revision": revision,
                           "org.opencontainers.image.source": source},
            }}
            env = {"COMPONENT": component, "IMAGE": "taskflow-" + component,
                   "SOURCE_REVISION": revision, "SOURCE_URL": source}

            def validate(image):
                with patch.dict("os.environ", env, clear=True), redirect_stdout(io.StringIO()), \
                     patch("subprocess.check_output", return_value=json.dumps([image])) as inspect:
                    exec(code, {})
                    inspect.assert_called_once_with(
                        ["docker", "image", "inspect", env["IMAGE"]], text=True)

            validate(valid)
            mutations = [("Os", "windows"), ("Architecture", "arm64"),
                         ("User", "0"), ("ExposedPorts", {}), ("Labels", {})]
            if component == "backend":
                mutations.append(("Entrypoint", ["java", "-jar", "/wrong.jar"]))
            for field, value in mutations:
                with self.subTest(component=component, field=field):
                    invalid = deepcopy(valid)
                    target = invalid if field in ("Os", "Architecture") else invalid["Config"]
                    target[field] = value
                    with self.assertRaises(SystemExit):
                        validate(invalid)
            for secret in ("TASKFLOW_DB_PASSWORD", "TASKFLOW_JWT_SECRET_BASE64",
                           "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN"):
                with self.subTest(component=component, secret=secret):
                    invalid = deepcopy(valid)
                    invalid["Config"]["Env"] = [secret + "="]
                    with self.assertRaises(SystemExit):
                        validate(invalid)


if __name__ == "__main__":
    unittest.main(verbosity=2)
