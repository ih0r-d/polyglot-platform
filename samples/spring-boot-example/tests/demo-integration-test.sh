#!/usr/bin/env bash
set -euo pipefail
SAMPLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT=${DEMO_TEST_PORT:-18085}
CLI="$SAMPLE_DIR/demo.sh"
# A new managed instance is required: never stop a developer's already-running service.
[ ! -f "$SAMPLE_DIR/.local/showcase/pid" ] || { echo 'Existing managed instance; stop it or use a fresh checkout before this integration test.' >&2; exit 1; }
cleanup() { "$CLI" stop; }
trap cleanup EXIT
"$CLI" demo --port "$PORT" --requests 6 --errors 2 --concurrency 3 --size 100
"$CLI" status --port "$PORT"
"$CLI" smoke --port "$PORT"
"$CLI" traffic --port "$PORT" --requests 2 --errors 0 --concurrency 2 --size 100000
"$CLI" restart --port "$PORT" --metrics false
"$CLI" smoke --port "$PORT"
"$CLI" metrics --port "$PORT"
printf '[PASS] Packaged JAR, repeatable Bash demo, concurrency, large payload, metrics disabled and lifecycle\n'
