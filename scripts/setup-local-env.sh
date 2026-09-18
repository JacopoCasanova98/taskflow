#!/usr/bin/env bash
set -euo pipefail

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [[ ! -f "$root/compose.yaml" ]]; then
  printf 'Cannot find compose.yaml next to the scripts directory.\n' >&2
  exit 1
fi
if [[ -e "$root/.env" || -L "$root/.env" ]]; then
  printf 'Local environment already configured: .env left untouched.\n'
  exit 0
fi
if ! command -v openssl >/dev/null 2>&1; then
  printf 'openssl is required to generate local credentials.\n' >&2
  exit 1
fi

umask 077
temporary=$(mktemp "$root/.env.setup.XXXXXX")
trap 'rm -f -- "$temporary"' EXIT
db_password=$(openssl rand -hex 32)
jwt_secret=$(openssl rand -base64 32)
if [[ $(printf '%s' "$jwt_secret" | openssl base64 -d -A | wc -c | tr -d ' ') != 32 ]]; then
  printf 'JWT generation failed: expected exactly 32 decoded bytes.\n' >&2
  exit 1
fi
printf 'TASKFLOW_DB_PASSWORD=%s\nTASKFLOW_JWT_SECRET_BASE64=%s\n' \
  "$db_password" "$jwt_secret" > "$temporary"
chmod 600 "$temporary"
# Publish the completed file without overwriting an environment created concurrently.
if ! ln "$temporary" "$root/.env"; then
  printf 'Could not create .env; any existing file was preserved.\n' >&2
  exit 1
fi
printf 'Created local .env with permissions 0600. Start with docker compose up -d --wait.\n'
