#!/usr/bin/env bash
set -eo pipefail

export SDKMAN_DIR="$HOME/.sdkman"
source "$SDKMAN_DIR/bin/sdkman-init.sh"

log() {
  echo "[INFO] $1"
}

build_module () {
  local DIR="$1"

  log "Building module: $DIR"
  pushd "$DIR" >/dev/null

  sdk env >/dev/null

  log "JDK: $(java -version 2>&1 | head -n 1)"

  if ../mvnw -q -ntp clean install -Djacoco.skip=true  ; then
    echo "[OK] $DIR build successful"
  else
    echo "[FAIL] $DIR build failed"
    exit 1
  fi

  popd >/dev/null
}

echo "========================================"
echo " Polyglot Platform Build"
echo "========================================"

build_module adapter
build_module tooling

echo "========================================"
echo "[OK] Platform build completed"
echo "========================================"