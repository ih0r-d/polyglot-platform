#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

TEST_DIR="$(mktemp -d)"
TEST_DIR="$(cd "$TEST_DIR" && pwd -P)"
trap 'rm -rf "$TEST_DIR"' EXIT

mkdir -p "$TEST_DIR/jdk with spaces/bin" "$TEST_DIR/fallback/bin" "$TEST_DIR/invalid/bin"
printf '#!/usr/bin/env bash\necho "test JDK"\n' > "$TEST_DIR/jdk with spaces/bin/java"
printf '#!/usr/bin/env bash\nexit 1\n' > "$TEST_DIR/fallback/bin/java"
cp "$TEST_DIR/fallback/bin/java" "$TEST_DIR/invalid/bin/java"
chmod +x "$TEST_DIR/jdk with spaces/bin/java" "$TEST_DIR/fallback/bin/java"

# Isolate Java detection from the developer's installed SDKMAN candidates.
setup_sdkman_env() { :; }

assert_java_home() {
  if [ "${JAVA_HOME:-}" != "$1" ]; then
    printf 'Expected JAVA_HOME=%s, got %s\n' "$1" "${JAVA_HOME:-<unset>}" >&2
    exit 1
  fi
}

(
  export JAVA_HOME="$TEST_DIR/jdk with spaces"
  export PATH="/usr/bin:/bin:$PATH"
  setup_java_home
  assert_java_home "$TEST_DIR/jdk with spaces"
)

(
  unset JAVA_HOME
  export PATH="$TEST_DIR/fallback/bin:/usr/bin:/bin"
  setup_java_home
  assert_java_home "$TEST_DIR/fallback"
)

for invalid_home in "$TEST_DIR/missing" "$TEST_DIR/invalid" ""; do
  (
    export JAVA_HOME="$invalid_home"
    export PATH="$TEST_DIR/fallback/bin:/usr/bin:/bin"
    setup_java_home
    assert_java_home "$TEST_DIR/fallback"
  )
done

(
  # SDKMAN may supply JAVA_HOME without putting its java first in PATH.
  unset JAVA_HOME
  setup_sdkman_env() { JAVA_HOME="$TEST_DIR/jdk with spaces"; }
  export PATH="/usr/bin:/bin:$PATH"
  setup_java_home
  assert_java_home "$TEST_DIR/jdk with spaces"
)

(
  # Exercise the version banner and Maven invocation without launching system Java.
  export JAVA_HOME="$TEST_DIR/jdk with spaces"
  export PATH="/usr/bin:/bin:$PATH"
  REPO_ROOT="$TEST_DIR"
  cat > "$TEST_DIR/mvnw" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[ "$JAVA_HOME" = "$PWD/jdk with spaces" ]
[ "$*" = "-B -ntp verify" ]
echo "test Maven"
EOF
  chmod +x "$TEST_DIR/mvnw"
  output="$(run_mvn verify)"
  [[ "$output" == *"JDK: test JDK"* ]]
  [[ "$output" == *"test Maven"* ]]
)

echo "[PASS] Java home detection and Maven launcher regression tests"
