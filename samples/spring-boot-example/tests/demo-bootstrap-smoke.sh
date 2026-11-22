#!/usr/bin/env bash
set -euo pipefail

SAMPLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT=${DEMO_PORT:-18535}
if [ -e "$SAMPLE_DIR/target/polyglot-springboot-example-0.0.1-SNAPSHOT-dev.jar" ] ||
    [ -e "$SAMPLE_DIR/.local/showcase/pid" ]; then
  echo "Run the demo bootstrap test in a clean checkout with no managed sample running." >&2
  exit 1
fi

MAVEN_REPOSITORY=$(mktemp -d)
export MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=$MAVEN_REPOSITORY"
cleanup() {
  "$SAMPLE_DIR/demo.sh" stop --port "$PORT" || true
  rm -rf -- "$MAVEN_REPOSITORY"
}
trap cleanup EXIT

"$SAMPLE_DIR/demo.sh" start --port "$PORT" --timeout 180
find "$MAVEN_REPOSITORY/io/github/ih0r-d/polyglot-codegen-maven-plugin" -type f \
  -name 'polyglot-codegen-maven-plugin-*-SNAPSHOT.jar' | grep -q .
"$SAMPLE_DIR/demo.sh" status --port "$PORT"
