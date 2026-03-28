#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

run_mvn spotless:apply

if ! git diff --quiet -- .; then
  echo "Spotless updated files. Stage the changes and run the commit again." >&2
  exit 1
fi

run_mvn spotless:check
run_mvn -DskipTests validate "$@"
