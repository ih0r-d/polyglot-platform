#!/usr/bin/env bash
set -euo pipefail

TYPE=${1:-patch}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

cd_repo_root
setup_java_home
setup_maven_opts

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

run_mvn versions:set \
  -DnewVersion="$NEXT" \
  -DprocessAllModules=true \
  -DgenerateBackupPoms=false

mapfile -t pom_files < <(project_pom_files)
git add "${pom_files[@]}"
git commit -m "Bump version: $CURRENT → $NEXT" || true

echo "✅ Bumped: $CURRENT → $NEXT"
