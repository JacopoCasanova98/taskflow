#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

printf '\n== Backend quality ==\n'
(cd "$repo_root/backend" && ./mvnw verify)

printf '\n== Frontend quality ==\n'
(cd "$repo_root/frontend" && npm run quality)
