#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

DOCS_VENV="$REPO_ROOT/.venv-docs"
DOCS_SITE_DIR="$REPO_ROOT/site"
DOCS_VENV_MKDOCS="$DOCS_VENV/bin/mkdocs"
export NO_MKDOCS_2_WARNING=true

cleanup_docs_site() {
  if [ -d "$DOCS_SITE_DIR" ]; then
    print_info "Cleaning docs site output: $DOCS_SITE_DIR"
    rm -rf "$DOCS_SITE_DIR"
  fi
}

ensure_docs_venv() {
  print_info "Preparing docs virtual environment: $DOCS_VENV"
  if [ ! -x "$DOCS_VENV/bin/python" ]; then
    python3 -m venv "$DOCS_VENV"
  fi

  # shellcheck disable=SC1091
  source "$DOCS_VENV/bin/activate"

  if [ ! -x "$DOCS_VENV_MKDOCS" ]; then
    python -m pip install --upgrade pip
    python -m pip install mkdocs mkdocs-material
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
