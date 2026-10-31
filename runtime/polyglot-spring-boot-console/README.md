# Polyglot Console

Optional, experimental Servlet MVC developer console for Java 25 / Spring Boot 4.1.
The regular starter does not depend on it. It uses the application's Micrometer registry and
Actuator health components directly; public Actuator HTTP exposure is unnecessary.

```xml
<dependency>
  <groupId>io.github.ih0r-d</groupId>
  <artifactId>polyglot-spring-boot-console</artifactId>
  <version>0.5.0-SNAPSHOT</version>
</dependency>
```

```properties
polyglot.console.enabled=true
polyglot.console.path=/polyglot-console
polyglot.console.refresh-interval=5s
```

Disabled by default. Open `/polyglot-console/` on the application's own port; servlet context paths
and custom console paths are supported. For example, `/polyglot-console/?tab=caching` opens Caching.
Unknown tab names fall back to Overview. Tab selection updates the query without navigation or
additional requests. Use Left/Right arrows, Home and End to select tabs; Tab moves to other controls.

## Dashboard

The header, four cumulative client KPIs and language selector remain available on every tab.
The language selector applies to KPIs and all four tabs. Global advanced diagnostics are labelled
separately. Only the visible panel renders; switching tabs does not restart polling or history.

| Tab | Measurements |
| --- | --- |
| Overview | Session-local throughput, interval mean client latency, language/outcome breakdown and time-window MAX. |
| Execution | Active/waiting bound calls, completed calls, cumulative mean monitor admission and under-lock duration. |
| Caching | Matching hits/misses, hit rates, evictions and current entries when available, per language and cache category. |
| Reloads | Explicit Python reload successes/failures, requests in progress, cumulative mean duration and reload evictions by cache. |

Execution bars use a common scale for **independent means**, not additive portions. Admission
includes scheduling and uncontended acquisition overhead; under-lock duration includes lazy
initialization, conversion and error handling. Neither is guest CPU time. Core bound-call counts
can differ from Spring-client KPI totals. Reload duration includes admission; failed reloads may
restore evicted entries. No reload actions or guest execution are available in the console.

Advanced diagnostics is collapsed by default. It contains global executor/lifecycle observations,
DEFAULT bindings, JVM identity/uptime/committed-heap usage/threads and allowlisted boolean
configuration. Cache sizes already visible in Caching are omitted from this section. Configuration
values describe settings, not runtime readiness. Health is a startup/lifecycle snapshot, not a
business-method probe.

Counts and most means are cumulative. Charts retain at most 120 intervals per language scope in
this browser session. Disconnects, pauses, hidden documents and missing samples create gaps;
reconnection starts a fresh interval baseline. Observed counter decreases or application restarts
clear history. Timer MAX is time-window based; no all-time maximum or estimated percentiles are
shown. Zero traffic is distinct from missing/disabled instrumentation (`—`).

## Run the existing sample

Select Java 25, then install the current artifacts with `task samples`. From the repository root:

```bash
./mvnw -f samples/spring-boot-example/pom.xml spring-boot:run \
  -Dspring-boot.run.profiles=observability,runtime-metrics-demo \
  -Dspring-boot.run.arguments="--server.port=8545 --polyglot.console.path=/polyglot-console"
```

Open `http://localhost:8545/polyglot-console/`. The explicit demo profile produces real successful
and rejected reloads using the same injected client. In another terminal generate NumPy HTTP work:

```bash
./samples/spring-boot-example/demo.sh load --port 8545 \
  --duration 120 --interval 2 --requests 12 --errors 2 --size 100000 --concurrency 4
```

## Security and limitations

UI and data remain read-only, loopback-only and subject to the application's existing Spring
Security filters. Authorize the configured console path and its descendants for intended developers.
No authentication implementation, `permitAll`, CSRF exception or additional endpoint is supplied.
Do not expose the console through a public proxy. Loopback restriction is not authentication.
No credentials, source paths, arbitrary properties or guest exception details are returned.

A modern browser with native ES modules and Canvas is required. Assets are packaged in this JAR;
there are no CDNs, frontend dependencies or build tools. WebFlux is not supported by this servlet
adapter. This is a local diagnostic tool, not persistent production monitoring.

If the page disconnects, check application readiness, authorization and the Console Data response.
Missing measurements can mean disabled metrics, no registry or incomplete tag scopes; they are not
inferred as zero. Browser zoom remains under user control; narrow screens reflow tables and may
scroll vertically. See the [runtime catalog](../../docs/runtime.md#runtime-operation-metrics) for
exact instrumentation boundaries.
