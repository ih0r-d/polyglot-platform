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
  local candidate sdkman_dir

  # Resolve installed candidates only: sourcing SDKMAN can switch JDKs or trigger network hooks.
  sdkman_dir="${SDKMAN_DIR:-$HOME/.sdkman}"
  if [ -f "$REPO_ROOT/.sdkmanrc" ]; then
    candidate="$(sed -n 's/^java=//p' "$REPO_ROOT/.sdkmanrc" | tr -d '\r')"
    if [ -n "$candidate" ] && [ -d "$sdkman_dir/candidates/java/$candidate" ]; then
      JAVA_HOME="$sdkman_dir/candidates/java/$candidate"
    fi
  fi
}

setup_java_home() {
  local java_bin link version_output version major links=0

  if [ -z "${JAVA_HOME:-}" ]; then
    setup_sdkman_env
    if [ -z "${JAVA_HOME:-}" ]; then
      java_bin="$(command -v java || true)"
      # Follow Linux alternatives and SDK symlinks without GNU-only readlink -f.
      while [ -n "$java_bin" ] && [ -L "$java_bin" ]; do
        links=$((links + 1))
        if [ "$links" -gt 40 ]; then
          echo "Cannot resolve java on PATH: too many symbolic links. Set JAVA_HOME explicitly." >&2
          return 1
        fi
        link="$(readlink "$java_bin")"
        case "$link" in
          /*) java_bin="$link" ;;
          *) java_bin="$(dirname "$java_bin")/$link" ;;
        esac
      done
      if [ -z "$java_bin" ] || [ "$java_bin" = /usr/bin/java ]; then
        echo "No JDK found (the macOS /usr/bin/java launcher is not a JDK). Set JAVA_HOME to a JDK or install the .sdkmanrc candidate manually." >&2
        return 1
      fi
      JAVA_HOME="$(cd "$(dirname "$java_bin")/.." && pwd -P)"
    fi
  fi

  if [ ! -x "$JAVA_HOME/bin/java" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
    echo "Invalid JAVA_HOME: $JAVA_HOME. Expected executable bin/java and bin/javac; the selected JDK will not be replaced." >&2
    return 1
  fi
  if ! version_output="$("$JAVA_HOME/bin/java" -version 2>&1)"; then
    printf 'Cannot run JAVA_HOME/bin/java: %s\n%s\n' "$JAVA_HOME" "$version_output" >&2
    return 1
  fi
  version="$(printf '%s\n' "$version_output" | sed -n 's/.*version "\([0-9][^"]*\)".*/\1/p' | head -n 1)"
  major="${version%%[.-]*}"
  case "$major" in
    ''|*[!0-9]*) echo "Cannot determine Java version from $JAVA_HOME/bin/java -version." >&2; return 1 ;;
  esac
  if [ "$major" -lt 21 ]; then
    echo "Java 21 or newer is required; selected Java $version at $JAVA_HOME. Set JAVA_HOME explicitly to a compatible JDK." >&2
    return 1
  fi
  export JAVA_HOME
  # Maven, sample plugins and JMH forks must all see the selected JDK.
  case "$PATH" in
    "$JAVA_HOME/bin":*) ;;
    *) export PATH="$JAVA_HOME/bin:$PATH" ;;
  esac
}

require_pinned_graalvm() {
  local expected graal_version java_version implementor

  setup_java_home || return 1
  expected="$(sed -n 's:.*<graal.version>\([^<]*\)</graal.version>.*:\1:p' "$REPO_ROOT/pom.xml")"
  if [ -z "$expected" ] || [ ! -r "$JAVA_HOME/release" ]; then
    echo "Cannot verify the pinned Oracle GraalVM: missing graal.version or JDK release file." >&2
    return 1
  fi
  graal_version="$(sed -n 's/^GRAALVM_VERSION="\([^"]*\)"/\1/p' "$JAVA_HOME/release")"
  java_version="$(sed -n 's/^JAVA_VERSION="\([^"]*\)"/\1/p' "$JAVA_HOME/release")"
  implementor="$(sed -n 's/^IMPLEMENTOR="\([^"]*\)"/\1/p' "$JAVA_HOME/release")"
  if [ "$graal_version" != "$expected" ] || [ "$java_version" != "$expected" ] || [ "$implementor" != "Oracle Corporation" ]; then
    echo "Benchmarks require Oracle GraalVM $expected (GRAALVM_VERSION and JAVA_VERSION in release). Selected: $JAVA_HOME ($implementor, Java $java_version, GraalVM ${graal_version:-absent})." >&2
    return 1
  fi
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
  setup_java_home || return 1
  setup_maven_opts
  print_info "JDK: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
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

sample_pom_files() {
  if [ -d "$REPO_ROOT/samples" ]; then
    find "$REPO_ROOT/samples" -type f -name pom.xml -print
  fi
}

update_sample_polyglot_versions() {
  local version
  local pom_file

  version=${1:-}
  if [ -z "$version" ]; then
    echo "VERSION required for update_sample_polyglot_versions" >&2
    return 1
  fi

  while IFS= read -r pom_file; do
    if grep -q "<polyglot.version>" "$pom_file"; then
      sed -i.bak \
        "s:<polyglot.version>[^<]*</polyglot.version>:<polyglot.version>$version</polyglot.version>:g" \
        "$pom_file"
      rm -f "$pom_file.bak"
    fi
  done < <(sample_pom_files)
}
