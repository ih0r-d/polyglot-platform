# Embedded Python Analytics Engine

A runnable `polyglot-platform` **0.5.0-SNAPSHOT** showcase: Java/Spring Boot validates telemetry
batches, embedded GraalPy executes NumPy analytics, and the starter owns binding, lifecycle,
Micrometer instrumentation and Actuator integration. No Python service, database or Docker is needed.
Existing `/stats`, `/libs` and `/forecast` examples remain available.

Java source responsibilities are separated: `controller/AnalyticsController` contains HTTP
orchestration; `controller/AnalyticsExceptionHandler` contains analytics-scoped advice and logging;
`validation/AnalyticsInputValidator` contains input rules; `dto/` contains request/result records;
`contracts/` contains only typed bindings. Python owns the numerical algorithms.

```text
HTTP → Spring validation → annotated Java contract → serialized executor → GraalPy / NumPy
                          ← detached Java records ← Python dicts / numerical arrays
Micrometer Timer + runtime gauges → standard Actuator endpoints
```

## One-command quick start

Prerequisites: Bash, curl, Git and a working **JDK 25+** selected with `JAVA_HOME`, installed
SDKMAN candidate or PATH. The library compiles for Java 21; this existing sample compiles for 25.
Use Oracle GraalVM **25.0.4.1.1** for pinned-runtime validation. A regular JDK 25 can run the JVM
example with interpreter fallback, but does not establish equivalent performance or Native Image support.

From the repository root:

```bash
./samples/spring-boot-example/demo.sh demo
```

If necessary, select an existing JDK explicitly, without changing SDKMAN settings:

```bash
JAVA_HOME=/absolute/path/to/jdk25 ./samples/spring-boot-example/demo.sh demo --port 8085
```

First start installs current reactor artifacts and builds the sample with the root Maven Wrapper.
It needs network access for missing Maven/Python packages, can take several minutes, and caches
artifacts locally. Later starts reuse the built JAR unless sample sources or HEAD changed.
The service remains running for inspection; finish with `./samples/spring-boot-example/demo.sh stop`.
Use the same `--port` (or `DEMO_PORT`) for subsequent HTTP commands when using a custom port.

The opt-in `observability` profile binds to loopback and exposes only health, info and metrics.
No authentication is installed: this is a local demo, not a production deployment template.

## Run from IntelliJ IDEA

The sample is a separate Maven project, outside the root reactor. Right-click its
`pom.xml` and select **Add as Maven Project**, then reload Maven projects. Importing only
the repository root does not import the sample's dependencies.

If test compilation reports missing JUnit or AssertJ, check **Project Structure → Modules →
polyglot-springboot-example → Sources**. Neither `src` nor `src/main` should be marked as a
source root. Unmark those broad roots if present; Maven imports `src/main/java` as Sources
and `src/test/java` as Tests. Keep the existing test-scoped dependencies in the POM.

Use JDK 25+ for the sample module and Maven runner. Prepare the GraalPy VFS through Maven
before the first IDE launch, from the repository root:

```bash
./mvnw -f samples/spring-boot-example/pom.xml process-resources
```

Run `io.github.ih0rd.examples.PolyglotSpringbootExampleApplication` with:

- Classpath module: `polyglot-springboot-example`.
- Program arguments: `--spring.profiles.active=observability`.
- VM options: `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow`.

For a Spring Boot run configuration, setting **Active profiles** to `observability` is
equivalent to the program argument. These options follow the standard
[IDEA Spring Boot run configuration](https://www.jetbrains.com/help/idea/run-debug-configuration-spring-boot.html).
Stop an existing shell-managed demo with `./samples/spring-boot-example/demo.sh stop`
before starting the IDE application on the same port. The script does not own or stop IDE processes.

## Experimental Polyglot Console

The sample consumes `polyglot-spring-boot-console` as a dependency JAR; it contains no copied
console UI or MVC wiring. With JDK 25 selected, run from the repository root:

```bash
./samples/spring-boot-example/demo.sh demo
./samples/spring-boot-example/demo.sh console
./samples/spring-boot-example/demo.sh traffic --requests 20 --errors 3 --concurrency 4
```

Open `/polyglot-console/` on the application's current port (the CLI defaults to 8080).
For Maven or IDEA, enable the local development profiles:

```bash
./mvnw -pl runtime/polyglot-spring-boot-console -am install -DskipTests
./mvnw -f samples/spring-boot-example/pom.xml spring-boot:run \
  -Dspring-boot.run.profiles=observability,console
```

The new module requires **Java 25**. The existing framework-neutral library retains its Java 21
baseline. `polyglot.console.enabled` defaults to `false`; the sample development profiles enable it.
`polyglot.console.path` defaults to `/polyglot-console` and `refresh-interval` to `5s` (allowed 1s–1m).
For a custom path, use `./demo.sh restart --console-path /ops/console`, then use the same option
for `./demo.sh console --console-path /ops/console`. The `DEMO_CONSOLE_PATH` environment variable
is equivalent. In Maven, use `-Dspring-boot.run.arguments="--polyglot.console.path=/ops/console"`.

The browser makes **one read-only GET** to the console's own `<console-path>/data` per refresh.
The module reads Spring-managed Micrometer timers/gauges and the polyglot lifecycle HealthIndicator
in-process. **Actuator HTTP exposure is unnecessary.** The sample still exposes health/info/metrics
for its existing CLI; these are independent of the console. Business analytics and traffic generation
remain outside the console.

Language-specific rows follow `polyglot.python.enabled` / `polyglot.js.enabled` and the global
`polyglot.core.enabled` switch. The sample disables JS, so its preregistered zero-count Timers
are not displayed. Enabled languages still show genuine zero counts. Startup logs an INFO
`Polyglot Console available at ...` URL after the embedded application is ready, including its
actual port, context path and custom console path.

Counts and Python means are cumulative. Table rows keep language/outcome scopes separate;
MAX is a time-window maximum. Charts contain at most 120 session-local intervals: throughput is
COUNT delta / elapsed time, and interval latency is matching TOTAL_TIME delta / COUNT delta.
Idle intervals have no latency point. Observed counter decreases and application-context identity
changes clear history. Missing/disabled metrics remain unavailable, not zero. Hidden tabs pause
polling. No historical data, percentiles or persistence are supplied by this developer tool.

**Security:** disabled by default and restricted to loopback peers/local host names even when
explicitly enabled. Assets and data pass through the application's existing SecurityFilterChain;
there is no permit-all rule, authentication implementation, CSRF change or custom credential.
Applications with security must authorize their intended developers for the configured console
path and its descendants. Do not publicly proxy this sample, trust forwarded headers from
untrusted proxies, or treat loopback checks as authentication. No paths, arbitrary properties,
health details, exception messages or user data are returned by the console DTO.

Open the HTTP URL, not the HTML file. HTTP 404 means the console is disabled, the path differs,
or the new dependency has not been built/reloaded. HTTP 401/403 can mean application authorization
or the local-only restriction. A reverse proxy/remote deployment is outside this experimental
console's supported scope. An empty dashboard can mean metrics are disabled, no calls have run,
or a unique/primary MeterRegistry is unavailable. Health is recorded startup state, not a probe
of Python business functions.

Developer checks (Node.js 22.7+ is only needed for JS tests, not console execution):

```bash
node --test runtime/polyglot-spring-boot-console/src/test/js/console-test.js
./mvnw -pl runtime/polyglot-spring-boot-console -am verify
```

## Business operations and numerical contracts

Input is a JSON object: `{"samples":[10,10,10,10,100]}`. Values are synthetic readings in arbitrary
sensor units; input order is time order. No financial or calibrated statistical risk claim is made.

| POST endpoint | Python work | Response |
| --- | --- | --- |
| `/analytics/summary` | NumPy mean, median, population standard deviation (`ddof=0`), min/max, linearly interpolated p05/p95 | Typed summary, count, NumPy version |
| `/analytics/rolling?window=2` | Vectorized valid-window convolution | Means in time order; `firstIndex=window-1` is zero-based |
| `/analytics/risk?threshold=1.5` | Population z-scores; anomaly if `abs(z) > threshold` | Scores, ascending zero-based anomaly indices, count, risk/quality scores |
| `/analytics/failure` | Deliberate Python exception | HTTP 500, safe `PYTHON_EXECUTION_FAILED` response |

`riskScore = 100 × anomalyCount / count`; `qualityScore = 100 - riskScore`. Both are percentages
of this batch, not probabilities. Zero-variance series have zero z-scores and no anomalies.
For `[10,10,10,10,100]`, the reference mean is 28, population deviation 36, z-scores are
`[-0.5,-0.5,-0.5,-0.5,2]`, anomaly index `[4]`, and quality score 80. These are analytical reference
values; timing and runtime gauge readings vary.

```bash
curl -fsS http://127.0.0.1:8080/analytics/summary \
  -H 'Content-Type: application/json' -d '{"samples":[10,10,10,10,100]}'
curl -fsS 'http://127.0.0.1:8080/analytics/rolling?window=2' \
  -H 'Content-Type: application/json' -d '{"samples":[10,10,10,10,100]}'
curl -fsS 'http://127.0.0.1:8080/analytics/risk?threshold=1.5' \
  -H 'Content-Type: application/json' -d '{"samples":[10,10,10,10,100]}'
curl -i http://127.0.0.1:8080/analytics/failure \
  -H 'Content-Type: application/json' -d '{}'
```

HTTP 400 rejects empty/missing/null/malformed input, non-finite numbers, more than 100000 samples,
absolute values above `1e100`, invalid windows and non-positive/non-finite thresholds. The numeric
bound also keeps intermediate variance arithmetic finite. Java validates before invoking Python;
these rejected requests do not increment invocation Timers. Python validates direct contract calls
too. Guest exceptions increment the failure Timer and return a safe HTTP 500 without source,
traceback, arguments or internal paths. Legacy endpoints retain their existing error behavior.

## Typed bindings, conversion and lifecycle

- `AnalyticsApi` uses DEFAULT binding: `analytics_api.py` exports class `AnalyticsApi`.
- `RiskApi` uses BY_METHOD_NAME: `risk_api.py` defines `assess` and `fail`. Successful module
  evaluation is cached once per contract/executor; method names match exactly. No per-request eval.
- Python accepts numeric sequences and returns ordinary dictionaries/lists, without importing Java
  DTO or HTTP types. `AnalyticsConfiguration` uses the existing `PolyglotContextCustomizer` and
  HostAccess target mappings to produce typed Java records and detached immutable lists **during
  the executor's locked conversion**. HTTP serialization never reads a live guest view. Mappings
  target only the new result records; legacy Map/List conversions are unchanged.
- Multiple singleton clients share one executor/Context. Concurrent HTTP requests are safe through
  the executor's serialization, not parallel Python execution. There is no Context pool or global Engine.
- Spring shutdown closes the owned executor/Context. No application code manually closes or enters it.
- Fail-fast and Python warmup remain enabled. Startup resolves bindings and evaluates module-level
  code/imports; it does not call business functions. This sample injects clients into eager controllers,
  so `polyglot.core.fail-fast=false` alone does **not** guarantee lazy Python initialization.
- Existing `polyglot.python.preload-scripts` performs raw startup evaluation, not prebinding or reload;
  subsequent binding may evaluate the source again. No preload is needed for this sample.

## Reproducible Python packaging

GraalPy Maven dependencies and plugin stay **25.0.4.1.1**, Spring Boot stays **4.1.0**. The committed
`graalpy.lock` pins NumPy **2.2.4**, Faker **26.0.0**, tabulate **0.9.0** and transitive dependencies.
NumPy does the actual numerical work; Faker/tabulate support the preserved legacy examples.

`process-graalpy-resources` installs the locked packages into a managed virtual environment under
`target/classes/org.graalvm.python.vfs/`, produces VFS metadata, and packages it in the application
JAR. The framework's existing GraalPy VFS setup exposes these dependencies to the embedded Context.
No external CPython process performs business calculations. Rebuilding uses cached resources when
possible; a clean build may need package downloads. Ordinary tests do not regenerate the lock.

Updating dependencies is an explicit maintainer action (not required to run the demo):

```bash
./mvnw -f samples/spring-boot-example/pom.xml \
  org.graalvm.python:graalpy-maven-plugin:25.0.4.1.1:lock-packages
```

Review lock changes before accepting them. See the official
[GraalPy build-tool guide](https://www.graalvm.org/python/docs/) and
[Boot executable JAR launcher](https://docs.spring.io/spring-boot/specification/executable-jar/launching.html).
The CLI is Bash and curl, with standard macOS/Linux sed/awk tools for known numeric response
fields. There is no Java CLI, jq requirement or external Python. Raw JSON is printed for inspection;
the script is not a general-purpose JSON parser. Java contains only the application implementation.

## CLI reference

Run from the sample directory, or prefix commands with `./samples/spring-boot-example/` from root.

| Command | Behavior |
| --- | --- |
| `help`, `COMMAND --help` | Prerequisites, options and examples; no build |
| `start` / `restart` | Build if needed; start and poll readiness with a timeout |
| `stop` | Graceful TERM only after matching PID, owner token, JAR path and process start time |
| `status` | Verified PID, readiness, URL and log path; nonzero when stopped/unready |
| `smoke` | Reference analytics, input/guest failures, Timer deltas, health and info |
| `traffic` | Known success/error counts across both contracts, configurable size and concurrent callers |
| `metrics` | Discover all available `polyglot.*` meters, display actual tags/measurements/units |
| `health` | Display health/info; no business-function health probe |
| `logs` | Recent managed application logs |
| `demo` | Start → reference analytics/errors → controlled traffic → metrics → health/info |

Options: `--port` (8080), `--host` (127.0.0.1 or localhost), `--requests` (10 successes), `--errors`
(2 guest failures), `--size` (1000 values), `--concurrency` (1, maximum 16), `--metrics true|false`,
`--timeout` (120 startup seconds). Total traffic is limited to 10000 calls per command. Matching
`DEMO_*` variables are listed by `help`. Plain non-interactive output also respects `NO_COLOR`.

Private state/logs live under `.local/showcase/` (umask 077), outside Maven's cleaned target directory.
A management lock prevents duplicate starts. Scripts never signal a PID whose ownership cannot
be verified. If a crashed process leaves stale state, inspect `pid`, `ps` and logs before removing
that **state file only**. After an interrupted management command, remove the empty `lock` directory
only after checking that no management command is still running. No force-kill or automatic JDK install.

## Authentic metrics and operational semantics

Inspect Actuator directly in a browser while the service is running:

- [Endpoint discovery](http://localhost:8080/actuator)
- [Invocation count, total time and maximum](http://localhost:8080/actuator/metrics/polyglot.client.invocations)
- [Successful Python calls](http://localhost:8080/actuator/metrics/polyglot.client.invocations?tag=language:python&tag=outcome:success)
- [Failed Python calls](http://localhost:8080/actuator/metrics/polyglot.client.invocations?tag=language:python&tag=outcome:failure)
- [Health](http://localhost:8080/actuator/health) and [info](http://localhost:8080/actuator/info)

Replace port 8080 when using `--port`. These are the same real endpoints read by the Bash CLI.

The starter instruments calls; the sample adds no duplicate timers or counters. The invocation
Timer is `polyglot.client.invocations`, with only `language=python|js` and
`outcome=success|failure`. There are at most four combinations, including unused JS combinations.
`COUNT` is cumulative, `TOTAL_TIME` and `MAX` are seconds; `MAX` is a time-window maximum.
`TOTAL_TIME/COUNT` is the recorded mean. Timing includes executor waiting, guest execution and
conversion; excludes HTTP parsing/serialization, proxy construction and startup binding.

Smoke generates exactly **3 successful guest calls and 1 guest failure**, plus a Java-rejected
HTTP 400. Traffic adds the requested counts. The client checks **deltas**, so repeated smoke/demo
runs work without restarting. Use a dedicated instance: unrelated traffic can change those deltas.

Other existing meters, discovered rather than hardcoded by the CLI:

- `polyglot.executor.available.count`, `polyglot.executor.configured.count`: executor snapshots;
- `polyglot.startup.duration`: recorded starter lifecycle initialization, in **milliseconds**;
  it is not full JVM/Spring startup or process-to-first-response latency. It can be zero
  when eager client/Context initialization happened before the timed lifecycle hook;
- `polyglot.executor.enabled`, `polyglot.executor.source.cache.size`: per-language diagnostics;
- `polyglot.python.instance.cache.size`, `polyglot.python.bound.interfaces.count`: DEFAULT
  instance-cache entries; do not treat them as the total number of annotated clients or live objects.

`/actuator/health` records startup state. It remains UP after an intentional business failure.
No Python function runs per health request. UNKNOWN means no startup guest checks ran (or shutdown);
DOWN means recorded startup failure/unavailability. Fail-fast can abort startup before HTTP is available.
`/actuator/info` reports configuration and executor metadata. Gson represents health status as
`{"code":"UP","description":""}` and wraps individual contributors in `health`.
Readiness is separately available at `/actuator/health/readiness`.

```bash
./demo.sh traffic --requests 20 --errors 3 --concurrency 4 --size 1000
./demo.sh metrics
curl -fsS 'http://127.0.0.1:8080/actuator/metrics/polyglot.client.invocations?tag=language:python&tag=outcome:success'
./demo.sh restart --metrics false
./demo.sh smoke
./demo.sh restart --metrics true
```

When disabled, invocation metrics return 404; analytics, health and info still work. Actuator is a
diagnostic interface, not a production metrics backend. This example does not add Prometheus,
Grafana or a custom endpoint. Existing warm-invocation JMH methodology remains in
[benchmarks](../../benchmarks/README.md); HTTP traffic here is correctness/demo traffic, not a benchmark.

## Reproducible public demonstration

For a local screen recording or LinkedIn walkthrough (nothing is published automatically):

```bash
./demo.sh demo --requests 6 --errors 2 --concurrency 1 --size 100
./demo.sh traffic --requests 12 --errors 3 --concurrency 4 --size 1000
./demo.sh metrics
./demo.sh health
./demo.sh stop
```

Show the deterministic mean/z-scores/quality score first, then actual Timer counts/timings and
runtime gauges. Explain that errors are deliberate and health is a startup snapshot. Runtime
measurements must be read from your run; no timing guarantees or Native Image claims are made.

## Verification and troubleshooting

```bash
bash -n samples/spring-boot-example/demo.sh
bash samples/spring-boot-example/tests/demo-test.sh
bash samples/spring-boot-example/tests/demo-integration-test.sh
./mvnw -B -ntp -f samples/spring-boot-example/pom.xml verify
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
  -jar samples/spring-boot-example/target/polyglot-springboot-example-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=observability
```

The sample HTTP tests check numerical
references, both bindings, conversions, errors, concurrent calls, Timer deltas/cardinality, disabled
metrics, and shutdown. Surefire uses fresh JVMs because NumPy native modules cannot be loaded into
multiple default GraalPy Contexts in one process. The shell integration test exercises the same user-facing demo/smoke/traffic commands in a separate
process. It does not spawn shell children from a JVM with an active GraalPy/NumPy Context.
Library gates remain documented in
[developer tooling](../../docs/developer-tooling.md).

- **JDK 21 selected:** set `JAVA_HOME` to a working JDK 25+, then retry. The CLI never replaces an
  explicit JDK or installs SDKMAN candidates. Missing `lib/jvm.cfg` means an incomplete installation.
- **NumPy import/install failure:** inspect Maven and application logs, verify the lock/runtime
  alignment, network/proxy access, and supported platform. Do not substitute system CPython or
  silently disable NumPy. Current tested platform is macOS ARM64 JVM mode; other platforms need validation.
- **Port occupied:** use `--port`, keeping the same option for later HTTP commands. Check `status`
  before restarting; an unrelated process will never be stopped by the script.
- **Unexpected metrics deltas:** stop other traffic or use a separate managed demo instance.
- **Readiness timeout:** inspect `logs` and increase `--timeout` for slow first startup; do not
  interpret moving initialization to the first request as eliminating it.
- **Native test stability:** some local HotSpot test forks, including coverage runs, crashed or
  hung during NumPy/native-runtime teardown (`SIGBUS`, `SIGTRAP`, or Python GC shutdown). This is
  not claimed fixed by the sample. The exact Oracle GraalVM 25.0.4.1.1 installation is unavailable
  on the test machine; its runtime/lifecycle validation remains pending. A passing static Quality
  Gate or HTTP demo does not certify native-extension teardown on every JVM.
- **Native Image:** this task does not validate it. The existing user-owned experimental resource
  hint is untouched. JVM success or an executable JAR is not proof of Spring Native Image support.
