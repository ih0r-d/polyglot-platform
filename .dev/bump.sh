#!/usr/bin/env bash
set -euo pipefail

TYPE=${1:-patch}

source "$HOME/.sdkman/bin/sdkman-init.sh" >/dev/null 2>&1

get_version() {
  cd "$1"
  sdk env >/dev/null 2>&1
  ./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout
  cd - >/dev/null
}

set_version() {
  cd "$1"
  sdk env >/dev/null 2>&1
  ./mvnw -q -B -ntp versions:set \
    -DnewVersion="$2" \
    -DprocessAllModules=true \
    -DgenerateBackupPoms=false
  cd - >/dev/null
}

CURRENT="$(get_version adapter)"
BASE="${CURRENT/-SNAPSHOT/}"

IFS='.' read -r MAJOR MINOR PATCH <<< "$BASE"

case "$TYPE" in
  major) ((MAJOR++)); MINOR=0; PATCH=0 ;;
  minor) ((MINOR++)); PATCH=0 ;;
  patch) ((PATCH++)) ;;
  *) echo "❌ Use patch|minor|major"; exit 1 ;;
esac

NEXT="$MAJOR.$MINOR.$PATCH-SNAPSHOT"

set_version adapter "$NEXT"
set_version tooling "$NEXT"

git add adapter/**/pom.xml tooling/**/pom.xml
git commit -m "Bump version: $CURRENT → $NEXT" || true

echo "✅ Bumped: $CURRENT → $NEXT"