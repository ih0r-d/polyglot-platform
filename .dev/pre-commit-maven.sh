#!/usr/bin/env bash
set -euo pipefail

./mvnw -B -ntp spotless:apply

if ! git diff --quiet -- .; then
  echo "Spotless updated files. Stage the changes and run the commit again." >&2
  exit 1
fi

./mvnw -B -ntp spotless:check
./mvnw -B -ntp -DskipTests validate
