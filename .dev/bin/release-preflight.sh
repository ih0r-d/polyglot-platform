#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

DOCS_VENV="$REPO_ROOT/.venv-docs"
DOCS_CLEAN="${RELEASE_PREFLIGHT_CLEAN_DOCS_VENV:-0}"

cleanup_docs_venv() {
  if [ "$DOCS_CLEAN" = "1" ] && [ -d "$DOCS_VENV" ]; then
    print_info "Cleaning docs virtual environment: $DOCS_VENV"
    rm -rf "$DOCS_VENV"
  fi
}

trap cleanup_docs_venv EXIT

run_docs_check() {
  cd_repo_root

  if command -v mkdocs >/dev/null 2>&1; then
    print_info "Running strict docs build with mkdocs"
    mkdocs build --strict
    return
  fi

  print_info "Preparing docs virtual environment: $DOCS_VENV"
  if [ ! -x "$DOCS_VENV/bin/python" ]; then
    python3 -m venv "$DOCS_VENV"
  fi

  # shellcheck disable=SC1091
  source "$DOCS_VENV/bin/activate"
  python -m pip install --upgrade pip
  python -m pip install mkdocs mkdocs-material
  python -m mkdocs build --strict
}

run_sample_check() {
  local sample_dir=$1

  print_info "Verifying sample: $sample_dir"
  (
    cd "$REPO_ROOT/$sample_dir"
    mvn -B -ntp -DskipTests verify
  )
}

print_section "Release Preflight"
print_step "1/5 quality verification"
run_mvn -Pquality verify

print_step "2/5 strict documentation build"
run_docs_check

print_step "3/5 install local release artifacts"
run_mvn -DskipTests install

print_step "4/5 maintained sample verification"
run_sample_check "samples/java-maven-example"
run_sample_check "samples/java-maven-codegen-example"
run_sample_check "samples/spring-boot-example"

if [ "$DOCS_CLEAN" = "1" ]; then
  print_warn "Docs virtual environment cleanup is enabled for this run"
else
  print_info "Keeping docs virtual environment at $DOCS_VENV for faster reruns"
fi

print_step "5/5 release preflight completed"
