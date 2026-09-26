"""Render an offline D1-D7 plan to stdout. No deployment executor or network calls."""

import argparse
import json
from pathlib import Path
import re
import sys
from urllib.parse import parse_qsl, urlsplit

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/release"))
from validate_release_pair import ValidationError, load_record, validate as validate_release_pair

SCHEMA_PATH = ROOT / "ops/deployment/deployment-intent.schema.json"
CA_PATH = "/opt/taskflow/trust/rds-ca-bundle.pem"


def validate_database_url(value):
    # URL parsing only; never resolves a host or opens a connection.
    if (not value.startswith("jdbc:postgresql://") or "#" in value
            or re.search(r"[\s\x00-\x1f\x7f]", value)):
        raise ValidationError("Invalid non-secret database URL")
    try:
        url = urlsplit(value[5:])
        if (url.username is not None or url.password is not None or url.fragment
                or url.port != 5432 or url.path != "/taskflow"
                or not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.[a-z0-9]+\.eu-west-1\.rds\.amazonaws\.com",
                                    url.hostname or "")):
            raise ValueError()
        query = parse_qsl(url.query, keep_blank_values=True, strict_parsing=True)
        if len(query) != 2 or dict(query) != {"sslmode": "verify-full", "sslrootcert": CA_PATH}:
            raise ValueError()
        if len({key for key, _ in query}) != 2:
            raise ValueError()
    except ValueError as exc:
        raise ValidationError("Database URL requires regional RDS, taskflow:5432 and approved verify-full TLS") from exc


def validate_intent(intent):
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    if not isinstance(intent, dict) or set(intent) != set(schema["required"]):
        raise ValidationError("Intent fields must exactly match the non-secret v1 contract")
    for field, rule in schema["properties"].items():
        if field == "release_pair":
            continue
        value = intent[field]
        if not isinstance(value, str) or len(value) > rule.get("maxLength", 256):
            raise ValidationError("Invalid intent field type or length")
        if "const" in rule and value != rule["const"]:
            raise ValidationError("Intent conflicts with the approved production contract")
        if "pattern" in rule and not re.fullmatch(rule["pattern"], value):
            raise ValidationError("Invalid non-secret identifier")
    validate_database_url(intent["database_url"])
    references = validate_release_pair(intent["release_pair"])
    for component in ("frontend", "backend"):
        host = intent["release_pair"][component + "_repository"].split("/", 1)[0]
        if not host.endswith(".dkr.ecr.eu-west-1.amazonaws.com"):
            raise ValidationError("Release registry Region conflicts with deployment Region")
    return references


def gate(identifier, purpose, requires, produces, contract):
    return {"id": identifier, "purpose": purpose, "requires": requires,
            "produces": produces, "contract": contract,
            "live_execution_required": True, "status": "not_executed"}


def build_gates(intent, references):
    return [
        gate("D1", "Validate release and preflight",
             ["independent_authorization", "registry_provenance_and_both_scan_reviews",
              "dependency_readiness", "current_schema_compatibility_decision"],
             ["eligible_pair_and_preflight_evidence"],
             {"authority": "docs/DEPLOYMENT_RUNBOOK.md", "unknown_compatibility": "stop",
              "readiness": "docs/PRODUCTION_READINESS.md", "serialize_deployments": True,
              "offline_checks": ["release_pair_structure", "non_secret_intent"],
              "live_preflight_proven": False}),
        gate("D2", "Authenticate host to ECR and pull both digests", ["D1"],
             ["both_exact_amd64_artifacts_available"],
             {"frontend_image": references["frontend_reference"],
              "backend_image": references["backend_reference"],
              "identity": "existing_host_role_reference_only", "failure": "stop_keep_current_runtime",
              "remove_temporary_registry_auth": True}),
        gate("D3", "Prepare CA/config and materialize secrets", ["D2"],
             ["validated_ca_and_config", "complete_protected_secret_generation"],
             {"helper": "scripts/production/materialize-secrets.sh",
              "secret_identifiers_in_order": [intent["database_secret_id"], intent["jwt_secret_id"]],
              "secret_stage": "AWSCURRENT", "secret_directory": "/run/taskflow/secrets",
              "files": ["spring.datasource.username", "spring.datasource.password", "taskflow.security.jwt.secret-base64"],
              "file_owner": "10001:10001", "file_mode": "0400", "directory_owner": "root:root",
              "directory_mode": "0700", "ca_path": intent["rds_ca_path"],
              "no_concurrent_materialization_and_recreation": True,
              "rotation_requires": "recreate_backend_not_restart", "failure": "stop_keep_previous_generation"}),
        gate("D4", "Activate/verify host observability", ["D3"], ["observability_prerequisites_and_evidence"],
             {"config_source": "ops/cloudwatch/amazon-cloudwatch-agent.json",
              "config_path": intent["cloudwatch_agent_config_path"],
              "precreated_log_groups": ["backend", "nginx", "host", "RDS"],
              "native_alarms": 6, "guest_alarms": 2, "sns": "optional_external_verify_if_configured",
              "authority": "docs/OBSERVABILITY.md", "telemetry_proven_offline": False}),
        gate("D5", "Run controlled Flyway migration", ["D4"], ["successful_migration_and_compatible_schema"],
             {"backend_image": references["backend_reference"], "mandatory": True,
              "entry_point": "com.taskflow.operations.DatabaseMigration",
              "launcher": "org.springframework.boot.loader.launch.PropertiesLauncher", "jar": "/app/app.jar",
              "database_role": "taskflow_migrator", "credentials": "external_ephemeral_file_at_execution_only",
              "user": "10001:10001", "read_only_root": True, "tmpfs": "/tmp",
              "no_new_privileges": True, "network": "isolated_bridge_with_existing_sg_and_imds_guard",
              "published_ports": [], "mounts": ["public_rds_ca", "protected_migration_password_file"],
              "migration_password_file_owner": "10001:10001", "migration_password_file_mode": "0400",
              "app_jwt_master_secrets_allowed": False, "cleanup": "one_shot_container_and_ephemeral_credential",
              "failure": "stop_before_D6_no_blind_retry", "unknown_compatibility": "stop"}),
        gate("D6", "Recreate backend and frontend", ["D5", "successful_migration_and_compatible_schema"],
             ["selected_pair_recreated_pending_acceptance"],
             {"compose_file": intent["compose_file"], "compose_project": intent["compose_project"],
              "services": ["backend", "frontend"], "action": "force_recreate",
              "runtime_database_role": "taskflow_app", "runtime_flyway_enabled": False,
              "hibernate": "validate", "normal_compose_down": False, "build": False, "pull": "never",
              "preserve_secure_cookies_proxy_and_protected_mounts": True, "atomic_switch": False}),
        gate("D7", "Verify runtime, edge and observability; record acceptance", ["D6"],
             ["independent_live_acceptance_record_only_if_all_checks_pass"],
             {"smoke_authority": "docs/PRODUCTION_SMOKE_TEST.md", "readiness_authority": "docs/PRODUCTION_READINESS.md",
              "public_hostname": intent["public_hostname"],
              "checks": ["runtime_health", "both_expected_running_digests", "https_host_and_certificate",
                         "http_redirect", "wrong_host_rejection", "operational_path_blocking",
                         "authentication_and_application_smoke", "logging_metrics_and_alarm_evidence",
                         "optional_sns_delivery_if_configured"],
              "mixed_pair": "fail_acceptance", "dry_run_is_acceptance": False}),
    ]


def validate_gate_order(gates):
    if [item["id"] for item in gates] != ["D1", "D2", "D3", "D4", "D5", "D6", "D7"]:
        raise ValidationError("Plan must preserve every D1-D7 gate in order")
    for previous, current in zip(gates, gates[1:]):
        if previous["id"] not in current["requires"]:
            raise ValidationError("Plan must require the previous gate")


def render_plan(intent):
    references = validate_intent(intent)
    gates = build_gates(intent, references)
    validate_gate_order(gates)
    return {
        "plan_schema_version": "1.0", "execution_mode": "dry-run", "live_operations_performed": False,
        "deployment_evidence": False, "git_commit": intent["release_pair"]["git_commit"].lower(),
        "environment": intent["environment"], "region": intent["region"], "platform": intent["release_pair"]["platform"],
        "frontend_image": references["frontend_reference"], "backend_image": references["backend_reference"],
        "runtime_inputs": {"TASKFLOW_FRONTEND_IMAGE": references["frontend_reference"],
                           "TASKFLOW_BACKEND_IMAGE": references["backend_reference"],
                           "TASKFLOW_DB_URL": intent["database_url"]},
        "failure_policy": "stop_on_failed_or_unknown_gate_no_automatic_rollback", "gates": gates,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("intent", help="local non-secret intent JSON with embedded release pair")
    args = parser.parse_args(argv)
    try:
        plan = render_plan(load_record(args.intent))
    except ValidationError:
        # Never reflect candidate values or nested field names into diagnostics.
        print("INVALID: no plan; intent or release pair violates the offline contract.", file=sys.stderr)
        return 1
    print(json.dumps(plan, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
