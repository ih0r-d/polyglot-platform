#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/docs.sh"

run_docs_build() {
  cd_repo_root

  if command -v mkdocs >/dev/null 2>&1; then
    print_info "Running strict docs build with mkdocs"
    mkdocs build --strict "$@"
    return
  fi

  ensure_docs_venv
  python -m mkdocs build --strict "$@"
}

print_section "Documentation Build"
run_docs_build "$@"
