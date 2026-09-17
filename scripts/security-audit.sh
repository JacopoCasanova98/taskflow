#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ "$(git rev-parse --is-shallow-repository)" != false ]]; then
  printf 'Full Git history is required; fetch the missing history before auditing.\n' >&2
  exit 1
fi

gitleaks_version=8.30.1
if command -v gitleaks >/dev/null 2>&1 && [[ "$(gitleaks version)" == "$gitleaks_version" ]]; then
  secret_scan=(gitleaks)
else
  secret_scan=(docker run --rm --network none
    --mount "type=bind,source=$repo_root,target=/repo,readonly"
    --workdir /repo "ghcr.io/gitleaks/gitleaks:v$gitleaks_version")
fi

printf '\n== Gitleaks: full Git history ==\n'
"${secret_scan[@]}" git --redact=100 --no-banner --config .gitleaks.toml --log-opts="--all --full-history" .
printf '\n== Gitleaks: working tree (including local ignored files) ==\n'
"${secret_scan[@]}" dir --redact=100 --no-banner --config .gitleaks.toml .

printf '\n== Backend dependency vulnerabilities ==\n'
nvd_options=()
if [[ -n "${NVD_API_KEY:-}" ]]; then
  nvd_options=(-DnvdApiKeyEnvironmentVariable=NVD_API_KEY)
else
  # Dependency-Check 13.0.0 has an empty-key API regression (#8715).
  # Its supported official NVD 2.0 feed avoids that path without disabling updates.
  nvd_options=('-DnvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz')
fi
(cd "$repo_root/backend" && ./mvnw org.owasp:dependency-check-maven:13.0.0:check "${nvd_options[@]}")

printf '\n== Frontend dependency vulnerabilities ==\n'
(cd "$repo_root/frontend" && npm audit --audit-level=high)
