#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

cd_repo_root

print_section "Git Hooks"
print_step "Configuring repository-local hooks path"
git config core.hooksPath .githooks

print_step "Marking commit-msg hook as executable"
if command -v chmod >/dev/null 2>&1; then
  chmod +x .githooks/commit-msg || print_warn "Unable to change hook permissions on this filesystem."
fi

print_info "Local Git hooks are configured for this clone."
print_info "Git will use .githooks/commit-msg for commit message validation."
