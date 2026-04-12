#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

run_docs_check() {
  cd_repo_root

  if command -v mkdocs >/dev/null 2>&1; then
    echo "[INFO] Running strict docs build with mkdocs"
    mkdocs build --strict
    return
  fi

  if python3 -m mkdocs --version >/dev/null 2>&1; then
    echo "[INFO] Running strict docs build with python3 -m mkdocs"
    python3 -m mkdocs build --strict
    return
  fi

  echo "❌ mkdocs is not available. Install mkdocs and mkdocs-material before running release."
  exit 1
}

run_sample_check() {
  local sample_dir=$1

  echo "[INFO] Verifying sample: $sample_dir"
  (
    cd "$REPO_ROOT/$sample_dir"
    mvn -B -ntp -DskipTests verify
  )
}

echo "[INFO] Running release preflight checks"
echo "[INFO] Step 1/5: quality verification"
run_mvn -Pquality verify

echo "[INFO] Step 2/5: strict documentation build"
run_docs_check

echo "[INFO] Step 3/5: install local release artifacts"
run_mvn -DskipTests install

echo "[INFO] Step 4/5: maintained sample verification"
run_sample_check "samples/java-maven-example"
run_sample_check "samples/java-maven-codegen-example"
run_sample_check "samples/spring-boot-example"

echo "[INFO] Step 5/5: release preflight completed"
