#!/usr/bin/env bash
# A caller may explicitly use sh, which ignores the shebang and disables Bash syntax.
if [ -z "${BASH_VERSION:-}" ]; then
  exec bash -- "$0" "$@"
fi
# macOS sh is Bash in POSIX mode; enable its normal parser before reading functions.
set +o posix
set -euo pipefail
umask 077
SAMPLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY="$(cd "$SAMPLE_DIR/../.." && pwd)"
STATE="$SAMPLE_DIR/.local/showcase"
JAR="$SAMPLE_DIR/target/polyglot-springboot-example-0.0.1-SNAPSHOT.jar"
HOST=${DEMO_HOST:-127.0.0.1}
PORT=${DEMO_PORT:-8080}
REQUESTS=${DEMO_REQUESTS:-10}
ERRORS=${DEMO_ERRORS:-2}
CONCURRENCY=${DEMO_CONCURRENCY:-1}
SIZE=${DEMO_SIZE:-1000}
METRICS=${DEMO_METRICS:-true}
TIMEOUT=${DEMO_TIMEOUT:-120}
PID= TOKEN= RUN_PORT= STARTED=

usage() {
  cat <<'HELP'
Embedded Python Analytics Showcase
Usage: ./demo.sh COMMAND [options]
Commands: help start stop restart status smoke traffic metrics health console logs demo
Options: --port N --host 127.0.0.1|localhost --requests N --errors N
         --concurrency N (1..16) --size N (1..100000) --metrics true|false
         --timeout N (startup seconds, default 120) --help
Environment: DEMO_HOST, DEMO_PORT, DEMO_REQUESTS, DEMO_ERRORS, DEMO_CONCURRENCY,
             DEMO_SIZE, DEMO_METRICS, DEMO_TIMEOUT. NO_COLOR is respected (plain output).
Prerequisites: JDK 25+ selected via JAVA_HOME/installed SDKMAN/PATH, Bash, curl.
First start builds current library/sample with the root Maven Wrapper; Maven/Python
package downloads need network access once, then use the local caches and graalpy.lock.
No jq, external Python, Docker or external services are required.
Examples:
  ./demo.sh demo
  ./demo.sh traffic --requests 20 --errors 3 --concurrency 4 --size 1000
  ./demo.sh restart --metrics false
  ./demo.sh stop
The managed service remains running after demo for inspection. Logs/PID are private
under .local/showcase/. Only a matching owner token, PID and process start time can be stopped.
HELP
}
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
COMMAND=${1:-help}
if [ "$COMMAND" = help ] || [ "$COMMAND" = --help ] || [ "$COMMAND" = -h ]; then usage; exit 0; fi
shift
case "$COMMAND" in start|stop|restart|status|smoke|traffic|metrics|health|console|logs|demo) ;; *) usage >&2; fail "Unknown command: $COMMAND" ;; esac
while [ "$#" -gt 0 ]; do
  case "$1" in
    --help|-h) usage; exit 0 ;;
    --port|--host|--requests|--errors|--concurrency|--size|--metrics|--timeout)
      [ "$#" -ge 2 ] || fail "Missing value for $1"
      case "$1" in
        --port) PORT=$2 ;; --host) HOST=$2 ;; --requests) REQUESTS=$2 ;; --errors) ERRORS=$2 ;;
        --concurrency) CONCURRENCY=$2 ;; --size) SIZE=$2 ;; --metrics) METRICS=$2 ;; --timeout) TIMEOUT=$2 ;;
      esac
      shift 2 ;;
    *) fail "Unknown option: $1" ;;
  esac
done
number() {
  [[ "$2" =~ ^[0-9]{1,6}$ ]] || fail "$1 must be an integer in $3..$4"
  local value=$((10#$2))
  [ "$value" -ge "$3" ] && [ "$value" -le "$4" ] || fail "$1 must be in $3..$4"
}
number port "$PORT" 1 65535
number requests "$REQUESTS" 0 10000
number errors "$ERRORS" 0 10000
[ "$((10#$REQUESTS + 10#$ERRORS))" -le 10000 ] || fail "Total requests/errors must be <= 10000"
number concurrency "$CONCURRENCY" 1 16
number size "$SIZE" 1 100000
number timeout "$TIMEOUT" 1 3600
case "$HOST" in 127.0.0.1|localhost) ;; *) fail "The demo binds to loopback; host must be 127.0.0.1 or localhost" ;; esac
case "$METRICS" in true|false) ;; *) fail "metrics must be true or false" ;; esac
PORT=$((10#$PORT)); REQUESTS=$((10#$REQUESTS)); ERRORS=$((10#$ERRORS))
CONCURRENCY=$((10#$CONCURRENCY)); SIZE=$((10#$SIZE)); TIMEOUT=$((10#$TIMEOUT))
BASE="http://$HOST:$PORT"
[ ! -L "$STATE" ] || fail "Refusing symlink state directory: $STATE"
mkdir -p "$STATE"
[ ! -L "$STATE/pid" ] || fail "Refusing symlink PID file"

read_state() {
  PID= TOKEN= RUN_PORT= STARTED=
  if [ -f "$STATE/pid" ]; then IFS='|' read -r PID TOKEN RUN_PORT STARTED < "$STATE/pid" || return 1; fi
}
owned() {
  [[ "$PID" =~ ^[0-9]+$ && "$TOKEN" =~ ^run\.[a-zA-Z0-9]+$ ]] || return 1
  local args actual_start
  args=$(ps -p "$PID" -o args= 2>/dev/null) || return 1
  actual_start=$(ps -p "$PID" -o lstart= 2>/dev/null | sed 's/^ *//') || return 1
  [[ "$args" == *"-Dshowcase.owner=$TOKEN "* && "$args" == *"$JAR"* && "$actual_start" = "$STARTED" ]]
}
java_setup() {
  source "$REPOSITORY/.dev/lib/common.sh"
  setup_java_home
  local version major
  version=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)
  major=$(printf '%s\n' "$version" | sed -n 's/.*version "\([0-9]*\).*/\1/p')
  [ "${major:-0}" -ge 25 ] || fail "This sample compiles for Java 25. Select JDK 25+ using JAVA_HOME; selected: $version"
  JAVA="$JAVA_HOME/bin/java"
  printf 'JVM: %s\n' "$version"
  if [ ! -f "$JAVA_HOME/release" ] || ! verified_graal_release; then
    printf 'NOTE: exact Oracle GraalVM 25.0.4.1.1 is not verified; JVM/interpreter mode is allowed, performance/native support is not implied.\n'
  fi
}
verified_graal_release() { # POSIX tools only; no ripgrep prerequisite.
  [ "$(sed -n 's/^GRAALVM_VERSION="\([^"]*\)"/\1/p' "$JAVA_HOME/release")" = 25.0.4.1.1 ] &&
    [ "$(sed -n 's/^JAVA_VERSION="\([^"]*\)"/\1/p' "$JAVA_HOME/release")" = 25.0.4.1.1 ] &&
    [ "$(sed -n 's/^IMPLEMENTOR="\([^"]*\)"/\1/p' "$JAVA_HOME/release")" = 'Oracle Corporation' ]
}
build() {
  local revision
  revision=$(git -C "$REPOSITORY" rev-parse HEAD)
  if [ ! -f "$JAR" ] || [ ! -f "$STATE/built-head" ] || [ "$(cat "$STATE/built-head")" != "$revision" ] ||
      [ -n "$(find "$SAMPLE_DIR/src" "$SAMPLE_DIR/pom.xml" "$SAMPLE_DIR/graalpy.lock" -type f -newer "$JAR" -print -quit)" ]; then
    printf '=== Building current library and locked Python application ===\n'
    if ! (cd "$REPOSITORY" &&
        ./mvnw -B -ntp -pl runtime/polyglot-spring-boot-starter -am install -DskipTests &&
        ./mvnw -B -ntp -f "$SAMPLE_DIR/pom.xml" package -DskipTests) > "$STATE/build.log" 2>&1; then
      tail -n 40 "$STATE/build.log" >&2
      fail "Build failed; full diagnostics: $STATE/build.log"
    fi
    printf '%s\n' "$revision" > "$STATE/built-head"
  fi
}
stop_owned() {
  read_state
  if ! owned; then
    [ ! -f "$STATE/pid" ] || fail "PID ownership could not be verified; refusing to signal any process. Inspect $STATE/pid"
    printf 'No managed sample is running.\n'; return
  fi
  printf 'Stopping managed sample PID %s...\n' "$PID"
  kill -TERM "$PID"
  local deadline=$((SECONDS + 30))
  while owned; do
    [ "$SECONDS" -lt "$deadline" ] || fail "Shutdown timed out; retained PID state. Inspect logs and retry stop."
    sleep 0.2
  done
  rm -f "$STATE/pid"
  printf 'Stopped.\n'
}
start() {
  read_state
  if owned; then
    [ "$RUN_PORT" = "$PORT" ] || fail "Already running on port $RUN_PORT; stop it before changing port"
    printf 'Already running: PID %s at %s\n' "$PID" "$BASE"; return
  fi
  [ ! -f "$STATE/pid" ] || fail "Stale/unverified PID state; inspect $STATE/pid before removing it"
  command -v curl >/dev/null || fail "curl is required"
  if curl -s --max-time 2 "$BASE/actuator/health" >/dev/null; then fail "Port $PORT already serves HTTP; choose --port"; fi
  java_setup
  build
  local directory deadline
  directory=$(mktemp -d "$STATE/run.XXXXXXXX")
  TOKEN=${directory##*/}
  nohup "$JAVA" --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
    "-Dshowcase.owner=$TOKEN" -jar "$JAR" --spring.profiles.active=observability,console \
    "--server.address=$HOST" "--server.port=$PORT" "--polyglot.metrics.enabled=$METRICS" \
    > "$directory/app.log" 2>&1 < /dev/null &
  PID=$!
  STARTED=$(ps -p "$PID" -o lstart= | sed 's/^ *//')
  printf '%s|%s|%s|%s\n' "$PID" "$TOKEN" "$PORT" "$STARTED" > "$STATE/pid"
  deadline=$((SECONDS + TIMEOUT))
  until curl -fsS --max-time 2 "$BASE/actuator/health/readiness" >/dev/null 2>&1; do
    if ! owned; then
      tail -n 30 "$directory/app.log" >&2
      if ! kill -0 "$PID" 2>/dev/null; then rm -f "$STATE/pid"; fi
      fail "Application exited; inspect $directory/app.log"
    fi
    if [ "$SECONDS" -ge "$deadline" ]; then stop_owned; fail "Readiness timed out; inspect $directory/app.log"; fi
    sleep 0.2
  done
  printf 'Ready: %s (PID %s)\nLog: %s/app.log\n' "$BASE" "$PID" "$directory"
  printf 'Actuator: %s/actuator\nInvocation metrics: %s/actuator/metrics/polyglot.client.invocations\n' "$BASE" "$BASE"
  printf 'Experimental console: %s/polyglot-console/\n' "$BASE"
}
# These readers inspect known numeric fields in this sample's JSON, not arbitrary JSON documents.
http() {
  local path=$1 expected=$2 payload=${3:-} response status body
  if [ -n "$payload" ]; then
    response=$(printf '%s' "$payload" | curl -sS --connect-timeout 5 --max-time 60 -w '\n%{http_code}' \
      -H 'Content-Type: application/json' --data-binary @- "$BASE$path") || fail "Cannot reach $BASE$path"
  else
    response=$(curl -sS --connect-timeout 5 --max-time 60 -w '\n%{http_code}' "$BASE$path") || fail "Cannot reach $BASE$path"
  fi
  status=${response##*$'\n'}
  body=${response%$'\n'*}
  case ",$expected," in *",$status,"*) ;; *) fail "$path: expected HTTP $expected, got $status: $body" ;; esac
  if [ "$status" = 404 ]; then printf 'METRICS_DISABLED'; else printf '%s' "$body"; fi
}
contains() { printf '%s\n' "$1" | grep -Eq "$2" || fail "$3"; }
scalar() {
  printf '%s\n' "$1" | tr '\n' ' ' | sed -nE "s/.*\"$2\"[[:space:]]*:[[:space:]]*(-?[0-9]+([.][0-9]+)?([eE][+-]?[0-9]+)?).*/\1/p"
}
counts() {
  local all value outcome metric result=""
  all=$(http /actuator/metrics/polyglot.client.invocations 200,404)
  if [ "$all" = METRICS_DISABLED ]; then printf 'disabled'; return; fi
  contains "$all" '"tag"[[:space:]]*:[[:space:]]*"language"' 'Missing language tag'
  contains "$all" '"tag"[[:space:]]*:[[:space:]]*"outcome"' 'Missing outcome tag'
  [ "$(printf '%s' "$all" | grep -o '"tag"' | wc -l | tr -d ' ')" -eq 2 ] || fail 'Unexpected invocation tag cardinality'
  for outcome in success failure; do
    metric=$(http "/actuator/metrics/polyglot.client.invocations?tag=language:python&tag=outcome:$outcome" 200)
    value=$(printf '%s\n' "$metric" | tr '\n' ' ' | sed -nE 's/.*"statistic"[[:space:]]*:[[:space:]]*"COUNT"[[:space:]]*,[[:space:]]*"value"[[:space:]]*:[[:space:]]*([0-9]+([.][0-9]+)?([eE][+-]?[0-9]+)?).*/\1/p')
    [ -n "$value" ] || fail "Missing $outcome Timer COUNT"
    result="$result $(awk -v value="$value" 'BEGIN { printf "%.0f", value }')"
  done
  printf '%s' "$result"
}
verify_deltas() {
  local before=$1 successes=$2 failures=$3 after first_success first_failure last_success last_failure
  if [ "$before" = disabled ]; then printf 'Invocation Timer disabled; HTTP results verified.\n'; return; fi
  after=$(counts)
  [ "$after" != disabled ] || fail 'Invocation Timer disappeared during traffic'
  read -r first_success first_failure <<< "$before"
  read -r last_success last_failure <<< "$after"
  [ "$((last_success-first_success))" -eq "$successes" ] && [ "$((last_failure-first_failure))" -eq "$failures" ] ||
    fail 'Unexpected Timer deltas; use a dedicated demo instance without other traffic'
  printf 'Timer deltas: success=%s failure=%s (cumulative totals %s/%s)\n' "$successes" "$failures" "$last_success" "$last_failure"
}
health() {
  local body info
  body=$(http /actuator/health 200)
  contains "$body" '"code"[[:space:]]*:[[:space:]]*"UP"|"status"[[:space:]]*:[[:space:]]*"UP"' 'Application is not UP'
  info=$(http /actuator/info 200)
  contains "$info" '"available"[[:space:]]*:[[:space:]]*true' 'Python executor unavailable'
  printf 'Health (startup snapshot, not a guest-function probe): %s\nInfo: %s\n' "$body" "$info"
}
smoke() {
  local before payload summary rolling risk invalid failure
  printf '=== Embedded NumPy analytics ===\n'
  before=$(counts)
  payload='{"samples":[10,10,10,10,100]}'
  summary=$(http /analytics/summary 200 "$payload")
  [ "$(scalar "$summary" mean)" = 28.0 ] || fail "Unexpected reference mean: $summary"
  contains "$summary" '"numpyVersion"[[:space:]]*:[[:space:]]*"2\.2\.4"' 'Expected NumPy 2.2.4'
  rolling=$(http '/analytics/rolling?window=2' 200 "$payload")
  contains "$rolling" '"means"[[:space:]]*:[[:space:]]*\[10\.0,10\.0,10\.0,55\.0\]' 'Unexpected rolling means'
  risk=$(http '/analytics/risk?threshold=1.5' 200 "$payload")
  [ "$(scalar "$risk" anomalyCount)" = 1 ] || fail 'Expected one anomalous reading'
  invalid=$(http /analytics/summary 400 '{"samples":[]}')
  contains "$invalid" '"code"[[:space:]]*:[[:space:]]*"INVALID_INPUT"' 'Expected Java input validation error'
  failure=$(http /analytics/failure 500 '{}')
  contains "$failure" '"code"[[:space:]]*:[[:space:]]*"PYTHON_EXECUTION_FAILED"' 'Expected safe Python failure'
  printf 'Summary: %s\nRolling: %s\nRisk: %s\nExpected guest failure (HTTP 500): %s\n' "$summary" "$rolling" "$risk" "$failure"
  verify_deltas "$before" 3 1
  health
}
traffic() {
  local before payload i active=0 pid failed=0 body path expected
  local -a workers
  workers=()
  before=$(counts)
  payload=$(awk -v size="$SIZE" 'BEGIN { printf "{\"samples\":["; for(i=0;i<size;i++) printf "%s%d", (i ? "," : ""), (i==size-1 ? 100 : 10+i%5); printf "]}" }')
  for ((i=0; i<REQUESTS+ERRORS; i++)); do
    if [ "$i" -ge "$REQUESTS" ]; then path=/analytics/failure; expected=500
    elif [ "$((i%2))" -eq 0 ]; then path=/analytics/summary; expected=200
    else path='/analytics/risk?threshold=1.5'; expected=200; fi
    (
      body=$(http "$path" "$expected" "$payload")
      if [ "$expected" -eq 500 ]; then
        contains "$body" '"code"[[:space:]]*:[[:space:]]*"PYTHON_EXECUTION_FAILED"' 'Unexpected failure response'
      fi
    ) &
    workers[${#workers[@]}]=$!
    active=$((active+1))
    if [ "$active" -eq "$CONCURRENCY" ]; then
      for pid in "${workers[@]}"; do wait "$pid" || failed=1; done
      workers=(); active=0
    fi
  done
  if [ "$active" -gt 0 ]; then for pid in "${workers[@]}"; do wait "$pid" || failed=1; done; fi
  [ "$failed" -eq 0 ] || fail 'At least one traffic request failed; see HTTP diagnostics above'
  printf 'Traffic: %s successes, %s expected guest errors, size=%s, concurrency=%s\n' "$REQUESTS" "$ERRORS" "$SIZE" "$CONCURRENCY"
  printf 'One executor serializes guest execution; concurrent HTTP does not imply Python parallelism.\n'
  verify_deltas "$before" "$REQUESTS" "$ERRORS"
}
metrics() {
  local names meter
  printf '=== Available polyglot meters (actual endpoint responses) ===\n'
  printf 'Open in browser: %s/actuator/metrics\n' "$BASE"
  names=$(http /actuator/metrics 200)
  while IFS= read -r meter; do
    [ -n "$meter" ] || continue
    printf '%s: %s\n' "$meter" "$(http "/actuator/metrics/$meter" 200)"
  done < <(printf '%s\n' "$names" | tr ',' '\n' | sed -nE 's/.*"(polyglot\.[a-z.]+)".*/\1/p')
  if [ "$(counts)" = disabled ]; then printf 'Invocation metrics are disabled or filtered.\n'; fi
}
console() {
  http /polyglot-console/index.html 200 >/dev/null
  printf 'Open in your browser: %s/polyglot-console/\nRead-only Actuator snapshots; generate real traffic with ./demo.sh traffic.\n' "$BASE"
}
case "$COMMAND" in start|stop|restart|demo)
  mkdir "$STATE/lock" 2>/dev/null || fail "Another management command holds $STATE/lock; if interrupted, inspect processes before removing the empty lock"
  trap 'rmdir "$STATE/lock" 2>/dev/null || true' EXIT
  trap 'exit 130' INT TERM ;;
esac
case "$COMMAND" in
  start) start ;;
  stop) stop_owned ;;
  restart) stop_owned; start ;;
  status)
    read_state
    if owned; then
      printf 'Running: PID %s URL http://%s:%s Log %s/%s/app.log\n' "$PID" "$HOST" "$RUN_PORT" "$STATE" "$TOKEN"
      curl -fsS --max-time 2 "http://$HOST:$RUN_PORT/actuator/health/readiness" || fail "Process is running but not ready"
      printf '\n'
    else fail "No verified managed sample is running"; fi ;;
  logs) read_state; [[ "$TOKEN" =~ ^run\.[a-zA-Z0-9]+$ ]] || fail "No managed log path"; tail -n 60 "$STATE/$TOKEN/app.log" ;;
  demo|smoke|traffic|metrics|health|console)
    if [ "$COMMAND" = demo ]; then start; fi
    command -v curl >/dev/null || fail "curl is required"
    case "$COMMAND" in
      demo) smoke; traffic; metrics; health; console ;;
      smoke) smoke ;; traffic) traffic ;; metrics) metrics ;; health) health ;; console) console ;;
    esac ;;
esac
