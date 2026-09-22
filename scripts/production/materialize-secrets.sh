#!/usr/bin/env bash
# REFERENCE ONLY: real AWS retrieval is forbidden during TaskFlow validation.
# Run as root on the reference Linux host; tests inject an offline fake aws.
set +x
set -euo pipefail
umask 077
export AWS_PAGER="" AWS_CLI_AUTO_PROMPT=off
fail() { printf '%s\n' 'Secret materialization failed; existing files were not replaced.' >&2; exit 1; }
[[ $EUID -eq 0 && $# -eq 2 && -n $1 && -n $2 && $1 != "$2" ]] || fail
for tool in aws jq python3 flock; do command -v "$tool" >/dev/null || fail; done
[[ $(aws --version 2>/dev/null) == aws-cli/2.* ]] || fail
# Production always uses the canonical location. Override is for isolated offline tests.
runtime=${TASKFLOW_RUNTIME_DIR:-/run/taskflow}
[[ $runtime == /* && ! -L $runtime ]] || fail
install -d -o root -g root -m 0700 "$runtime"
exec 9>"$runtime/materialize.lock"
flock -x 9
[[ ! -L $runtime/secrets ]] || fail
stage=$(mktemp -d "$runtime/.secrets.XXXXXXXX")
cleanup() { rm -rf -- "$stage"; }
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
trap fail ERR
# Response/error streams stay off the terminal; errors may contain sensitive context.
aws secretsmanager get-secret-value --secret-id "$1" --version-stage AWSCURRENT \
  --query SecretString --output json >"$stage/db-response" 2>"$stage/aws-error"
aws secretsmanager get-secret-value --secret-id "$2" --version-stage AWSCURRENT \
  --query SecretString --output json >"$stage/jwt-response" 2>"$stage/aws-error"
# Reject controls/blank values; preserve spaces, quotes and shell punctuation verbatim.
jq -e 'fromjson | type == "object" and .username == "taskflow_app" and (keys | sort) == ["password","username"] and
  ([.username,.password] | all(type == "string" and test("[^\\s]") and
  (explode | all(. >= 32 and . != 127))))' "$stage/db-response" >/dev/null 2>/dev/null
jq -jr 'fromjson | .username' "$stage/db-response" >"$stage/spring.datasource.username" 2>/dev/null
jq -jr 'fromjson | .password' "$stage/db-response" >"$stage/spring.datasource.password" 2>/dev/null
jq -e 'type == "string" and test("^[A-Za-z0-9+/]{43}=$")' "$stage/jwt-response" >/dev/null 2>/dev/null
jq -jr '.' "$stage/jwt-response" >"$stage/taskflow.security.jwt.secret-base64"
# Strict decoding and canonical encoding; content never enters argv or environment.
python3 - "$stage/taskflow.security.jwt.secret-base64" <<'PY'
import base64, pathlib, sys
try:
    encoded = pathlib.Path(sys.argv[1]).read_bytes()
    decoded = base64.b64decode(encoded, validate=True)
    if len(decoded) != 32 or base64.b64encode(decoded) != encoded:
        raise ValueError()
except Exception:
    sys.exit(1)
PY
rm -- "$stage/db-response" "$stage/jwt-response" "$stage/aws-error"
chown 10001:10001 "$stage/"*
chmod 0400 "$stage/"*
# Atomic directory publication, including replacement of a non-empty directory.
# AL2023/Linux renameat2(RENAME_EXCHANGE); fail closed if unsupported. No fallback
# to sequential file replacement. Both paths are on the same /run filesystem.
python3 - "$stage" "$runtime/secrets" <<'PY'
import ctypes, os, sys
try:
    source, target = sys.argv[1:]
    if os.path.lexists(target):
        if os.path.islink(target) or not os.path.isdir(target):
            raise ValueError()
        libc = ctypes.CDLL(None, use_errno=True)
        rename = libc.renameat2
        rename.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
        rename.restype = ctypes.c_int
        if rename(-100, os.fsencode(source), -100, os.fsencode(target), 2) != 0:
            raise OSError()
    else:
        os.rename(source, target)
except Exception:
    sys.exit(1)
PY
# EXIT removes the old generation after exchange (or the now-absent staging path).
trap - ERR
printf '%s\n' 'Secret files materialized; backend recreation is required to consume replacements.'
