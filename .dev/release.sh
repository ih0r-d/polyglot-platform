#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:-}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [ -z "$VERSION" ]; then
  echo "❌ VERSION required"
  exit 1
fi

cd "$REPO_ROOT"
JAVA_HOME="$(cd "$(dirname "$(command -v java)")/.." && pwd -P)"
export JAVA_HOME
if ! git diff-index --quiet HEAD --; then
  echo "❌ Working directory not clean"
  exit 1
fi

./mvnw -B -ntp versions:set \
  -DnewVersion="$VERSION" \
  -DprocessAllModules=true \
  -DgenerateBackupPoms=false

./mvnw -B -ntp -Prelease deploy

git cliff --config .git-cliff.toml \
  --unreleased \
  --tag "$VERSION" \
  --prepend CHANGELOG.md

git add CHANGELOG.md pom.xml api/**/pom.xml runtime/**/pom.xml build-tools/**/pom.xml
git commit -m "Release $VERSION" || true
git tag -a "v$VERSION" -m "Release $VERSION"

git push origin main
git push origin "v$VERSION"

echo "✅ Release $VERSION completed"
