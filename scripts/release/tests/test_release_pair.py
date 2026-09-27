"""Artificial fixtures only, never actual release evidence. Standard library only."""

from contextlib import redirect_stderr, redirect_stdout
import io
import json
from pathlib import Path
import re
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts/release"))
from validate_release_pair import ValidationError, load_record, main, validate


def synthetic_pair():
    # Deliberately artificial account, digests and timestamp: offline test data.
    registry = "000000000000.dkr.ecr.eu-west-1.amazonaws.com/synthetic-test-"
    return {
        "schema_version": "1.0", "git_commit": "a" * 40,
        "frontend_repository": registry + "frontend", "frontend_digest": "sha256:" + "b" * 64,
        "backend_repository": registry + "backend", "backend_digest": "sha256:" + "c" * 64,
        "digest_source": "ecr-registry-manifest", "build_timestamp": "2000-02-29T12:34:56Z",
        "platform": "linux/amd64",
    }


class ReleasePairTest(unittest.TestCase):
    def test_valid_pair_and_derived_identity(self):
        record = synthetic_pair()
        result = validate(record)
        self.assertEqual(result["git_tag"], "git-" + "a" * 40)
        for component in ("frontend", "backend"):
            self.assertEqual(result[component + "_reference"],
                             record[component + "_repository"] + "@" + record[component + "_digest"])
        self.assertNotIn("semantic_version", record)

    def test_uppercase_hex_is_normalized_only_in_output(self):
        record = synthetic_pair()
        record["git_commit"] = "A" * 40
        record["frontend_digest"] = "sha256:" + "B" * 64
        result = validate(record)
        self.assertEqual(result["git_tag"], "git-" + "a" * 40)
        self.assertTrue(result["frontend_reference"].endswith("sha256:" + "b" * 64))
        self.assertEqual(record["git_commit"], "A" * 40)

    def test_required_fields_and_partial_pairs(self):
        for field in synthetic_pair():
            with self.subTest(missing=field):
                record = synthetic_pair()
                del record[field]
                with self.assertRaises(ValidationError):
                    validate(record)

    def test_invalid_field_values(self):
        cases = {
            "schema_version": ["2.0", 1, None],
            "git_commit": ["abc123", "g" * 40, "a" * 41, "a" * 40 + "\n"],
            "platform": ["linux/arm64", "amd64", "linux/amd64,linux/arm64"],
            "build_timestamp": ["2000-01-01", "2000-01-01T00:00:00", "2000-01-01T00:00:00+01:00",
                                "2000-01-01T00:00:00+00:00", "2001-02-29T00:00:00Z",
                                "2000-13-01T00:00:00Z", "2000-01-01T24:00:00Z", "0000-01-01T00:00:00Z"],
            "semantic_version": ["1.2.3", "v01.2.3", "v1.2", "latest", "prod", "production",
                                 "stable", "current", "v1.2.3-01", "v1.2.3+build", "v1.2.3-", None],
            "digest_source": ["docker-image-id", "docker-config", "local", "", None],
        }
        for component in ("frontend", "backend"):
            cases[component + "_digest"] = ["", "<digest>", "b" * 64, "sha256:abc", "latest",
                                           "sha256:" + "g" * 64, "sha256:" + "b" * 65,
                                           "docker://sha256:" + "b" * 64]
        for field, values in cases.items():
            for value in values:
                with self.subTest(field=field, value=value):
                    record = synthetic_pair()
                    record[field] = value
                    with self.assertRaises(ValidationError):
                        validate(record)

    def test_semver_aliases(self):
        for version in ("v0.0.0", "v1.2.3", "v1.2.3-rc.1", "v1.2.3-alpha-beta.0"):
            record = synthetic_pair()
            record["semantic_version"] = version
            self.assertEqual(validate(record)["git_tag"], "git-" + "a" * 40)

    def test_repository_pair_separation(self):
        record = synthetic_pair()
        frontend = record["frontend_repository"]
        cases = [frontend, frontend.replace("frontend", "other-backend"),
                 record["backend_repository"].replace("000000000000", "111111111111"),
                 record["backend_repository"].replace("eu-west-1", "eu-west-2"),
                 record["backend_repository"] + ":latest",
                 record["backend_repository"] + "@sha256:" + "c" * 64,
                 "https://" + record["backend_repository"], "backend", "<BACKEND_REPOSITORY>"]
        for repository in cases:
            with self.subTest(repository=repository):
                record = synthetic_pair()
                record["backend_repository"] = repository
                with self.assertRaises(ValidationError):
                    validate(record)
        record = synthetic_pair()
        record["frontend_repository"], record["backend_repository"] = (
            record["backend_repository"], record["frontend_repository"])
        with self.assertRaises(ValidationError):
            validate(record)

    def test_no_component_override_or_unexpected_fields(self):
        for field in ("frontend_git_commit", "backend_platform", "frontend_tag", "scan_approved", "password"):
            record = synthetic_pair()
            record[field] = "unexpected"
            with self.assertRaises(ValidationError):
                validate(record)
        for record in ([], "record", None, 1):
            with self.assertRaises(ValidationError):
                validate(record)

    def test_local_digest_provenance_is_not_registry_evidence(self):
        record = synthetic_pair()
        # A full Docker ID has the same syntax. Explicit local provenance is rejected.
        record["frontend_digest"] = "sha256:" + "d" * 64
        record["digest_source"] = "docker-image-id"
        with self.assertRaises(ValidationError):
            validate(record)
        # No offline validator can detect a false registry-origin assertion.
        record["digest_source"] = "ecr-registry-manifest"
        validate(record)

    def test_cli_json_and_safe_errors(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "synthetic.json"
            path.write_text(json.dumps(synthetic_pair()))
            output, error = io.StringIO(), io.StringIO()
            with redirect_stdout(output), redirect_stderr(error):
                self.assertEqual(main([str(path)]), 0)
            self.assertIn("STRUCTURALLY VALID ONLY", error.getvalue())
            self.assertIn("frontend_reference", json.loads(output.getvalue()))
            for raw in ('{"git_commit":"first","git_commit":"second"}', '{bad json', '\ufeff{}'):
                path.write_text(raw)
                with self.assertRaises(ValidationError):
                    load_record(path)
            path.write_text('{"unrecognized":"DO-NOT-ECHO"}')
            output, error = io.StringIO(), io.StringIO()
            with redirect_stdout(output), redirect_stderr(error):
                self.assertEqual(main([str(path)]), 1)
            self.assertEqual(output.getvalue(), "")
            self.assertNotIn("DO-NOT-ECHO", error.getvalue())
            with self.assertRaises(ValidationError):
                load_record(Path(directory) / "missing.json")


class CrossContractTest(unittest.TestCase):
    def test_schema_shape_and_supported_constraints(self):
        schema = json.loads((ROOT / "ops/release/release-pair.schema.json").read_text())
        self.assertEqual(schema["type"], "object")
        self.assertFalse(schema["additionalProperties"])
        self.assertEqual(set(schema["required"]), set(synthetic_pair()))
        self.assertEqual(set(schema["properties"]), set(synthetic_pair()) | {"semantic_version"})
        for rule in schema["properties"].values():
            self.assertEqual(rule["type"], "string")
            self.assertLessEqual(set(rule), {"type", "const", "pattern", "description", "maxLength"})

    def test_ecr_iac_and_runtime_alignment(self):
        # Focused textual checks supplement the existing structured readiness/parity tests.
        tf = (ROOT / "infra/terraform/ecr.tf").read_text()
        for pattern in (r'for_each\s*=\s*toset\(\["frontend", "backend"\]\)',
                        r'image_tag_mutability\s*=\s*"IMMUTABLE"', r'encryption_type\s*=\s*"AES256"',
                        r'tagStatus\s*=\s*"untagged"', r'countNumber\s*=\s*7\b',
                        r'countUnit\s*=\s*"days"', r'countType\s*=\s*"sinceImagePushed"'):
            self.assertRegex(tf, pattern)
        self.assertIn('${local.name_prefix}-${each.key}', tf)
        self.assertEqual(len(re.findall(r'resource "aws_ecr_repository"', tf)), 1)
        outputs = (ROOT / "infra/terraform/outputs.tf").read_text()
        self.assertIn('output "ecr_repository_urls"', outputs)
        self.assertIn('for key, repository in aws_ecr_repository.app : key => repository.repository_url', outputs)
        cf = (ROOT / "infra/cloudformation/taskflow.yaml").read_text()
        self.assertEqual(cf.count('Type: AWS::ECR::Repository\n'), 2)
        for component in ("Frontend", "Backend"):
            block = re.search(r'^  ' + component + r'Repository:\n(.*?)(?=^  \w+:)', cf, re.M | re.S)[1]
            for term in ('ImageTagMutability: IMMUTABLE', 'EncryptionType: AES256',
                         '"tagStatus":"untagged"', '"countNumber":7', '"countUnit":"days"',
                         '${ProjectName}-${Environment}-' + component.lower()):
                self.assertIn(term, block)
        compose = (ROOT / "compose.production.yaml").read_text()
        for component in ("FRONTEND", "BACKEND"):
            self.assertIn('image: ${TASKFLOW_' + component + '_IMAGE:?', compose)
        self.assertNotRegex(compose, r'(?m)^\s*build:')
        release = (ROOT / "docs/CONTAINER_RELEASE.md").read_text()
        for term in ('<repository>@sha256:<digest>', 'git-<full-git-sha>', 'linux/amd64',
                     'REGISTRY_DELIVERY.md', 'registry manifest', 'scan-on-push'):
            self.assertIn(term, release)


if __name__ == "__main__":
    unittest.main(verbosity=2)
