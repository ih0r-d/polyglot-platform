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
if ! git diff-index --quiet HEAD --; then
  echo "❌ Working directory not clean"
  exit 1
fi

run_mvn versions:set \
  -DnewVersion="$VERSION" \
  -DprocessAllModules=true \
  -DgenerateBackupPoms=false

run_mvn -Prelease deploy

git cliff --config .git-cliff.toml \
  --unreleased \
  --tag "$VERSION" \
  --prepend CHANGELOG.md

mapfile -t pom_files < <(project_pom_files)
git add CHANGELOG.md "${pom_files[@]}"
git commit -m "Release $VERSION" || true
git tag -a "v$VERSION" -m "Release $VERSION"

git push origin main
git push origin "v$VERSION"

echo "✅ Release $VERSION completed"
