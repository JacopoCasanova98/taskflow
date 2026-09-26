"""Validate the MS10.5 reference contract offline; never apply IAM or request tokens.

V1 is deliberately closed. Changes to trust/capability boundaries require a reviewed
contract revision and corresponding validator changes, not arbitrary IAM extensions.
"""
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONTRACT = ROOT / "ops/identity/github-oidc-contract.json"


class ValidationError(ValueError):
    """Safe diagnostic: never include candidate values or keys."""


def require(condition):
    if not condition:
        raise ValidationError("identity contract violates the reviewed v1 security boundary")


def exact(actual, expected):
    """Closed, type-strict structure; capability arrays are duplicate-free sets."""
    require(type(actual) is type(expected))
    if isinstance(expected, dict):
        require(actual.keys() == expected.keys())
        for key, value in expected.items():
            exact(actual[key], value)
    elif isinstance(expected, list):
        require(all(type(item) is str for item in actual))
        require(len(actual) == len(set(actual)) and set(actual) == set(expected))
    else:
        require(actual == expected)


# These are independent enforcement rules, not loaded from the candidate document.
ROLE_RULES = {
    "runtime_host": ("<RUNTIME_HOST_ROLE_NAME>", "existing-iac-model",
                     "ecr_auth ecr_pull_taskflow_repositories application_secret_read ssm_agent telemetry_write",
                     "ecr_push_taskflow_repositories iam_mutation infrastructure_mutation master_secret_read migrator_secret_read"),
    "registry_publisher": ("<REGISTRY_PUBLISHER_ROLE_NAME>", "reference-only",
                           "ecr_auth ecr_push_taskflow_repositories ecr_read_release_metadata ecr_read_scan_results",
                           "application_secret_read master_secret_read migrator_secret_read ssm_deployment_interface ssm_remote_shell ec2_mutation rds_access iam_mutation infrastructure_mutation terraform_state_access route53_mutation acm_mutation cloudwatch_administration"),
    "deployment_operator": ("<DEPLOYMENT_ROLE_NAME>", "blocked-pending-reviewed-interface-and-migration-credentials", "",
                            "ssm_remote_shell application_secret_read master_secret_read migrator_secret_read ecr_push_taskflow_repositories iam_mutation infrastructure_mutation"),
    "infrastructure_provisioner": ("<INFRASTRUCTURE_PROVISIONER_ROLE_NAME>", "external-authorization-out-of-scope", "",
                                   "shared_delivery_identity"),
}
PUSH_ACTIONS = "BatchCheckLayerAvailability InitiateLayerUpload UploadLayerPart CompleteLayerUpload PutImage BatchGetImage DescribeImages DescribeImageScanFindings".split()


def validate(candidate):
    expected = {
        "schema_version": "1.0", "status": "reference-only-non-applied",
        "issuer": "https://token.actions.githubusercontent.com",
        "audience": "sts.amazonaws.com", "repository": "JacopoCasanova98/taskflow",
        "authorized_subjects": ["repo:JacopoCasanova98/taskflow:environment:release"],
        "subject_format": "name-based-reference-requires-verification-before-enabling",
        "trust_operator": "StringEquals", "credential_model": "short-lived-web-identity",
        "ordinary_ci": {"permissions": {"contents": "read"}, "aws_federation": False},
        "future_publisher_workflow": {
            "enabled": False, "trigger": "workflow_dispatch", "ref": "refs/heads/main", "environment": "release",
            "permissions": {"contents": "read", "id-token": "write"},
            "required_controls": "environment-source-restriction independent-approval no-untrusted-code reviewed-sha-and-quality-gates full-sha-action-pins verify-effective-subject".split(),
            "forbidden_sources": ["pull_request", "pull_request_target", "feature/**", "forks"],
        },
        "identities": {name: {"role": role, "state": state, "allowed": allowed.split(), "forbidden": forbidden.split()}
                       for name, (role, state, allowed, forbidden) in ROLE_RULES.items()},
        "publisher_authorization": {
            "token_actions": ["ecr:GetAuthorizationToken"], "token_resource": "*",
            "repository_actions": ["ecr:" + action for action in PUSH_ACTIONS],
            "repository_resources": ["arn:aws:ecr:eu-west-1:<AWS_ACCOUNT_ID>:repository/<NAME_PREFIX>-" + component
                                     for component in ("frontend", "backend")],
        },
        "session": {"requested_seconds": 900, "role_maximum_seconds": 3600,
                    "traceability": "github-run-id-run-attempt-and-reviewed-sha", "credential_persistence": False},
        "secrets": {
            "application_flow": ["Secrets Manager", "EC2 instance role", "materialize-secrets.sh", "/run/taskflow/secrets", "backend configtree"],
            "github_payloads": [], "migrator": "independent-live-operator-boundary",
            "master": "independent-database-administration-only",
            "forbidden_handling": "static-access-keys personal-credentials shared-developer-keys committed-env-credentials shell-tracing-credentials credential-debug-dumps secret-plan-or-release-artifacts credential-artifacts".split(),
        },
    }
    exact(candidate, expected)
    # Secret flow is ordered; all other arrays describe sets.
    require(candidate["secrets"]["application_flow"] == expected["secrets"]["application_flow"])


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result)
        result[key] = value
    return result


def load_contract(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    except (OSError, UnicodeError, ValueError, RecursionError) as exc:
        raise ValidationError("cannot read a valid identity contract JSON object") from exc


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("contract", nargs="?", type=Path, default=DEFAULT_CONTRACT)
    args = parser.parse_args(argv)
    try:
        validate(load_contract(args.contract))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print("MS10.5 identity contract valid offline; no AWS identity used")
    return 0


if __name__ == "__main__":
    sys.exit(main())
