#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MODULE="${MODULE:-}"
SKIP_TESTS="${SKIP_TESTS:-false}"

cd "$REPO_ROOT"
JAVA_HOME="$(cd "$(dirname "$(command -v java)")/.." && pwd -P)"
export JAVA_HOME

args=(-B -ntp clean install -Djacoco.skip=true)

if [ "$SKIP_TESTS" = "true" ]; then
  args+=(-DskipTests)
fi

if [ -n "$MODULE" ]; then
  echo "[INFO] Building module: $MODULE"
  args+=(-pl "$MODULE" -am)
else
  echo "[INFO] Building full project"
fi

echo "[INFO] JDK: $(java -version 2>&1 | head -n 1)"
./mvnw "${args[@]}"
