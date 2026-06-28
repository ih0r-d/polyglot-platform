#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:-}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

cd_repo_root

if [ -z "$VERSION" ]; then
  VERSION="$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | head -n 1)"
fi

status=0

while IFS= read -r pom_file; do
  if grep -q "<polyglot.version>" "$pom_file"; then
    sample_version="$(sed -n 's:.*<polyglot.version>\(.*\)</polyglot.version>.*:\1:p' "$pom_file" | head -n 1)"
    if [ "$sample_version" != "$VERSION" ]; then
      printf 'Sample %s uses polyglot.version=%s, expected %s\n' \
        "${pom_file#$REPO_ROOT/}" "$sample_version" "$VERSION" >&2
      status=1
    fi
  fi
done < <(sample_pom_files)

if [[ "$VERSION" != *-SNAPSHOT ]]; then
  snapshot_matches="$(mktemp)"
  while IFS= read -r -d '' sample_file; do
    grep -IHnE '[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT' "$sample_file" >> "$snapshot_matches" || true
  done < <(find "$REPO_ROOT/samples" \( -path '*/target/*' -o -path '*/.idea/*' \) -prune -o -type f ! -name pom.xml -print0)

  if [ -s "$snapshot_matches" ]; then
    cat "$snapshot_matches" >&2
    printf 'Samples must not reference released library SNAPSHOT versions for release version %s\n' "$VERSION" >&2
    status=1
  fi
  rm -f "$snapshot_matches"
fi

exit "$status"
