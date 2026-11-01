# Polyglot Tracing

Spring-managed typed Python and experimental JavaScript clients optionally create one
`polyglot.invoke` span per synchronous invocation. The starter uses the application's Micrometer
`Tracer`; it does not create an OpenTelemetry SDK, exporter, collector, or global registry.
The adapter, reload implementation, metrics, and Console do not depend on tracing.

## Spring Boot 4.1 setup

Add the existing polyglot starter and Boot's optional OpenTelemetry starter to the application:

```xml
<dependency>
  <groupId>io.github.ih0r-d</groupId>
  <artifactId>polyglot-spring-boot-starter</artifactId>
  <version>0.5.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

Use Spring Boot's dependency management. Boot **4.1.0** manages Micrometer Tracing **1.7.0** and
OpenTelemetry **1.62.0**. The library only declares an optional Micrometer Tracing API dependency;
bridges, SDKs, exporters, sampling, propagation, resource attributes and their lifecycle belong to
the application. Applications without a unique managed `Tracer` receive no tracing decoration.
`polyglot.core.enabled=false` and `management.tracing.enabled=false` disable this integration.
Disabling an exporter alone does not disable span creation. Existing application samplers apply,
including unsampled parent decisions. No Actuator HTTP exposure or Java agent is required.

```properties
spring.application.name=python-service
management.tracing.sampling.probability=0.1
management.opentelemetry.tracing.export.otlp.endpoint=http://127.0.0.1:4318/v1/traces
```

Use appropriate authentication/TLS settings for the deployment through standard Boot exporter
configuration. Never put collector credentials into Python contracts, span tags, or source control.
See the official [Boot tracing guide](https://docs.spring.io/spring-boot/reference/actuator/tracing.html),
[4.1.0 tracing auto-configuration source](https://github.com/spring-projects/spring-boot/tree/v4.1.0/module/spring-boot-micrometer-tracing-opentelemetry),
and [Micrometer direct tracing API](https://docs.micrometer.io/tracing/reference/api.html).
The live documentation may describe a newer patch; the dependencies above follow this repository's BOM.

## Span semantics

```text
HTTP request or application span (when present)
└── polyglot.invoke
```

The invocation span starts immediately before dispatch to the bound runtime proxy and ends after
its return or failure, including serialized admission, guest execution, and result conversion.
Binding/validation and Object methods such as `toString()` do not create invocation spans.
Direct `PyExecutor` clients outside Spring are unchanged and are not automatically traced.

Admission and under-lock durations remain the accurate, separate Runtime Metrics 2.0 timers.
They are not additional child spans. This avoids composing/replacing the single runtime observer
or adding tracing callbacks at synchronized executor instrumentation points. A reentrant client
call from a host callback still runs within the outer guest invocation and its existing monitor.
Neither total invocation latency nor under-lock duration is pure Python CPU time.

| Attribute | Values | Recorded |
| --- | --- | --- |
| `polyglot.language` | `python`, `js` | Every invocation |
| `polyglot.operation` | `invoke` | Every invocation |
| `polyglot.outcome` | `success`, `failure` | At completion |
| `polyglot.error.category` | `invocation` | Failed invocation |

Failures mark the span as an error using a synthetic `InvocationException` with the constant
message `Polyglot invocation failed`, no cause, and an empty stack. The original exception still
reaches the caller unchanged. Guest messages, source, paths, Java method/interface names, arguments,
results, and raw exceptions are never sent to the tracer. The category deliberately does not
infer guest, conversion or lifecycle phases from wrapped exceptions.

Scopes close in `finally`, including on interruption or shutdown rejection. Tracing callback
`RuntimeException`s are isolated from calls; VM Errors are not suppressed. An arbitrary broken
tracer that corrupts its own thread-local context cannot be repaired by the library. Normal
Micrometer/OTel scope semantics are covered by integration tests.

Synchronous Java → Python → Java callbacks see the active invocation context on the same thread.
This does not instrument Python functions with Python's OpenTelemetry SDK, inject context into
guest globals, or propagate context automatically to application-created threads or async work.
Use the application's supported context-propagation mechanisms across asynchronous boundaries.
Explicit reload remains untraced; subsequent calls through existing proxies retain tracing.

The integration uses `Tracer` directly, not a new Micrometer `Observation`, so it registers no
meters and does not duplicate existing client or runtime timers. Tracing can run while Polyglot
metrics are disabled. Sampling limits exported spans; unsampled calls still carry context.
There are no hot-path meter lookups, registrations, filesystem reads, or reload checks added.
Tracing does have span/scope and clock overhead; no latency/allocation speed guarantee is claimed.

## Local HTTP demonstration

From the repository root, using JDK 25:

```bash
task samples
docker compose -f samples/spring-boot-example/compose.dev.yml up -d
./mvnw -f samples/spring-boot-example/pom.xml -Pdev spring-boot:run
```

This opt-in Maven profile adds Boot's OpenTelemetry dependency; the `dev` Spring profile binds
to localhost, samples every call for demonstration, exports OTLP/HTTP to port 4318, and exposes health, info and metrics on loopback. Ordinary sample execution does not require a backend. Override the application
port with `-Dspring-boot.run.arguments=--server.port=8545` if needed.

```bash
curl 'http://127.0.0.1:8080/stats/random?n=3'
curl -X POST 'http://127.0.0.1:8080/analytics/failure'
```

The first request succeeds; the second returns the existing sanitized HTTP 500. In Jaeger or
Grafana Tempo, select the service `polyglot-springboot-example` (or your configured
`spring.application.name`) and locate `polyglot.invoke` beneath the HTTP server span. A handled
HTTP error need not mark the HTTP span ERROR, but the failed polyglot child is ERROR.

An optional local Jaeger backend can be run separately, following its official
[getting-started guide](https://www.jaegertracing.io/docs/latest/getting-started/). Use an official
Jaeger v2 distribution with OTLP/HTTP enabled on 4318 and its UI on 16686. Docker is optional and
not required by tests. Tempo must have an OTLP/HTTP receiver and a Grafana data source configured;
see its [official setup documentation](https://grafana.com/docs/tempo/latest/set-up-for-tracing/).
The sample includes `compose.dev.yml` for this optional Jaeger backend; the library starts no
collector. Console and Actuator metrics are enabled by the same `dev` Spring profile.

Collector-free consumer verification:

```bash
./mvnw -f samples/spring-boot-example/pom.xml -Pdev test
```

The profile adds `src/tracing-test/java` to the normal sample test sources, using an
in-memory exporter. `-Pdev test` runs both normal and tracing tests; their output directory is
separate from the ordinary profile to prevent stale test discovery. The HTTP test
verifies successful/failed Python child spans, matching HTTP parents, unchanged invocation counts,
and executor shutdown. It does not prove remote collector delivery or backend UI configuration.

The sample `dev` profile filters only exported SERVER spans for GET polling of Console and
Actuator health/info/metrics paths, using Boot's managed `SpanExportingPredicate`. Request metrics
and business tracing remain enabled; non-GET requests and non-SERVER spans are retained.
No filtering is installed by the library. Custom console/base/context paths are supported.

The sample excludes the OTLP metrics registry from Boot’s combined OpenTelemetry starter: Jaeger
is trace-only, and the existing local metrics registry and timer units remain unchanged.
