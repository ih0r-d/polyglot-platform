#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

echo "[INFO] Cleaning Maven build output and generated files"
run_mvn clean "$@"

cd_repo_root
find . -type f \( \
  -name '.flattened-pom.xml' -o \
  -name 'dependency-reduced-pom.xml' -o \
  -name 'pom.xml.tag' -o \
  -name 'pom.xml.releaseBackup' -o \
  -name 'pom.xml.versionsBackup' -o \
  -name 'pom.xml.next' -o \
  -name 'release.properties' \
\) -delete

echo "[INFO] Clean complete"
