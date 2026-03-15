#!/usr/bin/env bash
set -euo pipefail

git fetch --tags

remote_tags=$(git ls-remote --tags origin | awk '{print $2}' | sed 's|refs/tags/||')

for tag in $remote_tags; do
  if ! git rev-parse "$tag" >/dev/null 2>&1; then
    git push origin --delete "$tag"
    echo "🗑 deleted remote tag $tag"
  fi
done

echo "✅ Remote tags synced"