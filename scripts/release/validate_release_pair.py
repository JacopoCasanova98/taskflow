"""Offline release-pair structure validation; never authenticates or queries a registry."""

import argparse
from datetime import datetime
import json
from pathlib import Path
import re
import sys


SCHEMA_PATH = Path(__file__).resolve().parents[2] / "ops/release/release-pair.schema.json"


class ValidationError(ValueError):
    """Invalid release record; messages never echo candidate values."""


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValidationError("Duplicate JSON field")
        result[key] = value
    return result


def load_record(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    except (OSError, UnicodeError, json.JSONDecodeError, RecursionError) as exc:
        raise ValidationError("Cannot read a valid UTF-8 JSON record") from exc


def validate(record):
    """Return derived references, not deployment approval or registry attestation.

    The fixed v1 schema supplies field constraints. This is a purpose-built
    validator for that flat string record, not a general JSON Schema engine.
    Cross-field and calendar rules below are additional mandatory checks.
    """
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    if not isinstance(record, dict):
        raise ValidationError("Record must be a JSON object")
    if set(record) - set(schema["properties"]):
        raise ValidationError("Unknown record field")
    if set(schema["required"]) - set(record):
        raise ValidationError("Incomplete release pair")
    for field, value in record.items():
        rule = schema["properties"][field]
        if not isinstance(value, str):
            raise ValidationError(field + " must be a string")
        if len(value) > rule.get("maxLength", 4096):
            raise ValidationError(field + " exceeds its maximum length")
        if "const" in rule and value != rule["const"]:
            raise ValidationError(field + " has an unsupported value")
        if "pattern" in rule and re.fullmatch(rule["pattern"], value) is None:
            raise ValidationError(field + " has an invalid format")

    frontend = record["frontend_repository"].removesuffix("-frontend")
    backend = record["backend_repository"].removesuffix("-backend")
    if frontend != backend:
        raise ValidationError("Repositories must share registry, region and name prefix")
    try:
        datetime.strptime(record["build_timestamp"], "%Y-%m-%dT%H:%M:%SZ")
    except ValueError as exc:
        raise ValidationError("build_timestamp must be a valid UTC calendar timestamp") from exc

    return {
        "git_tag": "git-" + record["git_commit"].lower(),
        "frontend_reference": record["frontend_repository"] + "@" + record["frontend_digest"].lower(),
        "backend_reference": record["backend_repository"] + "@" + record["backend_digest"].lower(),
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("record", help="candidate release-pair JSON path")
    args = parser.parse_args(argv)
    try:
        references = validate(load_record(args.record))
    except ValidationError as exc:
        print("INVALID: " + str(exc), file=sys.stderr)
        return 1
    print("STRUCTURALLY VALID ONLY: registry provenance and scan approval require external evidence.",
          file=sys.stderr)
    print(json.dumps(references, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
