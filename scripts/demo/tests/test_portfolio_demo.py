"""Offline Render/Neon demo contract; no provider API or deployment."""
from copy import deepcopy
from pathlib import Path
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[3]


def validate_blueprint(model):
    assert set(model) == {"services"}
    services = model["services"]
    assert len(services) == 2
    backend, frontend = services
    assert set(backend) == {"type", "name", "runtime", "plan", "region", "branch", "autoDeployTrigger",
                            "rootDir", "dockerfilePath", "dockerContext", "healthCheckPath", "envVars"}
    assert backend["type"] == "web" and backend["runtime"] == "docker"
    assert backend["plan"] == "free" and backend["region"] == "frankfurt"
    assert backend["rootDir"] == "backend" and backend["dockerfilePath"] == "./Dockerfile"
    assert backend["dockerContext"] == "." and backend["healthCheckPath"] == "/actuator/health"
    expected = {
        "SPRING_PROFILES_ACTIVE": {"value": "demo"}, "TASKFLOW_COOKIE_SECURE": {"value": "true"},
        "SERVER_FORWARD_HEADERS_STRATEGY": {"value": "framework"},
        "JAVA_TOOL_OPTIONS": {"value": "-XX:MaxRAMPercentage=50.0"},
        **{name: {"sync": False} for name in ("TASKFLOW_DB_URL", "TASKFLOW_DB_USERNAME",
                                            "TASKFLOW_DB_PASSWORD", "TASKFLOW_JWT_SECRET_BASE64")},
    }
    actual = {item["key"]: {k: v for k, v in item.items() if k != "key"} for item in backend["envVars"]}
    assert actual == expected and len(backend["envVars"]) == len(expected)
    assert set(frontend) == {"type", "name", "runtime", "branch", "autoDeployTrigger", "rootDir",
                             "buildCommand", "staticPublishPath", "envVars"}
    assert frontend["type"] == "web" and frontend["runtime"] == "static"
    assert frontend["rootDir"] == "frontend"
    assert frontend["buildCommand"] == "npm ci && npm run build"
    assert frontend["staticPublishPath"] == "dist/taskflow/browser"
    assert frontend["envVars"] == [{"key": "NODE_VERSION", "value": "22.23.1"}]
    for service in services:
        assert service["branch"] == "main" and service["autoDeployTrigger"] == "off"


class PortfolioDemoTest(unittest.TestCase):
    def setUp(self):
        self.model = yaml.safe_load((ROOT / "render.yaml").read_text())

    def test_free_blueprint(self):
        validate_blueprint(self.model)

    def test_paid_secret_auto_deploy_and_runtime_drift_rejected(self):
        for index, field, value in ((0, "plan", "starter"), (0, "region", "oregon"),
                                    (0, "dockerfilePath", "other"), (0, "healthCheckPath", "/actuator/env"),
                                    (1, "plan", "free"), (1, "routes", []),
                                    (1, "branch", "feature/test"), (1, "autoDeployTrigger", "commit")):
            model = deepcopy(self.model)
            model["services"][index][field] = value
            with self.subTest(field=field), self.assertRaises(AssertionError):
                validate_blueprint(model)
        model = deepcopy(self.model)
        model["services"][0]["envVars"][-1] = {"key": "TASKFLOW_JWT_SECRET_BASE64", "value": "NOT-A-SECRET"}
        with self.assertRaises(AssertionError):
            validate_blueprint(model)

    def test_demo_does_not_redefine_production_contract(self):
        demo = yaml.safe_load((ROOT / "backend/src/main/resources/application-demo.yml").read_text())
        self.assertNotIn("database", demo["taskflow"])
        self.assertTrue(demo["spring"]["flyway"]["enabled"])
        self.assertEqual(demo["spring"]["jpa"]["hibernate"]["ddl-auto"], "validate")
        self.assertTrue(demo["taskflow"]["security"]["cookies"]["secure"])
        self.assertEqual(demo["server"]["forward-headers-strategy"], "framework")
        base = yaml.safe_load((ROOT / "backend/src/main/resources/application.yml").read_text())
        self.assertEqual(base["server"]["port"], "${PORT:8080}")
        self.assertEqual(base["management"]["endpoints"]["web"]["exposure"]["include"], "health")

    def test_manual_routes_are_explicit_and_ordered(self):
        # The public backend origin does not exist in the repository. Both routes
        # belong to the post-create Dashboard operation, not invented interpolation.
        self.assertNotIn("routes", self.model["services"][1])
        guide = (ROOT / "docs/PORTFOLIO_DEMO.md").read_text()
        api = "| First | `/api/*` | `<backend HTTPS origin>/api/*` |"
        spa = "| Last | `/*` | `/index.html` |"
        self.assertIn(api, guide)
        self.assertIn(spa, guide)
        self.assertLess(guide.index(api), guide.index(spa))
        self.assertIn("Dashboard **Rewrite** rules", guide)
        self.assertIn("If external rewriting fails authenticated mutations or cookie forwarding", guide)


if __name__ == "__main__":
    unittest.main(verbosity=2)
