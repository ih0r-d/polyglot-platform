#!/usr/bin/env bash
set -euo pipefail
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
mkdir -p "$TEST_DIR/samples/example/.local/showcase"
cp "$SOURCE_DIR/demo.sh" "$TEST_DIR/samples/example/demo.sh"
CLI="$TEST_DIR/samples/example/demo.sh"
STATE="$TEST_DIR/samples/example/.local/showcase"
expect_failure() {
  local expected=$1 result
  shift
  if result=$(bash "$CLI" "$@" 2>&1); then echo "Expected failure: $*" >&2; exit 1; fi
  [[ "$result" == *"$expected"* ]] || { echo "Missing '$expected' in: $result" >&2; exit 1; }
}
bash "$CLI" help | grep -q 'Embedded Python Analytics'
bash "$CLI" help | grep -q 'console'
sh "$CLI" help | grep -q 'Embedded Python Analytics'
POSIXLY_CORRECT=1 sh "$CLI" help | grep -q 'Embedded Python Analytics'
bash "$CLI" start --help | grep -q 'Prerequisites'
expect_failure 'Unknown command' unknown
expect_failure 'Missing value' start --port
expect_failure 'port must be' start --port 0
expect_failure 'requests must be' traffic --requests -1
expect_failure 'Total requests/errors' traffic --requests 10000 --errors 1
expect_failure 'concurrency must be' traffic --concurrency 17
expect_failure 'size must be' smoke --size 0
expect_failure 'metrics must be' start --metrics invalid
expect_failure 'loopback' start --host example.org
bash "$CLI" stop | grep -q 'No managed sample'
printf '%s|run.notowned|8080|not the process start time\n' "$$" > "$STATE/pid"
expect_failure 'ownership' stop
kill -0 "$$"
[ -f "$STATE/pid" ]
rm "$STATE/pid"
mkdir "$STATE/lock"
expect_failure 'Another management command' start
printf '[PASS] CLI help, validation, ownership protection and duplicate management lock\n'
