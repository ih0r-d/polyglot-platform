#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

WITH_SIGNING=0

usage() {
  cat <<'EOF'
Usage: ./.dev/bin/validate-maven-central-publish-local.sh [--with-signing]

Validates Maven Central publish configuration and deploy lifecycle locally without uploading.

Options:
  --with-signing  Enable GPG signing during the safe deploy lifecycle check.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --with-signing)
      WITH_SIGNING=1
      shift
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

if [ "$WITH_SIGNING" = "1" ] && [ -z "${MAVEN_GPG_PASSPHRASE:-}" ]; then
  echo "ERROR: --with-signing requires MAVEN_GPG_PASSPHRASE." >&2
  exit 1
fi

cd_repo_root

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/polyglot-central-publish-local.XXXXXX")"
SETTINGS_FILE="$TMP_ROOT/settings.xml"
DEPLOY_LOG="$TMP_ROOT/maven-deploy.log"
EFFECTIVE_DIR="$TMP_ROOT/effective-poms"
SNAPSHOT_REPO="$TMP_ROOT/maven-snapshots"
mkdir -p "$EFFECTIVE_DIR"

cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

cat > "$SETTINGS_FILE" <<'EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>central</id>
      <username>dummy</username>
      <password>dummy</password>
    </server>
  </servers>
</settings>
EOF

MODULES=(
  "api/polyglot-annotations"
  "api/polyglot-model"
  "runtime/polyglot-bom"
  "runtime/polyglot-adapter"
  "runtime/polyglot-spring-boot-starter"
  "build-tools/polyglot-codegen"
  "build-tools/polyglot-codegen-maven-plugin"
)

print_section "Local Maven Central Publish Validation"

print_step "1/4 static file validation"
ruby -e 'require "yaml"; ARGV.each { |f| YAML.load_file(f); puts "ok #{f}" }' .github/workflows/*.yml .github/workflows/*.yaml
ruby -rrexml/document -e 'ARGV.each { |f| REXML::Document.new(File.read(f)); puts "ok #{f}" }' \
  pom.xml api/pom.xml runtime/pom.xml build-tools/pom.xml

print_step "2/4 effective Central Publishing configuration"
for module in "${MODULES[@]}"; do
  output="$EFFECTIVE_DIR/${module//\//__}.xml"
  print_info "Generating effective POM for $module"
  ./mvnw -B -ntp -s "$SETTINGS_FILE" -Prelease -pl "$module" help:effective-pom -Doutput="$output" >/dev/null

  python3 - "$module" "$output" <<'PY'
import sys
import xml.etree.ElementTree as ET

module = sys.argv[1]
path = sys.argv[2]
root = ET.parse(path).getroot()
ns = {"m": "http://maven.apache.org/POM/4.0.0"}

central = None
for plugin in root.findall("./m:build/m:plugins/m:plugin", ns):
    group_id = plugin.findtext("m:groupId", default="", namespaces=ns)
    artifact_id = plugin.findtext("m:artifactId", default="", namespaces=ns)
    if group_id == "org.sonatype.central" and artifact_id == "central-publishing-maven-plugin":
        central = plugin
        break

if central is None:
    print(f"ERROR: {module}: Central Publishing plugin is missing.", file=sys.stderr)
    sys.exit(1)

def text(xpath, default=""):
    return (central.findtext(xpath, default=default, namespaces=ns) or "").strip()

config = {
    "extensions": text("m:extensions"),
    "publishingServerId": text("m:configuration/m:publishingServerId"),
    "skipPublishing": text("m:configuration/m:skipPublishing"),
    "autoPublish": text("m:configuration/m:autoPublish"),
    "waitUntil": text("m:configuration/m:waitUntil"),
}

expected = {
    "extensions": "true",
    "publishingServerId": "central",
    "skipPublishing": "false",
    "autoPublish": "false",
}

for key, value in expected.items():
    if config[key] != value:
        print(f"ERROR: {module}: expected {key}={value}, got {config[key] or '<missing>'}.", file=sys.stderr)
        sys.exit(1)

if config["waitUntil"].lower() != "validated":
    print(f"ERROR: {module}: expected waitUntil=validated, got {config['waitUntil'] or '<missing>'}.", file=sys.stderr)
    sys.exit(1)

print(
    f"ok {module}: extensions={config['extensions']}, "
    f"publishingServerId={config['publishingServerId']}, "
    f"skipPublishing={config['skipPublishing']}, "
    f"autoPublish={config['autoPublish']}, "
    f"waitUntil={config['waitUntil']}"
)
PY
done

print_step "3/4 safe deploy lifecycle"
deploy_args=(
  -B
  -ntp
  -s "$SETTINGS_FILE"
  -Prelease,quality
  -DskipTests
  -DskipPublishing=true
  -DcentralSnapshotsUrl="file://$SNAPSHOT_REPO"
  clean
  deploy
)

if [ "$WITH_SIGNING" = "1" ]; then
  deploy_args=(-Dgpg.passphrase="${MAVEN_GPG_PASSPHRASE}" "${deploy_args[@]}")
  print_info "Signing validation enabled; Central publishing remains skipped."
else
  deploy_args=(-Dgpg.skip=true "${deploy_args[@]}")
  print_info "Signing validation disabled; using -Dgpg.skip=true."
fi

setup_java_home
setup_maven_opts
print_info "JDK: $(java -version 2>&1 | head -n 1)"
print_info "Maven deploy log: $DEPLOY_LOG"
./mvnw "${deploy_args[@]}" 2>&1 | tee "$DEPLOY_LOG"

print_step "4/4 upload guard"
if grep -E "deploymentId|Uploaded|Publishing deployment|Successfully published|central\\.sonatype\\.com" "$DEPLOY_LOG" >/dev/null; then
  echo "ERROR: Maven deploy log looks like it attempted a real Central upload or publish." >&2
  exit 1
fi

if ! grep -F "Installing Central Publishing features" "$DEPLOY_LOG" >/dev/null; then
  echo "ERROR: Maven deploy log did not show Central Publishing extension activation." >&2
  exit 1
fi

if ! grep -F "BUILD SUCCESS" "$DEPLOY_LOG" >/dev/null; then
  echo "ERROR: Maven deploy lifecycle did not complete successfully." >&2
  exit 1
fi

print_section "Validation Summary"
print_info "Checked modules:"
for module in "${MODULES[@]}"; do
  print_info "- $module"
done
print_info "Effective Central Publishing config is present and safe for all checked modules."
print_info "Safe deploy lifecycle passed with -DskipPublishing=true."
print_info "No Maven Central upload or publish attempt was detected."
