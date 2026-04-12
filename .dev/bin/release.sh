#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:-}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

if [ -z "$VERSION" ]; then
  echo "❌ VERSION required"
  exit 1
fi

cd_repo_root
setup_java_home
pom_files=()
while IFS= read -r pom_file; do
  pom_files+=("$pom_file")
done < <(project_pom_files)
managed_files=(CHANGELOG.md "${pom_files[@]}")

current_version() {
  sed -n '0,/<version>/s:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -n 1
}

managed_release_changes_only() {
  local changed_file
  local changed_files=()
  while IFS= read -r changed_file; do
    changed_files+=("$changed_file")
  done < <(git diff --name-only HEAD --)
  [ "${#changed_files[@]}" -gt 0 ] || return 1
  for changed_file in "${changed_files[@]}"; do
    if [[ ! " ${managed_files[*]} " =~ " ${changed_file} " ]]; then
      return 1
    fi
  done
}

already_prepared=false
if ! git diff-index --quiet HEAD --; then
  if managed_release_changes_only && [ "$(current_version)" = "$VERSION" ]; then
    already_prepared=true
    echo "ℹ Resuming existing release preparation for $VERSION"
  else
    echo "❌ Working directory not clean"
    exit 1
  fi
fi

if [ "$already_prepared" = false ]; then
  "$SCRIPT_DIR/release-preflight.sh"

  run_mvn versions:set \
    -DnewVersion="$VERSION" \
    -DprocessAllModules=true \
    -DgenerateBackupPoms=false

  git cliff --config .git-cliff.toml \
    --unreleased \
    --tag "$VERSION" \
    --strip header \
    --prepend CHANGELOG.md
fi

git add CHANGELOG.md "${pom_files[@]}"
git commit -m "chore(release): $VERSION" || true
git tag -a "v$VERSION" -m "Release $VERSION"

git push origin main
git push origin "v$VERSION"

echo "✅ Release $VERSION prepared, tagged, and pushed"
echo "ℹ Maven Central publishing is manual-only via GitHub Actions workflow_dispatch"
