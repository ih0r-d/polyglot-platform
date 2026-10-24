#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/common.sh"

ROOT_DIR="$REPO_ROOT"
LOCAL_REPO="$ROOT_DIR/target/local-repo"
LOG_FILE=""
PROJECT_VERSION="$(awk '/<artifactId>polyglot-aggregator<\/artifactId>/{found=1;next} found && /<version>/{gsub(/.*<version>|<\/version>.*/, ""); print; exit}' "$ROOT_DIR/pom.xml")"

usage() {
  cat <<'EOF'
Usage: ./.dev/dry-run-release.sh [--log <path>]

Options:
  --log <path>  Write the full script output to a log file while keeping it in the terminal.
  -h, --help    Show this help message.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --log)
      if [ $# -lt 2 ]; then
        echo "ERROR: --log requires a file path" >&2
        exit 1
      fi
      LOG_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [ -n "$LOG_FILE" ]; then
  mkdir -p "$(dirname "$LOG_FILE")"
  : > "$LOG_FILE"
  exec > >(tee -a "$LOG_FILE") 2>&1
fi

cd "$ROOT_DIR"

case "$PROJECT_VERSION" in
  *-SNAPSHOT) ;;
  *)
    echo "ERROR: Local release dry-run requires a -SNAPSHOT project version; got '$PROJECT_VERSION'." >&2
    exit 1
    ;;
esac

setup_java_home
setup_maven_opts
echo "==> Using JDK: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"

echo "==> Cleaning previous dry-run artifacts"
rm -rf "$LOCAL_REPO"
find "$ROOT_DIR" -name ".flattened-pom.xml" -delete

echo "==> Generating flattened POMs with release profile"
./mvnw -B -ntp -Prelease -DskipTests clean process-resources

echo
echo "==> Flattened POM files"
find "$ROOT_DIR" -name ".flattened-pom.xml" | sort

POMS="
api/polyglot-annotations/.flattened-pom.xml
api/polyglot-model/.flattened-pom.xml
runtime/polyglot-bom/.flattened-pom.xml
runtime/polyglot-adapter/.flattened-pom.xml
runtime/polyglot-spring-boot-starter/.flattened-pom.xml
build-tools/polyglot-codegen/.flattened-pom.xml
build-tools/polyglot-codegen-maven-plugin/.flattened-pom.xml
"

echo
echo "==> Verifying flattened publishable POMs exist"
for f in $POMS; do
  if [ ! -f "$ROOT_DIR/$f" ]; then
    echo "ERROR: Missing flattened POM: $f"
    exit 1
  fi
done

echo
echo "==> Verifying flattened publishable POMs do not contain <parent>"
for f in $POMS; do
  if grep -q "<parent>" "$ROOT_DIR/$f"; then
    echo "ERROR: <parent> still present in $f"
    exit 1
  fi
done

echo
echo "==> Inspecting key metadata in flattened POMs"
for f in $POMS; do
  echo
  echo "--- $f"
  grep -nE "<groupId>|<artifactId>|<version>|<licenses>|<scm>|<developers>|<url>|<dependencyManagement>" "$ROOT_DIR/$f" || true
done

echo
echo "==> Verifying BOM still contains dependencyManagement"
if ! grep -q "<dependencyManagement>" "$ROOT_DIR/runtime/polyglot-bom/.flattened-pom.xml"; then
  echo "ERROR: BOM flattened POM lost <dependencyManagement>"
  exit 1
fi

echo
echo "==> Running local deploy dry-run"
./mvnw -B -ntp -Prelease -DskipTests -Dgpg.skip=true \
  -DcentralBaseUrl="file://$LOCAL_REPO" \
  -DcentralSnapshotsUrl="file://$LOCAL_REPO" \
  clean deploy

echo
echo "==> Deployed artifacts"
find "$LOCAL_REPO/io/github/ih0r-d" -maxdepth 3 | sort || true

UNWANTED="
polyglot-aggregator
polyglot-api
polyglot-adapter-parent
polyglot-build-tools
"

echo
echo "==> Checking unwanted artifacts are NOT deployed"
for a in $UNWANTED; do
  if find "$LOCAL_REPO" -type d -name "$a" | grep -q .; then
    echo "ERROR: Unwanted artifact was deployed: $a"
    exit 1
  fi
done

EXPECTED="
polyglot-annotations
polyglot-model
polyglot-bom
polyglot-adapter
polyglot-spring-boot-starter
polyglot-codegen
polyglot-codegen-maven-plugin
"

echo
echo "==> Checking expected artifacts ARE deployed"
for a in $EXPECTED; do
  if ! find "$LOCAL_REPO" -type d -name "$a" | grep -q .; then
    echo "ERROR: Expected artifact was not deployed: $a"
    exit 1
  fi
done

echo
echo "SUCCESS: dry-run release verification passed"
