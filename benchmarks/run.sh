#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source .dev/lib/common.sh
require_pinned_graalvm
exec "$JAVA_HOME/bin/java" -cp "benchmarks/target/classes:$(cat benchmarks/target/classpath.txt)" \
  org.openjdk.jmh.Main "$@"
