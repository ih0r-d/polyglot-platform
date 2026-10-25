# Java / Python invocation baseline

This standalone JMH 1.37 project is deliberately outside the root reactor. It adds no runtime
production dependencies and makes no changes to executor behavior. It inherits the repository's
Java release and GraalPy dependency management. Use the pinned Oracle GraalVM **25.0.4.1.1**;
verify the JDK `release` file, not a `latest` download selector.

## Build and run

From the repository root, select the pinned JDK through `JAVA_HOME` or `sdk env`
([setup options](../docs/developer-tooling.md#pinned-oracle-graalvm)), then install current artifacts:

```bash
./mvnw -B -ntp -pl runtime/polyglot-spring-boot-starter -am install -DskipTests
./mvnw -B -ntp -f benchmarks/pom.xml package dependency:build-classpath \
  -Dmdep.outputFile=target/classpath.txt
benchmarks/run.sh InvocationBenchmark -bm avgt -prof gc -foe true \
  -rf json -rff benchmarks/target/average.json
benchmarks/run.sh InvocationBenchmark -bm thrpt -tu s -prof gc -foe true \
  -rf json -rff benchmarks/target/throughput.json
benchmarks/run.sh StartupBenchmark -foe true \
  -rf json -rff benchmarks/target/startup.json
benchmarks/run.sh ContextCreationBenchmark -foe true \
  -rf json -rff benchmarks/target/context-creation.json
benchmarks/run.sh ContextCreationBenchmark -p strategy=shared,independent -foe true \
  -rf json -rff benchmarks/target/context-creation-repeat.json
```

For a direct comparison of a top-level Python callable and the bound `BY_METHOD_NAME` path:

```bash
benchmarks/run.sh 'InvocationBenchmark.(directTopLevelValue|boundMethodNameClient)$' \
  -p size=8 -bm avgt -tu us -prof gc -foe true \
  -rf json -rff benchmarks/target/method-name-average.json
benchmarks/run.sh 'InvocationBenchmark.(directTopLevelValue|boundMethodNameClient)$' \
  -p size=8 -bm thrpt -tu s -prof gc -foe true \
  -rf json -rff benchmarks/target/method-name-throughput.json
```

Reinstall the reactor artifacts after every runtime edit or revert: the standalone project resolves
installed SNAPSHOT jars, and root `verify` alone does not refresh them. A stale jar invalidates a
committed-baseline comparison even when the source tree is clean.

`run.sh` uses the shared developer Java resolver, verifies the exact Oracle patch from the JDK
`release` file, forwards all arguments to JMH, and uses the selected `JAVA_HOME/bin/java` for
the host and forks. A mismatched runtime fails before JMH starts.
Do not run Maven, tests or other benchmarks concurrently with measurement. Preserve JSON results,
command lines, Git SHA, JVM version/options, OS, CPU and RAM with each report. Run on an idle
machine using a consistent power mode. Results are local observations, not CI performance gates.

The benchmark annotation defaults use one thread, two forks, five one-second warmup iterations
and five one-second measurement iterations. Startup uses five independent forks with no warmup
and exactly one operation per fork. Repeated Context creation uses five forks, five single-operation
warmup iterations and five single-operation measurements per fork; it closes each Context outside the timed method.
Each fork uses `-Xms512m -Xmx2g`. For publication or small
regressions, increase durations/forks and inspect raw iteration variability; do not treat a short
warmup as proof that guest compilation has converged.

## What is measured

- `javaCall`: increment on a changing input; Java may inline the method, as in normal application code.
- `directValue`: cached Python bound-method `Value.execute`, converting the result to Java `int`.
- `directGenericConversion`: cached callable with `Value.as(int.class)`, including boxed result handling.
- `directMemberLookup`: resolve the bound method on each call, then the same generic conversion.
- `boundClient`: the same operation through `PyExecutor.bind` on an annotated interface. Spring's
  `PolyglotClientFactoryBean` returns this core proxy when invocation metrics are inactive and wraps
  it when a metrics observer is available. This case does not measure Spring or its metrics wrapper.
- `directTopLevelValue` and `boundMethodNameClient`: direct invocation versus Python's
  `BY_METHOD_NAME` convention for the same top-level function. The bound path includes source
  loading/evaluation when the contract has not already been loaded. These cases do not measure
  Spring startup.
- `javaTransfer`, `directTransfer`, `boundTransfer`: add one to every element, return a new Java
  `int[]`; input sizes 8 and 1024. Python iterates a host array and returns a guest list converted
  with `Value.as(int[].class)`. Both boundary directions and the Python loop are included; this is
  not a pure memcpy or zero-copy bandwidth measurement.
- `initializedContext`: `PolyglotHelper.newContext(PYTHON)`, including VFS, implicit Engine creation,
  context construction and Python initialization in a fresh JVM (JVM process launch is excluded). Close runs outside the timed call.
- `firstModuleEvaluation`: define the small Python contract in an already initialized context.
- `firstStdlibImport`: first `import json` in an already initialized context. Imports performed by
  GraalPy initialization are outside this operation; this is not a fully cold filesystem import.

`ContextCreationBenchmark` compares `strategy=independent` (a new implicit Engine per Context)
with `strategy=shared` (one caller-owned Engine per trial). Both explicitly reproduce the helper's
Python defaults through its existing overload. Engine-level options are set on the Engine for the
shared strategy. Each measurement creates and initializes one new Python Context, including VFS
creation; no business methods/imports are added. Context close is outside timing and Engine close
runs after all Contexts. Shared Engine construction is outside timing: this measures repeated
creation, not total application startup or the first Engine. Warmup does not prove complete guest
compilation convergence. Reverse strategy order for a repeat and compare uncertainty before
claiming a benefit. Default runtime behavior is unchanged. See the
[runtime recipe](../docs/runtime.md#opt-in-shared-python-engine) and recorded results in
[BASELINE.md](BASELINE.md#shared-engine-context-creation).

Steady-state setup initializes one owned Context, validates/binds once, resolves direct callables
and checks equivalent results before measurements. No imports, Context construction or deliberate
cache eviction occur in timed invocation methods. Executor weak-reference cache behavior remains
unchanged; GC may cause target recreation. Java baselines also perform untimed context setup to
keep fixture configuration consistent. All results are returned for JMH consumption.

The GC profiler reports JVM allocation estimates (bytes/op), not Python retained heap or native
memory. Startup and steady-state scores use different modes/units and must not be combined into
one average. Throughput is measured in a separate run, not computed from latency. Scalar scores
are repeated for each size parameter; `size` only affects transfer operations.

## Spring client observability overhead

`SpringObservabilityBenchmark.invoke` measures a warm `BY_METHOD_NAME` call through the actual
Spring `PolyglotClientFactoryBean`, including its optional Micrometer wrapper. Both parameter arms
use the same Python function, bound interface, Context/Spring configuration and `SimpleMeterRegistry`;
only `polyglot.metrics.enabled` differs. Setup checks the Python return value and exactly four
invocation Timers when enabled, zero when disabled. Binding, startup validation, Context construction,
HTTP handling and registry construction are outside timing. The return value is consumed by JMH.
This measures the successful-call path, not exception or exporter overhead.

After building the current starter and benchmark as above, use the pinned Oracle GraalVM:

```bash
benchmarks/run.sh SpringObservabilityBenchmark -p metricsEnabled=false,true \
  -bm avgt -tu ns -prof gc -foe true \
  -rf json -rff benchmarks/target/observability-average.json
benchmarks/run.sh SpringObservabilityBenchmark -p metricsEnabled=true,false \
  -bm avgt -tu ns -prof gc -foe true \
  -rf json -rff benchmarks/target/observability-average-repeat.json
benchmarks/run.sh SpringObservabilityBenchmark -p metricsEnabled=true,false \
  -bm thrpt -tu s -prof gc -foe true \
  -rf json -rff benchmarks/target/observability-throughput.json
benchmarks/run.sh SpringObservabilityBenchmark -p metricsEnabled=false,true \
  -bm thrpt -tu s -prof gc -foe true \
  -rf json -rff benchmarks/target/observability-throughput-repeat.json
```

Defaults are two forks per arm, five one-second warmup and five one-second measurement iterations,
one thread, `-Xms512m -Xmx2g`. Check confidence intervals and raw fork variation; a lower mean alone
does not establish an improvement. Allocation numbers are JVM bytes/op, not guest/native heap.
See [recorded results](BASELINE.md#spring-observability-diagnostic) for environment limitations.
If the pinned runtime is unavailable, the runner deliberately refuses to start. A separately labeled
HotSpot/interpreter diagnostic can invoke JMH directly with the same classpath and parameters:

```bash
"$JAVA_HOME/bin/java" -cp "benchmarks/target/classes:$(cat benchmarks/target/classpath.txt)" \
  org.openjdk.jmh.Main SpringObservabilityBenchmark -jvm "$JAVA_HOME/bin/java" \
  -p metricsEnabled=false,true -bm avgt -tu ns -prof gc -foe true \
  -rf json -rff benchmarks/target/observability-hotspot-average.json
```

Such results are not a substitute for Oracle GraalVM validation and must not be compared to the
historical compiled-GraalPy nanosecond baseline. The HTTP demo and its enabled/disabled tests are
documented in the [runtime guide](../docs/runtime.md#run-the-http-observability-demo).

## Data Transfer Investigation

`DataTransferBenchmark` uses representative `int[]` and `ArrayList<Integer>` inputs, heap/direct
`ByteBuffer`, and a prebuilt Python list at sizes 100, 1,000 and 100,000. Setup checks array/buffer
contents, the view's last element and the Python sum. A guarded guest export keeps the prebuilt list
stable if the executor's weak Java `Value` wrapper is collected. Runtime caches are unchanged.

- `arrayLength`: boundary/proxy control without iterating or copying the input.
- `arrayMaterialize`, `listMaterialize`: Python `list(values)` plus Java `int[]` conversion.
- `heapBufferMaterialize`, `directBufferMaterialize`: Python calls Java absolute `get(i)` per byte,
  then converts the Python list to `byte[]`. These are supported host-method copies, not Python
  buffer-protocol or zero-copy measurements. Inputs have position zero and limit equal to capacity.
- `pythonArrayResult`: return a prebuilt guest list as `int[]`, without rebuilding/transforming it.
- `pythonListResult`: return a guest-backed `List` view without consuming it; its score is not the
  cost of copying all elements or of subsequently traversing the view.
- `pythonCompute`: sum the prebuilt guest list, returning one scalar; no size-dependent host input.
- `directGuestMaterialize`: direct cached callable, returning the Python list as `Value` without
  Java array conversion. Comparing with the bound path is diagnostic, not a pure subtraction of
  independent overheads.
- `directArrayResult`, `enteredArrayResult`: identical `Value.as(int[].class)` conversion, with
  implicit versus explicit Context entry. The latter is an isolated diagnostic, not runtime policy.

The defaults match invocation benchmarks. The exploratory matrix uses shorter explicit settings
below; these runs do not establish JIT convergence or small regression thresholds. Repeat promising
candidates with the longer defaults before making a production change. Measure latency and
throughput independently; the GC profiler estimates JVM allocation, not native copies/retained heap.

```bash
benchmarks/run.sh 'DataTransferBenchmark.(arrayLength|arrayMaterialize|listMaterialize|heapBufferMaterialize|directBufferMaterialize|pythonArrayResult|pythonListResult|pythonCompute|directGuestMaterialize)$' \
  -bm avgt -tu us -f 2 -wi 3 -i 3 -w 500ms -r 500ms -prof gc -foe true \
  -rf json -rff benchmarks/target/transfer-baseline-average.json
benchmarks/run.sh 'DataTransferBenchmark.(arrayLength|arrayMaterialize|listMaterialize|heapBufferMaterialize|directBufferMaterialize|pythonArrayResult|pythonListResult|pythonCompute|directGuestMaterialize)$' \
  -bm thrpt -tu s -f 2 -wi 3 -i 3 -w 500ms -r 500ms -prof gc -foe true \
  -rf json -rff benchmarks/target/transfer-baseline-throughput.json
benchmarks/run.sh 'DataTransferBenchmark.(directArrayResult|enteredArrayResult)$' \
  -p size=100000 -bm avgt -prof gc -foe true \
  -rf json -rff benchmarks/target/transfer-entry-control.json
```

These fixtures are deliberately limited to single-threaded JVM execution with the pinned runtime.
Do not infer NumPy, all primitive types, concurrent Context access or Native Image performance.
The existing conversion contracts and buffer limitations are described in
[Runtime](../docs/runtime.md#array-collection-and-buffer-transfers).

JMH follows the [OpenJDK JMH harness and samples](https://github.com/openjdk/jmh).
See [BASELINE.md](BASELINE.md) for the recorded local measurements and limitations.
