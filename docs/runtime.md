# Runtime

## Overview

The runtime layer provides the adapter API on top of GraalVM Polyglot for Python and experimental
JavaScript execution.

Its core purpose is simple: bind a Java interface to a guest-language implementation and call it as normal Java code.

> Note
> The runtime adapter is intended for trusted application embedding. It is not a sandboxing framework.

## Core Runtime Module

`runtime/polyglot-adapter` is framework-neutral and contains the execution model used by every integration style.

### Main Components

- `AbstractPolyglotExecutor`: shared proxy binding, source caching, metadata, and common invocation helpers
- `PyExecutor`: Python-specific script loading, export resolution, instance caching, and binding validation
- `JsExecutor`: experimental JavaScript-specific script loading, function lookup, and binding validation
- `PolyglotHelper`: language-specific `Context` creation and initialization
- `ScriptSource` implementations: classpath, filesystem, in-memory, and composite resolution

## How Runtime Execution Works

![Runtime execution diagram](assets/runtime-diagram.svg)

### Context Creation

`PolyglotHelper` creates a language-specific `Context.Builder`, applies an optional customizer, builds the context, and calls `context.initialize(languageId)`.

Current defaults:

- `allowAllAccess(true)`
- `allowExperimentalOptions(true)`
- interpreter-only warnings disabled
- Python experimental feature warnings disabled

For Python, context creation uses `GraalPyResources.contextBuilder(...)` with a virtual file system. For JavaScript, it uses the regular `Context.newBuilder(...)`.

> **Security warning:** `allowAllAccess(true)` grants guest code unrestricted access to host Java
> types, I/O, and environment variables. These defaults are intended solely for **trusted scripts**
> that are part of the application. Do not use the adapter to execute untrusted or
> user-supplied scripts. See
> [`runtime-semantics.md`](runtime-semantics.md#security-contract) for the full security contract.

### Prepared GraalPy Runtime Resources

GraalVM 25.0.4.1.1 supports preparing runtime resources before application launch with
[`Engine.copyResources`](https://docs.oracle.com/en/graalvm/jdk/25/sdk/org/graalvm/polyglot/Engine.html).
Run this in a separate build or installation JVM with the same GraalPy dependencies as the
application (`Engine` is from `org.graalvm.polyglot`, `Path` from `java.nio.file`; the call can
throw `IOException`):

```java
if (!Engine.copyResources(Path.of("graalpy-resources"), "python")) {
    throw new IllegalStateException("No Python runtime resources were copied");
}
```

Deploy the generated directory with the application, then select it at JVM launch:

```bash
java --enable-native-access=ALL-UNNAMED \
  -Dpolyglot.engine.resourcePath=./graalpy-resources -jar application.jar
```

The same command works for Spring Boot applications using this starter. Set this JVM system
property before the first Engine is initialized; it is process-wide. No new starter property or
Context customizer is needed. A relative path resolves against the process working directory.

The generated layout contains the Python standard library and required native/runtime resources.
Include any additional GraalVM languages/tools used by the application in preparation as well.
Application VFS `src` and `venv` resources remain separately configured by `GraalPyResources`.
Preparing runtime files avoids their first-use unpacking; Python initialization, application
imports and binding validation still execute normally. An existing default resource cache already
reuses extracted files, so measure process-to-first-response latency before adopting this setup.

Use a complete bundle built for the same artifact versions, OS and architecture. Regenerate it
when those change. Prepare a separate immutable bundle and deploy it atomically: `copyResources`
replaces component directories and must not target a live application's bundle. The deployed
directory can be read-only; missing or damaged files can cause native-library or Python import
errors. Context shutdown leaves the prepared directory intact. This setup preserves the starter's
fail-fast configuration and Context/Engine ownership.

### Opt-In Shared Python Engine

The existing APIs support a caller-owned `org.graalvm.polyglot.Engine`; no new factory or global
singleton is required. Each Context still initializes Python and keeps its own guest globals.
An Engine can share code caches across Contexts; it does not share Python objects or the adapter's
per-executor caches.

```java
try (Engine engine = Engine.newBuilder("python")
        .option("engine.WarnInterpreterOnly", "false").build()) {
    try (Context context = PolyglotHelper.newContext(
            SupportedLanguage.PYTHON, false,
            builder -> builder.engine(engine)
                .allowAllAccess(true)
                .allowExperimentalOptions(true)
                .option("python.WarnExperimentalFeatures", "false"));
         PyExecutor executor = PyExecutor.createWithContext(
            context, scriptSource, ContextOwnership.OWNED_BY_EXECUTOR)) {
        // Bind and call application contracts here; other Contexts can use the same Engine.
    }
    // Closing the executor/Context leaves the Engine available for another Context.
}
```

Imports are from `org.graalvm.polyglot` (`Engine`, `Context`),
`io.github.ih0rd.adapter.context` (`PolyglotHelper`, `PyExecutor`, `ContextOwnership`), and
`io.github.ih0rd.polyglot` (`SupportedLanguage`). `scriptSource` is the application's `ScriptSource`.

Pass `false` for recommended defaults and restore the access/Python options explicitly as above.
Otherwise the helper's `engine.WarnInterpreterOnly` option on `Context.Builder` conflicts with an
explicit Engine. Engine-level options belong on `Engine.Builder`. This opt-in configuration keeps
the same trusted-code access policy as the default helper; it does not add a sandbox.

The application owns the Engine and closes it **after all its Contexts and executors**. Sharing an
Engine differs from sharing a Context: each executor can own its separate Context. Use
`ContextOwnership.BORROWED` only when the caller also manages that Context. Executor serialization
is unchanged; this recipe makes no concurrent native-extension or shared-Context throughput claim.
All Contexts on one Engine must use the same host-access configuration and compatible
language options; GraalVM rejects conflicting configurations.

This is useful when repeatedly creating Contexts. It does not eliminate first Python initialization
or guarantee a faster single-Context Spring startup. The standalone
[JMH benchmark](https://github.com/ih0r-d/polyglot-platform/blob/main/benchmarks/README.md)
separates cold startup from repeated Context creation. Upstream documents
[Engine code sharing](https://www.graalvm.org/jdk25/reference-manual/embed-languages/#code-caching-across-multiple-contexts).

### Script Resolution

Executors never read the filesystem or classpath directly. They ask `ScriptSource` whether a logical script exists and open it through a `Reader`.

Logical names are derived from interface names:

- `StatsApi` -> `stats_api`
- `ForecastService` -> `forecast_service`

The actual physical path depends on the chosen `ScriptSource`.

Logical script names are not raw paths. Built-in `ScriptSource` implementations accept relative
`/`-separated names without the language extension, including nested names such as
`scripts/utils/helper`. They reject `null`, blank values, path traversal, absolute paths, Windows
drive or UNC paths, backslash separators, and resource prefixes such as `file:` or `classpath:`.
`FileSystemScriptSource` also rejects symbolic links that resolve outside the configured language
directory under the configured base directory.

### Binding and Invocation

`bind(Class<T>)` creates a JDK dynamic proxy. Each interface method becomes a guest-language method call with the same method name.

At invocation time:

- the proxy delegates to the executor
- the executor resolves or loads the guest script
- the guest function or object member is invoked through GraalVM `Value`
- the result is converted back through `Value.as(returnType)`

If the guest result is `null`, the proxy returns `null`.

If the Java method has a `void` return type, the guest function is called normally and the result
is discarded without any type conversion.

Failures during guest execution surface as adapter exceptions (`InvocationException`,
`BindingException`, `ScriptNotFoundException`, `EvaluationException`). `PolyglotException` thrown
by guest code during proxy method invocation is wrapped as `InvocationException` with the original
`PolyglotException` as the cause. All are unchecked. See
[`runtime-semantics.md`](runtime-semantics.md#exception-contract) for the full exception contract.
For symptom-oriented fixes, see [`troubleshooting.md`](troubleshooting.md).

### Array, Collection and Buffer Transfers

Arguments are passed through the existing Polyglot API; the adapter does not serialize them or
pre-copy Java arrays/collections into Python-owned containers. Creating `list(values)` in Python
materializes a separate Python list. Returning the original Java array can preserve its identity;
returning a Python list as `int[]` instead eagerly converts and copies its elements into a Java
array. Those are different contracts, so an automatic snapshot or array-reuse shortcut is unsafe.

Result conversion remains `Value.as(method.getReturnType())`, including configured custom target
mappings. A Java `List` return type normally exposes a guest-backed view, not an eagerly detached
array; its operations require an open Context. The binding uses the raw return class, not generic
component information. Choosing a view can avoid eager materialization when that lifetime and
mutation behavior suits the application's existing contract, but is not a drop-in array replacement.
See upstream [Value conversion rules](https://docs.oracle.com/en/graalvm/jdk/25/sdk/org/graalvm/polyglot/Value.html#as(java.lang.Class)).

On the verified GraalPy **25.0.4.1.1** JVM setup, Java heap/direct `ByteBuffer` objects expose
buffer elements to the Java Polyglot API, but Python `memoryview(buffer)`, `len(buffer)` and
`list(buffer)` reject them. Java methods such as absolute `get(index)` remain callable from Python
with the configured host access. Per-byte host calls are not a bulk buffer transfer; preserve the
buffer's position/limit, read-only status and signed Java byte semantics when using that route.
Python `bytes` results can be copied into Java `byte[]` through the existing result conversion.
No automatic zero-copy buffer interoperability is promised. Newer upstream
[25.1 release notes](https://www.graalvm.org/release-notes/25.1/) describe foreign-buffer Python
protocol support; that is not evidence of support on this pinned LTS version or a reason to upgrade
within a data-transfer optimization task.

The standalone data-transfer benchmarks separate argument passing, Python materialization,
Python-only computation and Java result conversion. See
[benchmark instructions](https://github.com/ih0r-d/polyglot-platform/blob/main/benchmarks/README.md).

## Python Runtime Details

`PyExecutor` is convention-driven.

### Expected Layout

- Java interface: `ForecastService`
- script: `forecast_service.py`
- export name: `ForecastService`

### Supported Export Styles

Class-style export:

```python
polyglot.export_value("ForecastService", ForecastService)
```

Dictionary-style export:

```python
polyglot.export_value(
    "ForecastService",
    {
        "forecast": forecast
    }
)
```

The executor:

- evaluates the script
- resolves the export from polyglot bindings first, then Python bindings
- instantiates callable exports
- reuses dictionary-style exports directly
- caches resolved targets per interface using weak references

`validateBinding(...)` resolves the instance eagerly, so it is useful at startup when applications want to fail early.

### Python Lifecycle And Cache Semantics

- Source cache is keyed by Java interface type.
- Instance cache is keyed by Java interface type and stored via weak references.
- `clearSourceCache()` clears only source entries; live Python instances remain reusable until instance cache is invalidated.
- `invalidateContractCache(iface)` evicts source + instance entries for that single contract.
- `reloadContract(iface)` is a focused helper: evict that contract cache and eagerly re-validate it.
- Script changes are not auto-detected. To pick up changed code, invalidate/reload the affected contract or recreate the executor.

The more explicit repository-level contract for these behaviors is documented in
[`runtime-semantics.md`](runtime-semantics.md).

## JavaScript Runtime Details

`JsExecutor` uses the same script naming convention but a different binding model.

Expected layout:

- Java interface: `ForecastService`
- script: `forecast_service.js`
- functions in JS bindings: `forecast(...)`

When a Java interface is validated or called:

- the script is evaluated once and cached by interface
- each interface method must exist as an executable JavaScript function
- method calls are executed from language bindings

The runtime does not currently define a separate JavaScript export object convention comparable to Python's class-style and dictionary-style exports.

JavaScript support is intentionally narrower than Python in this release line and is currently
treated as experimental runtime-only binding support, not parity with Python lifecycle semantics.

## Adapter API Surface

Applications normally interact with the runtime through:

- `PyExecutor.create(...)` or `PyExecutor.createWithContext(...)`
- `JsExecutor.create(...)` or `JsExecutor.createWithContext(...)` for the experimental JavaScript path
- `bind(...)`
- `bind(..., convention)`
- `validateBinding(...)`
- `validateBinding(..., convention)`
- `metadata()`

`metadata()` returns a lightweight snapshot intended for diagnostics, actuator info, or metrics. It is not a stable serialization format.

### Context Ownership

Executor factories that create their own GraalVM `Context`, such as `PyExecutor.create(...)`, own
that context and close it from `executor.close()`.

The legacy `createWithContext(context, scriptSource)` overloads and public constructors keep their
historical behavior: the executor closes the supplied context. This is source-compatible with
existing applications that rely on executor-managed lifecycle.

For shared or externally managed contexts, use the overload that accepts `ContextOwnership`:

```java
Context sharedContext = Context.newBuilder("python").allowAllAccess(true).build();
PyExecutor executor =
    PyExecutor.createWithContext(
        sharedContext, scriptSource, ContextOwnership.BORROWED);
```

With `ContextOwnership.BORROWED`, `executor.close()` releases executor-local caches and marks the
executor closed, but it does not close the supplied context. The caller remains responsible for
closing the context after all borrowers are done.

Executor access to a context is serialized per executor instance. If multiple executors share one
borrowed context, callers must still coordinate access across those executors according to GraalVM's
context lifecycle and threading constraints.

## Binding Conventions

The runtime currently exposes three convention values:

- `DEFAULT`
- `BY_INTERFACE_EXPORT`
- `BY_METHOD_NAME`

Current behavior:

- Python:
  - `DEFAULT`: backward-compatible export-based binding
  - `BY_INTERFACE_EXPORT`: explicit export-object/class convention
  - `BY_METHOD_NAME`: direct function lookup from bindings by Java method name
- JavaScript:
  - `DEFAULT`: function lookup by method name
  - `BY_METHOD_NAME`: same runtime path as `DEFAULT`
  - `BY_INTERFACE_EXPORT`: rejected explicitly

Important compatibility note:

- Python invocation behavior under `DEFAULT` remains aligned with the historical export-based path.
- Python validation under `DEFAULT` is now stricter: if the exported contract exists but required Java methods are missing or not executable, `validateBinding(...)` fails earlier than before.

For Python `BY_METHOD_NAME`, a module source is evaluated after its first successful validation or
invocation for that contract and executor, then reused. Mutable Python globals persist between its
method calls. A failed module evaluation is retried. `clearSourceCache()` allows all method-name
contract modules to be evaluated again; `invalidateContractCache(iface)` or
`reloadContract(iface, Convention.BY_METHOD_NAME)` resets one contract's loaded marker. These
operations do not roll back globals already present in the Python Context. Earlier versions
re-evaluated the module on every bound call, so applications relying on repeated module-level side
effects must move them into the function or explicitly invalidate/reload. This behavior does not
change Python `DEFAULT` binding. `preloadScript()` remains separate and may evaluate the same source
again when the contract is later validated or invoked.

## Spring Boot Starter

`runtime/polyglot-spring-boot-starter` wraps the runtime adapter for Spring applications.

### What It Provides

- `PolyglotAutoConfiguration`
- `PolyglotPythonAutoConfiguration`
- `PolyglotJsAutoConfiguration`
- `SpringPolyglotContextFactory`
- `PolyglotExecutors`
- `@EnablePolyglotClients`
- `PolyglotClientRegistrar`
- `PolyglotClientFactoryBean`
- actuator contributors
- Micrometer executor gauges and Spring-managed client invocation timers
- startup warmup lifecycle

### How Users Interact With the Runtime Adapter in Spring

1. Add the starter and language runtime dependencies.
2. Enable the desired languages under `polyglot.*`.
3. Annotate interfaces with `@PolyglotClient`.
4. Enable scanning with `@EnablePolyglotClients`.
5. Inject the generated Spring beans as normal interfaces.

If `@EnablePolyglotClients` does not declare `basePackages`, the starter scans the package of the
importing configuration class.

Language resolution rules for `@PolyglotClient` are enforced by `PolyglotClientFactoryBean`:

- one explicit language: use that executor
- multiple explicit languages: fail
- no explicit language and exactly one executor available: use it
- no explicit language and multiple executors available: fail and require explicit configuration

## Spring Configuration

Current configuration properties live under `polyglot.*`.

Core properties:

- `polyglot.core.enabled`
- `polyglot.core.fail-fast`
- `polyglot.core.log-metadata-on-startup`
- `polyglot.core.log-level`

Python properties:

- `polyglot.python.enabled`
- `polyglot.python.resources-path`
- `polyglot.python.safe-defaults`
- `polyglot.python.warmup-on-startup`
- `polyglot.python.preload-scripts`

JavaScript properties:

- `polyglot.js.enabled`
- `polyglot.js.resources-path`
- `polyglot.js.warmup-on-startup`
- `polyglot.js.preload-scripts`

Operational properties:

- `polyglot.metrics.enabled`
- `polyglot.actuator.enabled`
- `polyglot.actuator.info.enabled`
- `polyglot.actuator.health.enabled`

### Configuration Metadata and Completion

The starter JAR contains `META-INF/spring-configuration-metadata.json` for all 17 `polyglot.*`
properties. Spring Boot's existing configuration processor generates names, types, descriptions
and scalar defaults from `PolyglotProperties`. Additional metadata supplies the empty-list defaults
for preload scripts and the supported startup log levels: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`.
JavaScript properties are explicitly described as experimental.

Spring-aware editors and language servers can use this standard metadata for property completion,
documentation and value suggestions in `application.properties` and `application.yml`. A consumer
only needs the starter dependency; it does not need the configuration processor to read the
starter's packaged metadata. Completion UI depends on the editor's Spring support. No
polyglot-specific IDE plugin is required, and metadata does not change runtime binding or defaults.
Arbitrary resource locations and logical script names are not enumerated or discovered by metadata.

For contributors, keep record-component descriptions in plain-text class-level `@param` Javadoc.
Use `META-INF/additional-spring-configuration-metadata.json` only for values the processor cannot
infer and for hints, rather than copying generated properties. See Spring Boot's
[metadata generation](https://docs.spring.io/spring-boot/specification/configuration-metadata/annotation-processor.html)
and [manual hints](https://docs.spring.io/spring-boot/specification/configuration-metadata/manual-hints.html).
After changing metadata inputs, rebuild from clean sources:

```bash
./mvnw -B -ntp -pl runtime/polyglot-spring-boot-starter -am clean verify
jar tf runtime/polyglot-spring-boot-starter/target/polyglot-spring-boot-starter-0.5.0-SNAPSHOT.jar
```

The generated metadata is the consumer-facing file inside that JAR; editing only `target/` output
will not update future builds. The starter's metadata tests verify property coverage, types,
descriptions, defaults and that every suggested log level binds successfully.

### Optional Metrics and Health

The starter's Actuator and Micrometer dependencies are optional. Add Spring Boot Actuator to the
application to enable the health/info integration and Micrometer registry integration; add a
registry/exporter appropriate for the deployment to publish metrics externally. For example:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Expose the standard endpoints using Spring Boot configuration, for example:

```properties
management.endpoints.web.exposure.include=health,info,metrics
```

`polyglot.metrics.enabled` defaults to `true` and can disable Polyglot meters. When a Micrometer
`MeterRegistry` is present, `polyglot.client.invocations` is a Timer for calls made through
Spring-managed `@PolyglotClient` interfaces. Its only tags are `language` (`python` or experimental
`js`) and `outcome` (`success` or `failure`). The Timer count and total time provide call counts and
duration without a separate counter. Method names, client names, arguments, script contents and
results are never tags. Direct calls through `PolyglotExecutors` or a manually bound executor are
not timed by this Spring integration.

The observer caches four thread-safe timers (two languages and two outcomes) per registry;
unused combinations have zero counts. Repeated client registration does not create extra meters.
Timing includes executor lock waiting, guest execution and result conversion, but excludes binding
and startup validation. `Object` methods are excluded. Standard Boot meter filters such as
`management.metrics.enable.polyglot.client.invocations=false` also apply.

Existing executor/cache/startup gauges remain available. Metrics setup does not construct a GraalVM
Context. When no `MeterRegistry` is present, Spring clients are not wrapped for invocation timing.

The `polyglot` health indicator reports lifecycle state, not Python application correctness:

- `UNKNOWN` before startup, when core support is disabled, after shutdown, or when no startup
  warmup, preload or client validation ran;
- `UP` when startup completed with an executor and at least one configured guest initialization
  check succeeded;
- `DOWN` when lifecycle startup failed with `fail-fast=false`, or completed with no executor.

It reads the recorded startup state and does not resolve executor beans or execute Python during a
health request. With fail-fast and warmup disabled, health stays `UNKNOWN` even after a later
client call: this contributor records startup readiness, not live guest liveness. `UP` does not
validate every script or business method, and failures after startup do not change this snapshot.
Spring Boot endpoint exposure and health group configuration remain controlled by the application;
protect exposed operational endpoints using the application's security policy. Direct construction
of the legacy `PolyglotHealthIndicator` retains its executor-presence check for compatibility.

#### Run the HTTP observability demo

The existing [Spring Boot sample](https://github.com/ih0r-d/polyglot-platform/tree/main/samples/spring-boot-example) already includes Actuator
and real Python clients. With JDK 25 selected (the sample's compilation level), run from the
repository root:

```bash
./samples/spring-boot-example/demo.sh demo
```

The CLI builds/starts a local NumPy telemetry analytics service, verifies DEFAULT and BY_METHOD_NAME
bindings, sends controlled success/error traffic, and displays actual metrics and health/info.
It also supports `stop`, `status`, `smoke`, `traffic`, `metrics`, `health` and `logs`; see the
[sample guide](https://github.com/ih0r-d/polyglot-platform/blob/main/samples/spring-boot-example/README.md).
Existing stats, library and forecast endpoints remain available. For manual Maven startup:

```bash
./mvnw -B -ntp -pl runtime/polyglot-spring-boot-starter -am install -DskipTests
./mvnw -f samples/spring-boot-example/pom.xml spring-boot:run \
  -Dspring-boot.run.profiles=observability
```

The sample consumes its committed `graalpy.lock`; package re-locking is an explicit Maven goal,
not an ordinary build/test step. The opt-in `observability` profile binds to
`127.0.0.1` and exposes only `health`, `info` and `metrics`; it does not change the base sample's
configuration or add an exporter.

In another terminal, send two successful requests and one deliberately invalid forecast request:

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/info
curl -fsS 'http://127.0.0.1:8080/stats/random?n=3'
curl -fsS 'http://127.0.0.1:8080/stats/data?n=3'
curl -i -X POST 'http://127.0.0.1:8080/forecast?steps=0&seasonPeriod=4' \
  -H 'Content-Type: application/json' -d '[1,2,3,4]'
curl -fsS 'http://127.0.0.1:8080/actuator/metrics/polyglot.client.invocations?tag=language:python&tag=outcome:success'
curl -fsS 'http://127.0.0.1:8080/actuator/metrics/polyglot.client.invocations?tag=language:python&tag=outcome:failure'
curl -fsS http://127.0.0.1:8080/actuator/metrics/polyglot.client.invocations
```

On a fresh application these requests produce success `COUNT=2`, failure `COUNT=1` and aggregate
`COUNT=3`. The invalid forecast raises Python `ValueError` and returns the existing HTTP 500;
the sample logs the exception. `TOTAL_TIME` and `MAX` are seconds, vary per run, and include
invocation/conversion rather than HTTP processing. `TOTAL_TIME / COUNT` is the recorded mean;
`MAX` is a Micrometer time-window maximum. The aggregate response exposes only the `language`
and `outcome` tags, with at most four combinations regardless of how many clients exist.

`/actuator/info` reports `python.available=true` and `executors.availableCount=1`. Startup health
remains `UP` after the business failure: it does not probe every invocation. This sample uses Gson,
so health statuses appear as objects with `code`, and each component has a `health` wrapper.
Do not expose operational endpoints publicly without application security. The
[Actuator metrics endpoint](https://docs.spring.io/spring-boot/4.1/api/rest/actuator/metrics.html)
is diagnostic, not a production metrics backend.

Restart with `-Dspring-boot.run.arguments=--polyglot.metrics.enabled=false` to disable the invocation
Timer; its metric endpoint then returns 404, while Python calls, health and info still work.
The sample's HTTP integration tests verify both modes and executor shutdown:

```bash
./mvnw -B -ntp -f samples/spring-boot-example/pom.xml test
```

Each test class uses a fresh JVM because the sample's NumPy native extension cannot be loaded
into a second default GraalPy Context in the same process. See the
[observability benchmark](https://github.com/ih0r-d/polyglot-platform/blob/main/benchmarks/README.md#spring-client-observability-overhead) for warm
invocation measurement boundaries and runtime requirements.

Starter behavior:

- Unspecified values in a partially configured property group retain the documented defaults. For
  example, setting only `polyglot.python.enabled=true` keeps `classpath:python`, recommended Python
  defaults, and the enabled core/fail-fast settings. `polyglot.core.enabled=false` disables the
  starter's language auto-configurations as well as its core lifecycle and facade.
- `polyglot.core.fail-fast=true` eagerly instantiates discovered `@PolyglotClient` beans during
  startup so invalid bindings fail before the application begins serving requests. Binding checks
  resolve the script and export without calling bound business methods; evaluating the Python module
  and constructing an exported class can still execute initialization code.
- The default is `true`. Setting `polyglot.core.fail-fast=false` defers validation of otherwise
  unrequested clients, although injecting a client into an eager singleton can still validate it
  during startup. This does not disable context creation, warmup, or configured preload scripts.
- This runtime validation is distinct from the opt-in Maven `polyglot:validate-contracts` goal. The
  Maven goal statically checks a supported Java/Python source subset without creating a GraalVM
  context or executing Python; see [Code Generation](codegen.md#opt-in-java-to-python-contract-validation).
- `polyglot.core.log-metadata-on-startup=true` emits a single startup summary at the configured
  `polyglot.core.log-level`.
- `polyglot.python.preload-scripts` and `polyglot.js.preload-scripts` evaluate the listed logical
  scripts during startup after warmup. These names are resolved through the configured
  `ScriptSource`; they are not interface names, they do not populate interface caches, and repeated
  evaluation or repeated script side effects remain possible on later contract binding.
- `polyglot.python.safe-defaults=true` applies the starter's recommended GraalPy defaults. When
  disabled, the starter creates a more minimal Python context and expects applications to provide
  their own `PolyglotContextCustomizer` beans if they need additional options.

### Shared Engine in Spring Boot

Configure Python and provide the existing customizer SPI. Restore the access/Python defaults
explicitly:

```properties
polyglot.python.enabled=true
polyglot.python.safe-defaults=false
polyglot.python.resources-path=classpath:python
```

The configuration below
declares an executor bean with an explicit Engine dependency so Spring closes that executor
before the Engine. The starter backs off for this bean and keeps its normal client, warmup,
preload and fail-fast integration. This uses supported helper/executor/customizer APIs rather
than depending on Spring wiring implementation classes; no new starter property or Engine
auto-detection is involved.

```java
@Configuration(proxyBeanMethods = false)
class SharedPythonEngineConfig {
    @Bean(destroyMethod = "close")
    Engine pythonEngine() {
        return Engine.newBuilder("python")
            .option("engine.WarnInterpreterOnly", "false").build();
    }

    @Bean
    PolyglotContextCustomizer sharedPythonEngine(Engine pythonEngine) {
        return (language, builder) -> {
            if (language == SupportedLanguage.PYTHON) {
                builder.engine(pythonEngine)
                    .allowAllAccess(true)
                    .allowExperimentalOptions(true)
                    .option("python.WarnExperimentalFeatures", "false");
            }
        };
    }

    @Bean(destroyMethod = "close")
    @DependsOn("pythonEngine")
    PyExecutor pyExecutor(
            @Qualifier("pyScriptSource") ScriptSource scriptSource,
            ObjectProvider<PolyglotContextCustomizer> customizers) {
        Context context = PolyglotHelper.newContext(SupportedLanguage.PYTHON, false,
            builder -> customizers.orderedStream()
                .forEach(c -> c.customize(SupportedLanguage.PYTHON, builder)));
        return PyExecutor.createWithContext(
            context, scriptSource, ContextOwnership.OWNED_BY_EXECUTOR);
    }
}
```

Use Spring's `org.springframework.context.annotation` annotations and
`org.springframework.beans.factory.ObjectProvider`, plus
`org.springframework.beans.factory.annotation.Qualifier`. The SPI type is
`io.github.ih0rd.polyglot.annotations.spring.context.PolyglotContextCustomizer`;
`ScriptSource` is `io.github.ih0rd.polyglot.model.config.ScriptSource`. Runtime imports are the
same as in the core recipe above.

For an Engine shared across separate Spring applications, its external owner must keep it alive
until **all** those applications close. Register only customizers referencing that Engine; do not
let an individual application's shutdown close it. Any additional Context created manually
remains the caller's responsibility and must close before its Engine. Default starter
configuration continues to create independent Engines and use the existing lifecycle.

## Operational Behavior

If actuator is present, the starter can expose:

- a health indicator based on recorded startup initialization and shutdown state
- an info contributor with configuration and executor availability

If Micrometer is present, the starter registers gauges for:

- executor enablement
- source cache size
- Python instance cache size
- count of Python bound interfaces
- count of JavaScript loaded interfaces

Spring-managed client calls also expose bounded invocation timers; see
[optional metrics and health](#optional-metrics-and-health) for states, timing boundaries and flags.

## Current Runtime Limits

- The runtime is intended for trusted application embedding, not untrusted sandbox execution.
- JavaScript execution is supported as an experimental bounded path, but JavaScript contract
  generation is not.
- Startup preloading evaluates named scripts but does not provide hot reload or source versioning.
- Startup preloading is raw script evaluation, not true contract prebinding.
- The binding convention is currently opinionated and name-based; alternative conventions are not implemented yet.
- Starter-managed executors are shared singleton beans that serialize access to the underlying
  GraalVM `Context`. They are intended for concurrent Spring application use, but contract loading
  and cache invalidation remain per-executor concerns.
- Source caching is keyed by Java interface. Python target instances are cached per interface using
  weak references. Changing a script after startup does not trigger automatic reload; clear caches
  or recreate the executor if you need to pick up changed sources.

## Optional experimental console (Java 25)

Add the separate console artifact to a Spring Boot 4.1 servlet MVC application:

```xml
<dependency>
  <groupId>io.github.ih0r-d</groupId>
  <artifactId>polyglot-spring-boot-console</artifactId>
  <version>0.5.0-SNAPSHOT</version>
</dependency>
```

It includes the existing starter and Actuator integration, while the regular
`polyglot-spring-boot-starter` has no console dependency. The console alone requires Java 25;
existing API/runtime modules keep their Java 21 compilation baseline. WebFlux and Native Image
console support are unverified. MVC must already be available in the consuming application.

```yaml
polyglot:
  console:
    enabled: true             # default false
    path: /polyglot-console   # absolute non-root path; letters/digits/_/- segments
    refresh-interval: 5s      # 1s to 1m
```

Open this path on the application's own port. Custom servlet context paths are respected.
All assets come from the dependency JAR. The private console `/data` endpoint reads existing
Micrometer snapshots and the polyglot HealthIndicator in-process: it makes no Actuator HTTP
requests and requires no `management.endpoints.web.exposure` changes. It does not instrument
calls, execute Python, initialize contexts or expose arbitrary Actuator responses. Configuration
metadata for the three properties is packaged in the JAR.

The console is an experimental local developer tool, not persistent production monitoring.
It restricts both UI and data to loopback peers/local host names and adds same-origin CSP and
no-store headers. Existing Spring Security filters still control every request. Applications
must authorize their intended developers for the configured console path and all descendants;
no SecurityFilterChain, permit-all rule, CSRF exception or credentials are supplied. Do not
expose it through a public proxy. Loopback access is not authentication, and trusted proxy/
forwarded-header configuration remains the application's responsibility.

Only known polyglot/JVM meter names, allowed scopes, lifecycle status, context identity,
JVM identity tags and allowlisted booleans are returned. Missing metrics remain unavailable. Means use matching
COUNT/TOTAL_TIME; MAX is time-window dependent. Charts retain at most 120 browser-session
intervals, clear on observed counter decreases/context restarts, and pause in hidden tabs.
They do not recover history or estimate percentiles. Health reports recorded startup state,
not business-method liveness.

The [Spring Boot sample](https://github.com/ih0r-d/polyglot-platform/blob/main/samples/spring-boot-example/README.md)
consumes this artifact and demonstrates real NumPy traffic. Its CLI still uses exposed Actuator
endpoints independently; the console integration test closes those HTTP endpoints and verifies
real client calls through a custom console path and servlet context path.

Console metric allowlist (the collector does not register any meters):

| Metric | Scope |
| --- | --- |
| `polyglot.client.invocations` | Python/JS × success/failure; count, total seconds, window max seconds |
| `polyglot.executor.configured.count` | Global |
| `polyglot.executor.available.count` | Global |
| `polyglot.startup.duration` | Global, milliseconds |
| `polyglot.executor.enabled` | Per language, actual 0/1 gauge |
| `polyglot.executor.source.cache.size` | Per language |
| `polyglot.python.instance.cache.size` | Python DEFAULT bindings |
| `polyglot.python.bound.interfaces.count` | Python DEFAULT bindings |
| `polyglot.js.loaded.interfaces.count` | Existing experimental JS bindings |
| `jvm.info` | Only `version`, `vendor`, `runtime` identity tags |
| `process.uptime` | JVM uptime, seconds |
| `jvm.memory.used`, `jvm.memory.committed` | Sum of distinct heap pools, bytes; non-heap excluded |
| `jvm.threads.live` | Live platform threads |

The JVM panel reads the existing [Micrometer JVM binders](https://docs.micrometer.io/micrometer/reference/reference/jvm.html).
It never registers additional meters or infers the JVM distribution from GraalPy dependencies.
Heap is displayed in MiB/GiB and excludes native memory; committed heap is not maximum heap.
Missing, invalid or ambiguous meters stay unavailable, including incomplete heap totals when a
reported pool cannot be read. Disabled JVM instrumentation is respected. Paths, launch arguments,
environment variables and arbitrary meter tags are never returned.

`Startup hook (ms)` is the starter's recorded lifecycle-hook duration, rounded down to milliseconds,
not full application or GraalPy startup. Eager client creation may initialize Python before this
hook, so zero is a valid observation and does not mean that Python startup was free.

A regression test registers the real runtime binder and compares every gauge name/language
scope with the console snapshot. This prevents silently losing newly added runtime metrics.
Java code is organized into `autoconfigure`, `properties`, `internal/dto`, `internal/metrics`
and `internal/web`; each DTO has its own file. The current web adapter is explicitly Servlet MVC
only. A reactive application does not activate it; WebFlux needs a separately implemented/tested
adapter that can reuse the collector, DTOs and assets. No reactive support is implied by having
Spring WebFlux on the classpath.

Language-specific console rows honor the existing `polyglot.core.enabled`,
`polyglot.python.enabled` and `polyglot.js.enabled` settings. A preregistered zero-count Timer
does not imply that its language is enabled. Disabled languages are omitted from invocation
and runtime-gauge rows; enabled languages retain their real zero counts. No console-specific
language flags are required. If effective polyglot configuration is unavailable, only global
gauges are advertised.

After an enabled embedded application reaches `ApplicationReadyEvent`, the console logs an
INFO URL using its actual bound port, servlet context path and configured console path.
The link uses localhost because console access is local-only. Application authorization still
applies. No startup URL is announced for a disabled console, a different application context,
a failed startup or a context without an embedded server.
