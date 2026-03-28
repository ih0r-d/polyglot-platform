#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

cd_repo_root
setup_java_home
setup_maven_opts
./mvnw -q -DforceStdout -Dexpression=project.version help:evaluate 2>/dev/null | tr -d '\r' | grep -E '^[0-9]'
