#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

cd_repo_root

print_section "Contributor Setup"
print_step "Installing repository-local Git hooks"
"$SCRIPT_DIR/setup-hooks.sh"

print_step "Checking local Maven/Java environment"
project_version="$("$SCRIPT_DIR/version.sh")"
print_info "Project version: ${project_version}"

print_step "Running lightweight local validation"
run_mvn -DskipTests validate

print_section "Next Steps"
print_info "Use 'task build' or './mvnw clean verify' for a full local build."
print_info "Use 'task verify' before opening a pull request."
print_info "Use 'task quality' for the stricter local quality gate."
print_info "Git hooks are enabled only for this clone."
