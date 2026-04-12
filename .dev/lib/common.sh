#!/usr/bin/env bash
set -euo pipefail

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_DIR="$(cd "$LIB_DIR/.." && pwd)"
REPO_ROOT="$(cd "$DEV_DIR/.." && pwd)"

if [ -t 1 ]; then
  C_RESET="$(printf '\033[0m')"
  C_BOLD="$(printf '\033[1m')"
  C_BLUE="$(printf '\033[34m')"
  C_GREEN="$(printf '\033[32m')"
  C_YELLOW="$(printf '\033[33m')"
else
  C_RESET=""
  C_BOLD=""
  C_BLUE=""
  C_GREEN=""
  C_YELLOW=""
fi

print_section() {
  printf '\n%s%s== %s ==%s\n' "$C_BOLD" "$C_BLUE" "$1" "$C_RESET"
}

print_step() {
  printf '%s[STEP]%s %s\n' "$C_BLUE" "$C_RESET" "$1"
}

print_info() {
  printf '%s[INFO]%s %s\n' "$C_GREEN" "$C_RESET" "$1"
}

print_warn() {
  printf '%s[WARN]%s %s\n' "$C_YELLOW" "$C_RESET" "$1"
}

setup_sdkman_env() {
  cd_repo_root

  if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    # SDKMAN scripts are not strict-nounset safe, so run both init and `sdk env` with nounset disabled.
    # shellcheck disable=SC1090
    set +u
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    if command -v sdk >/dev/null 2>&1 && [ -f "$REPO_ROOT/.sdkmanrc" ]; then
      sdk env
    fi
    set -u
  fi
}

setup_java_home() {
  local java_bin

  setup_sdkman_env
  java_bin="$(command -v java)"
  export JAVA_HOME
  JAVA_HOME="$(cd "$(dirname "$java_bin")/.." && pwd -P)"
}

setup_maven_opts() {
  local extra_opts

  extra_opts="--enable-native-access=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -XX:+IgnoreUnrecognizedVMOptions"
  if [ -n "${MAVEN_OPTS:-}" ]; then
    export MAVEN_OPTS="$extra_opts ${MAVEN_OPTS}"
  else
    export MAVEN_OPTS="$extra_opts"
  fi
}

cd_repo_root() {
  cd "$REPO_ROOT"
}

run_mvn() {
  cd_repo_root
  setup_java_home
  setup_maven_opts
  print_info "JDK: $(java -version 2>&1 | head -n 1)"
  print_info "Maven: ./mvnw -B -ntp $*"
  ./mvnw -B -ntp "$@"
}

project_pom_files() {
  local dir

  printf '%s\n' "$REPO_ROOT/pom.xml"
  for dir in api runtime build-tools; do
    if [ -d "$REPO_ROOT/$dir" ]; then
      find "$REPO_ROOT/$dir" -type f -name pom.xml -print
    fi
  done
}
