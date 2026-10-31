#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT
TEST_COUNT=0

# Exercise real entry points in disposable repositories without Python downloads or Maven.
new_fixture() {
  TEST_COUNT=$((TEST_COUNT + 1))
  FIXTURE="$TEST_DIR/case $TEST_COUNT"
  mkdir -p "$FIXTURE/.dev/bin" "$FIXTURE/.dev/lib" "$FIXTURE/mock-bin"
  cp "$REPO_ROOT"/.dev/bin/docs-{build,serve}.sh "$FIXTURE/.dev/bin/"
  cp "$REPO_ROOT/.dev/bin/release-preflight.sh" "$FIXTURE/.dev/bin/"
  cp "$REPO_ROOT/.dev/lib/"{common,docs}.sh "$FIXTURE/.dev/lib/"
  cat >> "$FIXTURE/.dev/lib/common.sh" <<'MOCK'
run_mvn() { printf 'maven %s\n' "$*" >> "$DOCS_TEST_LOG"; }
MOCK
  printf '#!/usr/bin/env bash\nexit 0\n' > "$FIXTURE/.dev/bin/validate-sample-versions.sh"
  chmod +x "$FIXTURE/.dev/bin/validate-sample-versions.sh"
  mkdir -p "$FIXTURE/samples/"{java-maven-example,java-maven-codegen-example,spring-boot-example}
  export DOCS_TEST_LOG="$FIXTURE/commands.log"
  : > "$DOCS_TEST_LOG"
  export PATH="$FIXTURE/mock-bin:/usr/bin:/bin"
  unset MOCK_FAILURE RELEASE_PREFLIGHT_CLEAN_DOCS_VENV VIRTUAL_ENV
  cat > "$FIXTURE/mock-bin/python3" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf 'python3 %s\n' "$*" >> "$DOCS_TEST_LOG"
[ "${MOCK_FAILURE:-}" != venv ] || exit 41
[ "$1 $2" = '-m venv' ]
mkdir -p "$3/bin"
cp "$(dirname "$0")/python" "$3/bin/python"
printf 'export PATH=%q/bin:$PATH\n' "$3" > "$3/bin/activate"
MOCK
  cat > "$FIXTURE/mock-bin/python" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf 'python %s\n' "$*" >> "$DOCS_TEST_LOG"
if [ "$2" = pip ]; then
  [ "${MOCK_FAILURE:-}" != pip ] || exit 42
  if [ "$*" = '-m pip install mkdocs mkdocs-material' ]; then
    printf '#!/usr/bin/env bash\nexit 0\n' > "$(dirname "$0")/mkdocs"
    chmod +x "$(dirname "$0")/mkdocs"
  fi
fi
if [ "$2" = mkdocs ] && [ "${MOCK_FAILURE:-}" = build ]; then
  exit 43
fi
MOCK
  cat > "$FIXTURE/mock-bin/mvn" <<'MOCK'
#!/usr/bin/env bash
printf 'maven %s\n' "$*" >> "$DOCS_TEST_LOG"
MOCK
  chmod +x "$FIXTURE/mock-bin/"*
}

run_script() {
  bash "$FIXTURE/.dev/bin/$1" "${@:2}" > "$FIXTURE/output.log" 2>&1
}

assert_logged() {
  if ! /usr/bin/grep -F -- "$1" "$DOCS_TEST_LOG" > /dev/null; then
    printf 'Missing command: %s\n' "$1" >&2
    cat "$FIXTURE/output.log" >&2
    exit 1
  fi
}

assert_not_logged() {
  if /usr/bin/grep -F -- "$1" "$DOCS_TEST_LOG" > /dev/null; then
    printf 'Unexpected command: %s\n' "$1" >&2
    exit 1
  fi
}

prepare_existing_env() {
  python3 -m venv "$FIXTURE/.local/venv-docs"
  : > "$DOCS_TEST_LOG"
}

new_fixture
run_script docs-build.sh --site-dir 'custom output'
assert_logged "python3 -m venv $FIXTURE/.local/venv-docs"
assert_logged 'python -m pip install mkdocs mkdocs-material'
assert_logged 'python -m mkdocs build --strict --site-dir custom output'
[ -x "$FIXTURE/.local/venv-docs/bin/mkdocs" ]

new_fixture
prepare_existing_env
printf '#!/usr/bin/env bash\nexit 0\n' > "$FIXTURE/.local/venv-docs/bin/mkdocs"
chmod +x "$FIXTURE/.local/venv-docs/bin/mkdocs"
run_script docs-build.sh
assert_not_logged 'python3 -m venv'
assert_not_logged 'python -m pip'
assert_logged 'python -m mkdocs build --strict'

new_fixture
prepare_existing_env
run_script docs-build.sh
assert_not_logged 'python3 -m venv'
assert_logged 'python -m pip install mkdocs mkdocs-material'

new_fixture
mkdir -p "$FIXTURE/site"
run_script docs-serve.sh --dev-addr 127.0.0.1:8001
assert_logged 'python -m mkdocs serve --dev-addr 127.0.0.1:8001'
[ ! -d "$FIXTURE/site" ]

for entry_point in docs-build.sh docs-serve.sh; do
  new_fixture
  cat > "$FIXTURE/mock-bin/mkdocs" <<'MOCK'
#!/usr/bin/env bash
printf 'mkdocs %s\n' "$*" >> "$DOCS_TEST_LOG"
MOCK
  chmod +x "$FIXTURE/mock-bin/mkdocs"
  run_script "$entry_point" --verbose
  assert_not_logged 'python3'
  assert_not_logged 'python -m pip'
  assert_logged 'mkdocs '
done

for failure in venv pip build; do
  new_fixture
  export MOCK_FAILURE="$failure"
  if run_script docs-build.sh; then
    printf 'Expected %s failure to propagate\n' "$failure" >&2
    exit 1
  fi
  case "$failure" in
    venv) assert_not_logged 'python -m pip' ;;
    pip) assert_not_logged 'python -m mkdocs' ;;
    build) assert_logged 'python -m mkdocs build --strict' ;;
  esac
done

new_fixture
prepare_existing_env
run_script release-preflight.sh
assert_logged 'python -m pip install --upgrade pip'
assert_logged 'python -m pip install mkdocs mkdocs-material'
assert_logged 'python -m mkdocs build --strict'
[ -d "$FIXTURE/.local/venv-docs" ]
assert_not_logged deploy

for failure in none build; do
  new_fixture
  mkdir -p "$FIXTURE/.venv-docs" "$FIXTURE/docs/.venv" "$FIXTURE/.local/planning"
  printf 'keep\n' > "$FIXTURE/.local/planning/private.md"
  export RELEASE_PREFLIGHT_CLEAN_DOCS_VENV=1
  if [ "$failure" = build ]; then
    export MOCK_FAILURE=build
    if run_script release-preflight.sh; then
      echo 'Expected preflight docs failure' >&2
      exit 1
    fi
    assert_not_logged 'maven -DskipTests install'
  else
    run_script release-preflight.sh
  fi
  [ ! -d "$FIXTURE/.local/venv-docs" ]
  [ -d "$FIXTURE/.venv-docs" ]
  [ -d "$FIXTURE/docs/.venv" ]
  [ "$(cat "$FIXTURE/.local/planning/private.md")" = keep ]
done

printf 'Docs tooling tests: %s scenarios passed\n' "$TEST_COUNT"
