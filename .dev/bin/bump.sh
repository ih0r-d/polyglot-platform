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

update_sample_polyglot_versions "$NEXT"
"$SCRIPT_DIR/validate-sample-versions.sh" "$NEXT"

pom_files=()
while IFS= read -r pom_file; do
  pom_files+=("$pom_file")
done < <(project_pom_files)
sample_pom_files=()
while IFS= read -r pom_file; do
  sample_pom_files+=("$pom_file")
done < <(sample_pom_files)
git add "${pom_files[@]}" "${sample_pom_files[@]}"
git commit -m "chore(version): bump $CURRENT to $NEXT" || true

echo "✅ Bumped: $CURRENT → $NEXT"
