#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "[1/2] Building a Jar"
mvn clean package -DskipTests

echo "[2/2] Building a native image"
mvn native:compile

echo
echo "Native executable created at: target/java-python-aot-adapter"

./target/java-python-aot-adapter
