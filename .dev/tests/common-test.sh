#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

TEST_DIR="$(mktemp -d)"
TEST_DIR="$(cd "$TEST_DIR" && pwd -P)"
trap 'rm -rf "$TEST_DIR"' EXIT

make_jdk() {
  local location=$1 version=$2
  mkdir -p "$location/bin"
  cat > "$location/bin/java" <<EOF
#!/usr/bin/env bash
echo 'openjdk version "$version"' >&2
EOF
  printf '#!/usr/bin/env bash\nexit 0\n' > "$location/bin/javac"
  chmod +x "$location/bin/java" "$location/bin/javac"
}

assert_java_home() {
  [ "${JAVA_HOME:-}" = "$1" ] || { echo "Expected JAVA_HOME=$1, got ${JAVA_HOME:-<unset>}" >&2; exit 1; }
  [ "$(command -v java)" = "$1/bin/java" ] || { echo "PATH does not prefer JAVA_HOME" >&2; exit 1; }
}

expect_failure() {
  local expected=$1 output
  shift
  if output="$("$@" 2>&1)"; then
    echo "Expected failure: $*" >&2
    exit 1
  fi
  [[ "$output" == *"$expected"* ]] || { printf 'Missing error %s in: %s\n' "$expected" "$output" >&2; exit 1; }
}

make_jdk "$TEST_DIR/jdk with spaces" 25.0.4.1.1
make_jdk "$TEST_DIR/fallback" 21.0.2
make_jdk "$TEST_DIR/old" 17.0.12
make_jdk "$TEST_DIR/sdk/candidates/java/pinned" 25.0.4.1.1
mkdir -p "$TEST_DIR/sdk/bin" "$TEST_DIR/project"
# SDKMAN initialization must never run, including auto-env/install hooks.
printf 'echo "Unexpected SDKMAN initialization" >&2\nexit 97\n' > "$TEST_DIR/sdk/bin/sdkman-init.sh"
printf 'java=pinned\n' > "$TEST_DIR/project/.sdkmanrc"
printf '<graal.version>25.0.4.1.1</graal.version>\n' > "$TEST_DIR/project/pom.xml"
export SDKMAN_DIR="$TEST_DIR/sdk"
REPO_ROOT="$TEST_DIR/project"

(
  export JAVA_HOME="$TEST_DIR/jdk with spaces" PATH="/usr/bin:/bin:$PATH"
  setup_java_home
  assert_java_home "$TEST_DIR/jdk with spaces"
  selected_path="$PATH"
  setup_java_home
  [ "$PATH" = "$selected_path" ]
)

(
  # Installed SDKMAN candidate; no shell initialization or catalog lookup needed.
  unset JAVA_HOME
  setup_java_home
  assert_java_home "$TEST_DIR/sdk/candidates/java/pinned"
)

for sdk_dir in "$TEST_DIR/no-sdkman" "$TEST_DIR/sdk-with-missing-candidate"; do
  (
    unset JAVA_HOME
    export SDKMAN_DIR="$sdk_dir" PATH="$TEST_DIR/fallback/bin:/usr/bin:/bin"
    setup_java_home
    assert_java_home "$TEST_DIR/fallback"
  )
done

(
  # Missing SDKMAN candidate must not affect a valid explicit JDK.
  export SDKMAN_DIR="$TEST_DIR/no-sdkman" JAVA_HOME="$TEST_DIR/jdk with spaces"
  setup_java_home
  assert_java_home "$TEST_DIR/jdk with spaces"
)

mkdir -p "$TEST_DIR/jre/bin"
cp "$TEST_DIR/fallback/bin/java" "$TEST_DIR/jre/bin/java"
make_jdk "$TEST_DIR/non-executable" 25
chmod -x "$TEST_DIR/non-executable/bin/java"
for invalid_home in "$TEST_DIR/missing" "$TEST_DIR/jre" "$TEST_DIR/project" "$TEST_DIR/non-executable"; do
  (
    export JAVA_HOME="$invalid_home" PATH="$TEST_DIR/fallback/bin:/usr/bin:/bin"
    expect_failure "Invalid JAVA_HOME" setup_java_home
    [ "$JAVA_HOME" = "$invalid_home" ]
  )
done

(
  export JAVA_HOME="$TEST_DIR/old"
  expect_failure "Java 21 or newer is required" setup_java_home
  [ "$JAVA_HOME" = "$TEST_DIR/old" ]
)

(
  make_jdk "$TEST_DIR/broken" 25
  printf '#!/usr/bin/env bash\nexit 1\n' > "$TEST_DIR/broken/bin/java"
  export JAVA_HOME="$TEST_DIR/broken"
  expect_failure "Cannot run JAVA_HOME/bin/java" setup_java_home
)

(
  make_jdk "$TEST_DIR/unrecognized" unknown
  export JAVA_HOME="$TEST_DIR/unrecognized"
  expect_failure "Cannot determine Java version" setup_java_home
)

(
  # Resolve Linux alternatives and relative symlinks without readlink -f.
  unset JAVA_HOME
  export SDKMAN_DIR="$TEST_DIR/no-sdkman"
  mkdir -p "$TEST_DIR/links/bin" "$TEST_DIR/links/alternatives"
  ln -s ../alternatives/java "$TEST_DIR/links/bin/java"
  ln -s "$TEST_DIR/fallback/bin/java" "$TEST_DIR/links/alternatives/java"
  export PATH="$TEST_DIR/links/bin:/usr/bin:/bin"
  setup_java_home
  assert_java_home "$TEST_DIR/fallback"
)

(
  unset JAVA_HOME
  export SDKMAN_DIR="$TEST_DIR/no-sdkman"
  ln -s loop-b "$TEST_DIR/loop-a"
  ln -s loop-a "$TEST_DIR/loop-b"
  command() { if [ "$*" = "-v java" ]; then printf '%s/loop-a\n' "$TEST_DIR"; else builtin command "$@"; fi; }
  expect_failure "too many symbolic links" setup_java_home
)

(
  unset JAVA_HOME
  export SDKMAN_DIR="$TEST_DIR/no-sdkman"
  command() { if [ "$*" = "-v java" ]; then printf '/usr/bin/java\n'; else builtin command "$@"; fi; }
  expect_failure "macOS /usr/bin/java launcher" setup_java_home
)

(
  unset JAVA_HOME
  export SDKMAN_DIR="$TEST_DIR/no-sdkman"
  command() { if [ "$*" = "-v java" ]; then return 1; else builtin command "$@"; fi; }
  expect_failure "No JDK found" setup_java_home
)

(
  export JAVA_HOME="$TEST_DIR/jdk with spaces"
  cat > "$JAVA_HOME/release" <<'EOF'
IMPLEMENTOR="Oracle Corporation"
JAVA_VERSION="25.0.4.1.1"
GRAALVM_VERSION="25.0.4.1.1"
EOF
  require_pinned_graalvm
  sed 's/25.0.4.1.1/25.0.0/g' "$JAVA_HOME/release" > "$TEST_DIR/old-release"
  cp "$TEST_DIR/old-release" "$JAVA_HOME/release"
  expect_failure "Benchmarks require Oracle GraalVM 25.0.4.1.1" require_pinned_graalvm
  # Matching numbers alone must not accept a different vendor.
  printf 'IMPLEMENTOR="GraalVM Community"\nJAVA_VERSION="25.0.4.1.1"\nGRAALVM_VERSION="25.0.4.1.1"\n' > "$JAVA_HOME/release"
  expect_failure "Benchmarks require Oracle GraalVM" require_pinned_graalvm
  printf 'IMPLEMENTOR="Oracle Corporation"\nJAVA_VERSION="25"\nGRAALVM_VERSION="25.0.4.1.1"\n' > "$JAVA_HOME/release"
  expect_failure "Benchmarks require Oracle GraalVM" require_pinned_graalvm
)

(
  export JAVA_HOME="$TEST_DIR/fallback"
  expect_failure "missing graal.version or JDK release file" require_pinned_graalvm
)

(
  # Maven and child java processes see the same explicitly selected JDK.
  export JAVA_HOME="$TEST_DIR/jdk with spaces" PATH="/usr/bin:/bin:$PATH"
  cat > "$REPO_ROOT/mvnw" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[ "$JAVA_HOME" = "$PWD/../jdk with spaces" ]
[ "$(command -v java)" = "$JAVA_HOME/bin/java" ]
[ "$*" = "-B -ntp verify" ]
echo "test Maven"
EOF
  chmod +x "$REPO_ROOT/mvnw"
  # Keep the path spelling used by the Maven stub.
  export JAVA_HOME="$REPO_ROOT/../jdk with spaces"
  output="$(run_mvn verify)"
  [[ "$output" == *'JDK: openjdk version "25.0.4.1.1"'* ]]
  [[ "$output" == *"test Maven"* ]]
  export JAVA_HOME="$TEST_DIR/missing"
  expect_failure "Invalid JAVA_HOME" run_mvn verify
)

echo "[PASS] JDK resolution, SDKMAN fallback, version checks and Maven launcher regression tests"
