# Java / Python invocation baseline

This standalone JMH 1.37 project is deliberately outside the root reactor. It adds no runtime
production dependencies and makes no changes to executor behavior. It inherits the repository's
Java release and GraalPy dependency management. Use the pinned Oracle GraalVM **25.0.4.1.1**;
verify the JDK `release` file, not a `latest` download selector.

## Build and run

From the repository root, activate `sdk env`, then install the current reactor artifacts:

```bash
./mvnw -B -ntp -pl runtime/polyglot-adapter -am install -DskipTests
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

`run.sh` forwards all arguments to JMH and uses `JAVA_HOME/bin/java` for the host and forks.
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
  `PolyglotClientFactoryBean.getObject()` validates and returns this same proxy; Spring scanning,
  application startup and metrics infrastructure are not measured here.
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

JMH follows the [OpenJDK JMH harness and samples](https://github.com/openjdk/jmh).
See [BASELINE.md](BASELINE.md) for the recorded local measurements and limitations.
