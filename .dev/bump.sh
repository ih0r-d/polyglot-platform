#!/usr/bin/env bash
set -euo pipefail

TYPE=${1:-patch}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"
JAVA_HOME="$(cd "$(dirname "$(command -v java)")/.." && pwd -P)"
export JAVA_HOME

CURRENT="$(./mvnw -q -Dexpression=project.version -DforceStdout help:evaluate)"
BASE="${CURRENT/-SNAPSHOT/}"

IFS='.' read -r MAJOR MINOR PATCH <<< "$BASE"

case "$TYPE" in
  major) ((MAJOR++)); MINOR=0; PATCH=0 ;;
  minor) ((MINOR++)); PATCH=0 ;;
  patch) ((PATCH++)) ;;
  *) echo "❌ Use patch|minor|major"; exit 1 ;;
esac

NEXT="$MAJOR.$MINOR.$PATCH-SNAPSHOT"

./mvnw -B -ntp versions:set \
  -DnewVersion="$NEXT" \
  -DprocessAllModules=true \
  -DgenerateBackupPoms=false

git add pom.xml api/**/pom.xml runtime/**/pom.xml build-tools/**/pom.xml
git commit -m "Bump version: $CURRENT → $NEXT" || true

echo "✅ Bumped: $CURRENT → $NEXT"
