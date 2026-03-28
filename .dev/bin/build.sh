#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

MODULE="${MODULE:-}"
SKIP_TESTS="${SKIP_TESTS:-false}"
MVN_ARGS=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    --module)
      MODULE="${2:?--module requires a value}"
      shift 2
      ;;
    --skip-tests)
      SKIP_TESTS=true
      shift
      ;;
    --)
      shift
      MVN_ARGS+=("$@")
      break
      ;;
    *)
      MVN_ARGS+=("$1")
      shift
      ;;
  esac
done

args=(clean install -Djacoco.skip=true)

if [ "$SKIP_TESTS" = "true" ]; then
  args+=(-DskipTests)
fi

if [ -n "$MODULE" ]; then
  echo "[INFO] Building module: $MODULE"
  args+=(-pl "$MODULE" -am)
else
  echo "[INFO] Building full project"
fi

if [ "${#MVN_ARGS[@]}" -gt 0 ]; then
  args+=("${MVN_ARGS[@]}")
fi

cd_repo_root
setup_java_home
setup_maven_opts
echo "[INFO] JDK: $(java -version 2>&1 | head -n 1)"
./mvnw -B -ntp "${args[@]}"
