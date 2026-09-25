"""Static CI contract; requires PyYAML, never executes workflow commands."""

from pathlib import Path
import re
import unittest

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
        self.assertNotRegex(self.text, r"(?i)secrets\.|vars\.|AWS_|id-token|pull_request_target")

    def test_independent_quality_jobs(self):
        jobs = self.workflow["jobs"]
        self.assertEqual(set(jobs), {"backend-quality", "frontend-quality"})
        expected = {
            "backend-quality": ("backend", ["docker info", "./mvnw -B -ntp verify"],
                "actions/setup-java", {"distribution": "temurin", "java-version": "21",
                    "cache": "maven", "cache-dependency-path": "backend/pom.xml"}),
            "frontend-quality": ("frontend", ["npm ci", "npm run quality"],
                "actions/setup-node", {"node-version": "22.23.1", "cache": "npm",
                    "cache-dependency-path": "frontend/package-lock.json"}),
        }
        for name, job in jobs.items():
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
        # Exact command/step contracts exclude deployment, image builds and publishing.
        self.assertEqual(set(self.workflow), {"name", "on", "permissions", "concurrency", "jobs"})


if __name__ == "__main__":
    unittest.main(verbosity=2)
