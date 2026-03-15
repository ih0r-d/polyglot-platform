#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:-}

if [ -z "$VERSION" ]; then
  echo "❌ VERSION required"
  exit 1
fi

if ! git diff-index --quiet HEAD --; then
  echo "❌ Working directory not clean"
  exit 1
fi

source "$HOME/.sdkman/bin/sdkman-init.sh" >/dev/null 2>&1

release_module() {
  local DIR="$1"

  echo "→ Releasing $DIR"
  cd "$DIR"
  sdk env >/dev/null 2>&1

  ./mvnw -q -ntp -B versions:set \
    -DnewVersion="$VERSION" \
    -DprocessAllModules=true \
    -DgenerateBackupPoms=false

  ./mvnw -Prelease deploy

  cd - >/dev/null
}

release_module adapter
release_module tooling

git cliff --config .git-cliff.toml \
  --unreleased \
  --tag "$VERSION" \
  --prepend CHANGELOG.md

git add CHANGELOG.md adapter/**/pom.xml tooling/**/pom.xml
git commit -m "Release $VERSION" || true
git tag -a "v$VERSION" -m "Release $VERSION"

git push origin main
git push origin "v$VERSION"

echo "✅ Release $VERSION completed"