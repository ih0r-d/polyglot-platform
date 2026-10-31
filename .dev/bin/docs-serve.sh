#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/docs.sh"
DOCS_SITE_DIR="$REPO_ROOT/site"

cleanup_docs_site() {
  if [ -d "$DOCS_SITE_DIR" ]; then
    print_info "Cleaning docs site output: $DOCS_SITE_DIR"
    rm -rf "$DOCS_SITE_DIR"
  fi
}

run_docs_serve() {
  cd_repo_root

  if command -v mkdocs >/dev/null 2>&1; then
    print_info "Starting local docs server with mkdocs"
    mkdocs serve "$@"
    return
  fi

  ensure_docs_venv
  python -m mkdocs serve "$@"
}

print_section "Documentation Server"
trap cleanup_docs_site INT TERM EXIT
run_docs_serve "$@"
