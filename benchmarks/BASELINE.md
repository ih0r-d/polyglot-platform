# Local baseline: Oracle GraalVM 25.0.4.1.1

Recorded 2026-10-17 on runtime sources at Git HEAD
`0e86cf45a0261bde71885bc4239c6bdd0a5be122`, with the new benchmark project uncommitted.
Five pre-existing user edits (documentation and an unrelated AOT sample) were preserved.
No production optimization or dependency version upgrade was applied.

## Environment and execution

- Apple M4 Pro, 12 logical CPUs, 24 GiB RAM, Mac model `Mac16,8`, AArch64.
- macOS 26.6.2, build 25G83; no affinity, power-mode or desktop-background-load control.
- Oracle GraalVM JDK `25.0.4.1.1+1-LTS-jvmci-b01`; JDK release metadata reports both
  `JAVA_VERSION` and `GRAALVM_VERSION` as `25.0.4.1.1`.
- GraalPy Maven artifacts `25.0.4.1.1`; Python reports `3.12.8`, Oracle GraalVM, Java
  `25.0.4.1.1 (aarch64)`. Empty benchmark VFS, no third-party Python packages.
- Maven wrapper 3.9.14, Java compilation release 21, JMH 1.37.
- JVM args captured by JMH: `-XX:ThreadPriorityPolicy=1 -XX:+UnlockExperimentalVMOptions
  -XX:+EnableJVMCIProduct -XX:+EnableJVMCI -XX:-UnlockExperimentalVMOptions -Xms512m -Xmx2g`.
  The JVM warns that priority policy may be ignored without system privileges; no elevated
  scheduling was requested. Native-access and JMH Unsafe deprecation warnings did not fail runs.
- One benchmark worker, independent JVM forks. No test/build jobs ran concurrently with measurement.
  Warmup/measurement/fork defaults are in [README.md](README.md); raw JMH data is in `results/`.

The exact local `JAVA_HOME` was
`/tmp/polyglot-upgrade-jdk/graalvm-jdk-25.0.4.1.1+1.1/Contents/Home`.
Maven used `-s /tmp/polyglot-revapi-settings.xml
-Dmaven.repo.local=/tmp/polyglot-revapi-repository` because the user's default settings point to
an unavailable corporate mirror. Once JMH dependencies were downloaded, verification used `-o`.
Use your own settings/cache with the canonical commands in README; these temporary paths are not
required repository configuration.

Executed measurement commands (with the `JAVA_HOME` above):

```bash
benchmarks/run.sh InvocationBenchmark -bm avgt -prof gc -rf json \
  -rff benchmarks/target/average.json -foe true
benchmarks/run.sh 'InvocationBenchmark.(javaCall|directValue|boundClient|javaTransfer|directTransfer|boundTransfer)$' \
  -p size=8 -bm thrpt -tu s -prof gc -rf json \
  -rff benchmarks/target/throughput-small.json -foe true
benchmarks/run.sh 'InvocationBenchmark.*Transfer$' -p size=1024 -bm thrpt -tu s -prof gc \
  -rf json -rff benchmarks/target/throughput-large.json -foe true
benchmarks/run.sh 'InvocationBenchmark.(directGenericConversion|directMemberLookup|directValue|boundClient)$' \
  -p size=8 -bm avgt -prof gc -rf json -rff benchmarks/target/controls.json -foe true
benchmarks/run.sh StartupBenchmark -rf json -rff benchmarks/target/startup.json -foe true
```

The first average-time run preceded addition of the two diagnostic methods and a retained direct
lookup target to the fixture. Existing timed method bodies were unchanged. The controls run
repeated scalar direct/proxy measurements with the final fixture. Scalar scores below use that
repeat; transfer scores use the first run. Reproduce the full suite from the final fixture before
using this exploratory baseline as a regression threshold.

## Measurements

Errors are JMH's 99.9% confidence interval half-width, not standard deviation. Latency and
throughput are independent runs; throughput must not be inferred by inverting mean latency.
Allocation numbers are JVM estimates from the latency runs, not native/Python retained memory.

| Operation | Elements | Average latency, ns/op | Throughput, million op/s | JVM bytes/op |
| --- | ---: | ---: | ---: | ---: |
| Java increment (inlined) | 1 | 0.430 ± 0.011 | 2332.06 ± 17.03 | approximately 0 |
| Cached Value, `asInt()` | 1 | 42.950 ± 0.988 | 24.027 ± 0.142 | 420 |
| Cached Value, `as(int.class)` | 1 | 56.977 ± 0.865 | not measured | 434 |
| Per-call member lookup + `as(int.class)` | 1 | 75.944 ± 1.085 | not measured | 612 |
| Annotated interface via `bind` | 1 | 126.690 ± 8.626 | 6.424 ± 2.420 | 796 |
| Java array transform | 8 | 3.138 ± 0.045 | 318.332 ± 4.737 | 48 |
| Direct Python array transform | 8 | 602.655 ± 31.699 | 1.735 ± 0.007 | 624 |
| Bound Python array transform | 8 | 669.135 ± 16.921 | 1.526 ± 0.033 | 1025 |
| Java array transform | 1024 | 156.061 ± 5.518 | 6.465 ± 0.236 | 4112 |
| Direct Python array transform | 1024 | 58284.678 ± 2087.363 | 0.017605 ± 0.000214 | 37964 |
| Bound Python array transform | 1024 | 75427.747 ± 27681.364 | 0.018356 ± 0.000608 | 38518 |

| First operation in a fresh JVM | ms/op, 5 forks, one operation each |
| --- | ---: |
| Helper Context creation + Python initialization | 1389.813 ± 139.761 |
| Contract source evaluation after Context initialization | 34.741 ± 4.607 |
| First `import json` after Context initialization | 313.964 ± 149.708 |

Process launch and Context close are excluded. Import, Context initialization and steady-state
method execution are distinct workloads; subtracting their scores does not isolate Engine cost.

## Findings and next experiment

**Verified observations:**

- The scalar bound path is about 84 ns/op slower than cached direct `asInt()` in the repeated
  latency run, with about 376 additional JVM bytes/op. This is aggregate adapter overhead, not a
  measurement of monitor or reflection cost in isolation.
- Generic result conversion including boxing adds about 14 ns/op in the direct controls. Resolving
  the Python member each call adds about 19 ns/op beyond that. These are measurable contributors,
  not a complete allocation profile of the proxy path.
- Initialization and first imports are far more expensive than warmed calls. Existing singleton
  executor/Context reuse already avoids this cost during ordinary repeated invocation.
- For 1024 items, the Python loop and boundary transfers together dominate the measured work.
  No isolated evidence identifies a redundant conversion. Large-transfer proxy latency has a
  wide interval, and throughput reverses the direct/proxy ordering. No stable percentage proxy
  overhead is established for this case. Its bound allocation estimate also changes materially
  between latency and throughput runs; compiler/GC effects need investigation before attribution.

**Recommended smallest optimization experiment:** prototype a specialized `int` result-conversion
path inside the existing binding implementation, without changing caches, locking, API or defaults.
The direct conversion controls justify investigating it, but an InvocationHandler still returns
`Object`, so boxing may remain and the direct delta is not a promised proxy saving. Accept a change
only if repeated proxy A/B measurements improve and tests preserve nulls, numeric conversions,
conversion exceptions, guest exceptions and other return types. Do not implement it from these
numbers alone without that verification.

**Limitations / hypotheses:** five-second warmup is not proof of guest JIT convergence; this is one
uncontrolled desktop host, JVM mode only, no Native Image or concurrent throughput claims. Weak
instance-cache recreation under GC is possible and was not counted. GC profiler data cannot prove
which allocation site is wasteful. Spring application startup, AOP and full auto-configuration
were not benchmarked: the measured annotated client uses the same executor proxy returned by the
starter factory. More forks/longer runs are needed before defining performance budgets.

## Verification

- Standalone `spotless:apply verify dependency:build-classpath`: PASS; two Java files clean.
- Root `task quality -- -o -Pquality,api-compat` with the settings/cache above: PASS,
  381 tests, zero failures/errors/skips. This runs the Maven verify lifecycle plus quality checks.
- Revapi: annotations, model, adapter and Spring starter all compared against published `0.4.0`: PASS.
- Reactor adapter `install -DskipTests`: PASS (artifact setup only; test evidence is the quality run).
- All five JMH commands: PASS, 28 parameterized results / 65 forked JVMs, fail-on-error enabled.
- `git diff --check`, new-file whitespace checks, shell syntax and original user-file hashes: PASS.

Production configuration, project/dependency versions, public API and runtime semantics are unchanged.

## Shared Engine Context Creation

Recorded 2026-10-17 on production sources at HEAD
`617ac235c0cafa28e1f7ed2b7be1681252133da9`, with `ContextCreationBenchmark` and the
shared-Engine documentation/integration tests uncommitted. Hardware, OS, Oracle GraalVM JDK and
GraalPy versions are the same as above. No production implementation or dependency changes.
The benchmark build used the existing temporary Maven settings/cache documented above.

Commands (with the pinned `JAVA_HOME`):

```bash
benchmarks/run.sh ContextCreationBenchmark -foe true -rf json \
  -rff benchmarks/target/context-creation.json
benchmarks/run.sh ContextCreationBenchmark -p strategy=shared,independent -foe true -rf json \
  -rff benchmarks/target/context-creation-repeat.json
```

The actual runs additionally set `JAVA_TOOL_OPTIONS=-Djmh.ignoreLock=true` to bypass an existing
JMH lock; no other benchmarks/builds/tests ran concurrently. The JMH fork socket required local
sandbox permission. JVM arguments and per-fork measurements are recorded in
[context-creation.json](results/context-creation.json) and
[context-creation-repeat.json](results/context-creation-repeat.json).

Both strategies use identical access/Python settings through the existing helper overload and
perform eager Python initialization. `independent` creates one implicit Engine per Context;
`shared` creates one explicit Engine per trial. Each fork performs five single-operation warmups
and five measurements, on one thread. Context close is outside measurement; the shared Engine
closes after the last Context. Its initial construction is outside measurement. This is repeated
Context creation, not cold application startup; the existing `StartupBenchmark` is unchanged.

| Run | Independent Engine, ms/op | Shared Engine, ms/op | Mean time reduction |
| --- | ---: | ---: | ---: |
| context-creation.json | 134.014 ± 49.517 | 78.899 ± 28.062 | 41.1% |
| context-creation-repeat.json | 154.254 ± 81.704 | 68.139 ± 19.017 | 55.8% |

Errors are JMH's 99.9% confidence interval half-widths, with 25 observations across five forks per
strategy. Mean direction repeats with reversed order, consistent with the earlier isolated
experiment, but intervals overlap in both runs. These noisy desktop measurements do not establish
a precise percentage guarantee or a strict significance gate. No allocations, native memory,
steady-state invocation throughput, third-party package imports or Native Image results are
claimed for this experiment. Warmup does not establish complete guest compilation convergence.

The opt-in application-owned Engine recipe uses existing APIs and does not change library defaults.
It is useful to evaluate for applications creating many Contexts. A single default Spring executor
already reuses its Context; the benchmark is not evidence of faster first application startup.
Integration tests check isolated globals/bindings, executor-owned versus borrowed Contexts,
Engine reuse after closing Contexts, and Spring-managed/external Engine shutdown handling.

Verification for this addition: full `./mvnw -B -ntp -Pquality,api-compat verify` with the pinned
JDK and temporary settings/cache above passed (386 tests, no failures/errors/skips; all four
Revapi modules compared against published `0.4.0`). Standalone benchmark package/formatting,
strict `task docs:build`, `task docs:clean`, and `git diff --check` also passed.
