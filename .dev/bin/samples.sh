#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

(run_mvn -DskipTests install)
"$SCRIPT_DIR/validate-sample-versions.sh"
for sample in java-maven-example java-maven-codegen-example spring-boot-example; do
  (run_mvn -DskipTests verify -f "samples/$sample/pom.xml")
done
