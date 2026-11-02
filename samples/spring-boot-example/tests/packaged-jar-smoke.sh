#!/usr/bin/env bash
set -euo pipefail

SMOKE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SMOKE_DIR/../../.dev/lib/common.sh"
setup_java_home
JAR=${1:-"$SMOKE_DIR/target/polyglot-springboot-example-0.0.1-SNAPSHOT.jar"}
[ -f "$JAR" ] || { echo "Build the sample JAR first: $JAR" >&2; exit 1; }
WORKSPACE=$(mktemp -d)
APP_PID=
LOG="$SMOKE_DIR/target/packaged-jar-smoke.log"
mkdir -p "$WORKSPACE/bin" "$WORKSPACE/tmp" "$WORKSPACE/run" "$SMOKE_DIR/target"
: > "$LOG"
cp "$JAR" "$WORKSPACE/application.jar"
ln -s "$JAVA_HOME/bin/java" "$WORKSPACE/bin/java"

cleanup() {
  local result=$?
  trap - EXIT
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill -TERM "$APP_PID" 2>/dev/null || true
    for attempt in {1..50}; do
      kill -0 "$APP_PID" 2>/dev/null || break
      sleep 0.2
    done
    if kill -0 "$APP_PID" 2>/dev/null; then kill -KILL "$APP_PID" 2>/dev/null || true; fi
    wait "$APP_PID" 2>/dev/null || true
  fi
  if [ "$result" -ne 0 ]; then tail -n 60 "$LOG" >&2; fi
  rm -rf "$WORKSPACE"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"$JAVA_HOME/bin/jar" tf "$WORKSPACE/application.jar" > "$WORKSPACE/entries"
grep -q '^BOOT-INF/classes/python/analytics_api.py$' "$WORKSPACE/entries"
grep -q '^BOOT-INF/classes/org.graalvm.python.vfs/venv/.*numpy/__init__.py$' "$WORKSPACE/entries"
# Only Java is on the child PATH; no system python, python3 or pip can be discovered there.
(
  cd "$WORKSPACE/run"
  exec env -u PYTHONHOME -u PYTHONPATH -u VIRTUAL_ENV PATH="$WORKSPACE/bin" \
    "$JAVA_HOME/bin/java" --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
    "-Djava.io.tmpdir=$WORKSPACE/tmp" -jar "$WORKSPACE/application.jar" \
    --spring.profiles.active=packaged-smoke --server.address=127.0.0.1 --server.port=0 \
    --spring.main.banner-mode=off --polyglot.core.log-metadata-on-startup=false \
    --management.endpoints.web.exposure.include=health --management.endpoint.health.probes.enabled=true \
    --management.endpoint.health.show-details=never
) > "$LOG" 2>&1 &
APP_PID=$!
PORT=
for attempt in {1..120}; do
  kill -0 "$APP_PID" 2>/dev/null || { echo 'Application exited before readiness' >&2; exit 1; }
  PORT=$(sed -n 's/.*Tomcat started on port \([0-9][0-9]*\).*/\1/p' "$LOG" | tail -n 1)
  if [ -n "$PORT" ] && curl -fsS --max-time 2 "http://127.0.0.1:$PORT/actuator/health/readiness" > /dev/null 2>&1; then break; fi
  sleep 1
done
[ -n "$PORT" ] || { echo 'Startup timed out' >&2; exit 1; }
curl -fsS --max-time 5 "http://127.0.0.1:$PORT/actuator/health/readiness" > /dev/null
RESPONSE=$(curl -fsS --max-time 60 -H 'Content-Type: application/json' \
  --data '{"samples":[10,10,10,10,100]}' "http://127.0.0.1:$PORT/analytics/summary")
printf '%s' "$RESPONSE" | grep -Eq '"mean"[[:space:]]*:[[:space:]]*28(\.0)?[,}]'
printf '%s' "$RESPONSE" | grep -Eq '"count"[[:space:]]*:[[:space:]]*5[,}]'
printf '%s' "$RESPONSE" | grep -Eq '"numpyVersion"[[:space:]]*:[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"'
# Native NumPy libraries require real extraction. Only VFS-owned temp directories are checked.
find "$WORKSPACE/tmp" -type f -path '*/numpy/*' -name '*.so' > "$WORKSPACE/native-files"
[ -s "$WORKSPACE/native-files" ] || { echo 'No extracted NumPy/native resources observed' >&2; exit 1; }
kill -TERM "$APP_PID"
for attempt in {1..60}; do
  kill -0 "$APP_PID" 2>/dev/null || break
  sleep 1
done
if kill -0 "$APP_PID" 2>/dev/null; then echo 'Graceful shutdown timed out' >&2; exit 1; fi
STATUS=0
wait "$APP_PID" || STATUS=$?
APP_PID=
case "$STATUS" in 0|143) ;; *) echo "Unexpected native/JVM exit status: $STATUS" >&2; exit 1 ;; esac
find "$WORKSPACE/tmp" -type d -name 'org.graalvm.python.vfsx*' > "$WORKSPACE/remaining-vfs"
[ ! -s "$WORKSPACE/remaining-vfs" ] || { echo 'VFS extraction remained after JVM shutdown' >&2; exit 1; }
printf '[PASS] Copied executable JAR, isolated working directory, Python-free PATH, NumPy mean=28, graceful shutdown and VFS extraction cleanup\n'
