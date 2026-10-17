#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Activate the pinned Oracle GraalVM runtime first}"
exec "$JAVA_HOME/bin/java" -cp "benchmarks/target/classes:$(cat benchmarks/target/classpath.txt)" \
  org.openjdk.jmh.Main "$@"
