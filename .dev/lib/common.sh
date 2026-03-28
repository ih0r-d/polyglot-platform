#!/usr/bin/env bash
set -euo pipefail

DEV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DEV_DIR/.." && pwd)"

setup_java_home() {
  local java_bin

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
