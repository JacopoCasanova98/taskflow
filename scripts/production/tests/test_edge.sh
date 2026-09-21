#!/usr/bin/env bash
# Disposable isolated fixture bridge, never production Compose or AWS.
set -euo pipefail
root=$(cd "$(dirname "$0")/../../.." && pwd)
name="taskflow-edge-test-$$"
image="${TASKFLOW_EDGE_TEST_IMAGE:-taskflow-frontend:ms9.5}"
cleanup() {
  docker rm -f "$name-frontend" "$name-backend" >/dev/null 2>&1 || true
  docker network rm "$name" >/dev/null 2>&1 || true
}
trap cleanup EXIT
# Exact reference source ranges are exercised; other peers share this isolated /16.
docker network create --internal --subnet 10.42.0.0/16 "$name" >/dev/null
docker run -d --rm --pull=never --network "$name" --ip 10.42.2.3 --network-alias backend \
  --name "$name-backend" --read-only --cap-drop ALL --security-opt no-new-privileges:true \
  -v "$root/scripts/production/tests/test_edge.py:/test.py:ro" --entrypoint python \
  taskflow-cfn-lint:1.57.0 /test.py backend >/dev/null
docker run --rm --pull=never --network none --read-only --tmpfs /tmp \
  --entrypoint nginx "$image" -t
docker run -d --rm --pull=never --network "$name" --ip 10.42.2.2 --network-alias frontend \
  --name "$name-frontend" --read-only --tmpfs /tmp --cap-drop ALL \
  --security-opt no-new-privileges:true --entrypoint nginx "$image" -g 'daemon off;' >/dev/null
for mode in trusted untrusted; do
  ip=10.42.0.10
  if [[ "$mode" == untrusted ]]; then ip=10.42.2.10; fi
  docker run --rm --pull=never --network "$name" --ip "$ip" --read-only --cap-drop ALL \
    --security-opt no-new-privileges:true -v "$root/scripts/production/tests/test_edge.py:/test.py:ro" \
    --entrypoint python taskflow-cfn-lint:1.57.0 /test.py "$mode"
done
