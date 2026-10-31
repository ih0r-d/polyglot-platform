#!/usr/bin/env bash

# Source common.sh first to resolve REPO_ROOT and the logging helpers.
DOCS_VENV="$REPO_ROOT/.local/venv-docs"
export NO_MKDOCS_2_WARNING=true

ensure_docs_venv() {
  print_info "Preparing docs virtual environment: $DOCS_VENV"
  if [ ! -x "$DOCS_VENV/bin/python" ]; then
    python3 -m venv "$DOCS_VENV"
  fi

  # shellcheck disable=SC1091
  source "$DOCS_VENV/bin/activate"

  # Release preflight refreshes tooling; ordinary builds reuse the installed environment.
  if [ "${1:-}" = refresh ] || [ ! -x "$DOCS_VENV/bin/mkdocs" ]; then
    python -m pip install --upgrade pip
    python -m pip install mkdocs mkdocs-material
  fi
}
