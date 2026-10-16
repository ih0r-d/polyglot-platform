# Internal Roadmap

Reviewed 2026-10-16 at `e1e913a737ee6793f0cc2de35c258534ea90abac` (`main`,
`0.5.0-SNAPSHOT`); release baseline `v0.4.0` (`6cb4a3e`). This is the existing internal
roadmap, not a new release promise. No implementation or upgrade is authorized by this note.

Only `ROADMAP.md`, `NEXT-STEPS.md` and `STRATEGY.md` are currently tracked by Git. The four
existing policy/architecture/vision/exit-criteria notes are ignored by `.gitignore`; they are
local references, not new publication artifacts. This update does not change tracking policy.

## 1. Current State And Reconciliation

| Status | Work | Repository evidence / remaining boundary |
| --- | --- | --- |
| Completed | Context ownership | `e5c4e5e`; `ContextOwnership`, executor overloads and ownership/close tests in `PyExecutorTest` / `JsExecutorTest`; legacy overloads retain ownership |
| Completed | Script-name/path hardening | `87a49ae`; model `ScriptNames` and built-in sources reject unsafe names; do not undo validation for compatibility |
| Completed | Valid `JAVA_HOME` preservation | `3129dd4`; `.dev/lib/common.sh`, Bash regression suite and Taskfile integration |
| Completed | Generated LF source compatibility | `0e30467`; `JavaInterfaceGeneratorTest` v0.4.0 goldens and `PolyglotCodegenCheckMojoTest`; historical CRLF drift remains a known limitation |
| Completed, bounded | Java contract validation | `cc9b69d`; JavaParser-based validation, generation tests; this does not establish general Python syntax support |
| Completed, decisions open | API and compatibility documentation | `e1e913a`; `docs/public-api.md`, `docs/stability.md`, `docs/compatibility.md`; codegen/factory classifications explicitly pending |
| Implemented, retain | Lifecycle/cache/preload semantics and tests | `docs/runtime-semantics.md`; `PyExecutorRealContextIntegrationTest` covers raw preload, side effects, cache clearing and explicit reload; unit tests cover serialization, close waiting, interruption and weak references |
| Implemented, bounded | Architecture checks | `RepositoryArchitectureTest` checks POM direction/cycles; API/build-tools/runtime ArchUnit tests exist. Full exported-signature/internal-leak enforcement is not established |
| Implemented, retain | Maintained sample CI | `.github/workflows/ci.yml` verifies three Maven samples; AOT fat-JAR/native jobs are manual-dispatch only |
| Active / not implemented | japicmp gate | No japicmp plugin/profile in root POM; begin with agreed API modules, then runtime/starter |
| Blocked on maintainer decision | Codegen Java API compatibility | `JavaTypeRenderer.render(PolyType)` changed `String` to JavaParser `Type`, `getImports/reset` removed since v0.4.0; AST and generator SPI boundaries unresolved |
| Planned / not implemented | Repeatable bridge performance baseline | No dedicated checked-in benchmark entry point identified; correctness tests are not performance evidence |
| Obsolete | 0.3.x release checklists and Java 17 / Boot 3 prerequisites | Current root release target is Java 21, Graal dependencies 25.0.2, Boot 4.1.0; old targets are deferred, not 1.0 gates |
| Duplicated | Lifecycle, strictness, sample CI and policy work repeated across planning notes | Keep status here, ordered tasks in `NEXT-STEPS.md`, product policy in `docs/`; other planning notes describe strategy/gates only |

The previous audit reported successful verify/quality/sample checks after the source fix. Those
are historical results, not tests rerun during this planning-only review. There is no newly
verified runtime regression here. Five existing user-modified files remain outside this task.

## 2. Roadmap Principles And Ownership

- Python-first, framework-independent Java core; Spring Boot is the primary framework integration.
- `ROADMAP.md`: status, priorities, research and decisions. `NEXT-STEPS.md`: ordered reviewable work.
- `STRATEGY.md` and `1.0.0-VISION.md`: product scope. `1.0.0-EXIT-CRITERIA.md`: release gates.
- `COMPATIBILITY-POLICY.md` and `ARCHITECTURE-RULES.md`: planning constraints; product API and
  compatibility policy live in `docs/`, not in competing planning specifications.
- Prefer additive changes and reproduce a defect before changing behavior. A completed task is not
  automatically reopened because an older note still lists it.

## 3. Development Priorities

| Priority | Workstream | Outcome |
| --- | --- | --- |
| P0 | Backward compatibility automation and explicit break decisions | Fixed v0.4.0 baseline, supported API/SPI allowlist, failing binary/source gate; no blanket helper exclusions hiding breaks |
| P0 investigation / P1 coverage | Runtime correctness, lifecycle, concurrency and caching | Verify full invocation/conversion and close boundaries; preserve serialization, weak caches and explicit reload semantics |
| P1 | Safe internal cleanup | One proven duplication per PR, no public utility removal or source-loading redesign |
| P2 research; security evaluation can become P0 | GraalVM/GraalPy modernization | Evidence-based patch-track evaluation before feature-track migration; no version change merely for freshness |
| P2 | Java/Python interoperability performance | Reproducible cold/warm conversion and invocation baseline before optimization |
| P2 | Spring Boot developer experience | Existing-property and failure-diagnostic coverage on Boot 4.1.0, no new framework abstraction |
| P3 deferred | New frameworks and expanded JavaScript/features | Outside this stabilization horizon; no implementation queue |
| P1 release gate | 1.0 readiness | Resolved boundaries, compatibility enforcement, correctness evidence, benchmark baseline and one stabilization cycle |

## 4. GraalVM / GraalPy Research

Research snapshot: 2026-10-16. Official release pages and GitHub assets can report different
component versions. Resolve exact Maven artifacts, distribution, edition and matching language
versions before any experiment; do not infer artifact availability from a JDK release number.
No candidate below has been benchmarked or validated in this repository during this task.

### Version Comparison

- Current: `pom.xml` / runtime BOM use Graal components `25.0.2`; `.sdkmanrc` names
  `25-graalce` without an exact patch. `PolyglotHelper` already uses `GraalPyResources`,
  `VirtualFileSystem` and `Context.Builder`; there is no legacy embedding rewrite to perform.
- Oracle's 25.0 LTS notes list **25.0.4.1.1**, a security update dated 2026-09-15. This is a
  patch-track candidate, conditional on matching embeddable artifact availability and edition.
  [Oracle release notes](https://docs.oracle.com/en/graalvm/jdk/25/docs/release-notes/).
- The upstream GraalPy release list exposes **25.4.4**, Python 3.13, dated 2026-09-22;
  it also lists **25.0.3**, **25.1.3**, **25.2.4**, and **25.3.4.1**. Since 25.0.3, JVM
  standalone downloads are removed; custom Java embedding remains the relevant deployment here.
  [GraalPy releases](https://github.com/oracle/graalpython/releases/).
- GraalVM's innovation notes currently name **25.4.4.1.1**; this is not identical to that
  GraalPy download tag. **25.3.4.1** changes the Python language/stdlib to 3.13 and adds Native
  Image monitor improvements. Keep the language migration separate from a patch evaluation.
  [25.4 notes](https://www.graalvm.org/release-notes/25.4/),
  [25.3 notes](https://www.graalvm.org/release-notes/25.3/).
- From **25.1.3**, the optimizing runtime requires GraalVM 25.1 or later; a Java 21 fallback
  compile/test lane does not prove optimizing execution. Host JDK and language migration must be
  evaluated together. [25.1 notes](https://www.graalvm.org/release-notes/25.1/).

### Candidate Evaluations

Each row is a bounded research task, not a supported feature claim. Version is the exact research
anchor; features already present on 25.0.2 do not justify upgrading.

| Candidate / version / official source | Current path and practical use | Risk and minimal PoC / benchmark | Recommendation |
| --- | --- | --- | --- |
| Patch-track **25.0.4.1.1**; [Oracle notes](https://docs.oracle.com/en/graalvm/jdk/25/docs/release-notes/) | Current 25.0.2 predates security/Native Image fixes; evaluate maintained deployments, not new API scope | Confirm artifact/CE availability and dependency alignment; compare 25.0.2 vs matching patch on verify, quality, samples and native smoke; inspect SBOM/advisories for actual exposure | **Experiment** first; adopt only after availability and gates pass, expedite if applicable security exposure is confirmed |
| Short interop GIL control, **25.1.3**; [release notes](https://www.graalvm.org/release-notes/25.1/) | `polyglot.gil_locked_during_interop` can reduce lock transitions for short Java calls from Python; current executor serializes context access | Deadlock with blocking callbacks. Benchmark scalar/map callbacks with control on/off and a bounded callback test; never enable globally or remove executor locks | **Experiment** after baseline; no throughput guarantee |
| Foreign buffers, **25.1.3**; [release notes](https://www.graalvm.org/release-notes/25.1/) | Python binary APIs accept foreign buffers; potential bulk numerical payload path instead of boxed element calls | Mutability, ownership, endianness and context lifetime. Compare copied bytes vs Java ByteBuffer to `memoryview` at 1 KiB/1 MiB; verify mutations, bounds, close behavior and allocations; zero-copy remains a hypothesis | **Experiment** without adding generated buffer types |
| Host/guest conversion, **25.0.2** baseline; [Polyglot embedding guide](https://www.graalvm.org/jdk25/reference-manual/embed-languages/) | Proxy invocation currently uses `Value.as(method.getReturnType())`; inspect scalar/list/map and generic erasure before changing mappings | Add correctness probes for null, numeric overflow and nested collections, then compare direct Value calls with bound interface calls; preserve return/exception behavior | **Adopt** correctness measurements; defer new mapping API until a demonstrated gap |
| Explicit Engine/source reuse, **25.0.2** baseline; [code caching guide](https://www.graalvm.org/jdk25/reference-manual/embed-languages/) | Helper creates contexts without explicit shared Engine; repeated short-lived contexts may repeat warmup | Engine/Source references affect lifetime and memory. Compare isolated engines vs caller-owned shared Engine across 10 sequential contexts; report cold/first/warm time and RSS, close all owned resources | **Experiment** via existing builder customizer; defer pooling, singleton or default changes |
| Embedding resources/packages, **25.3.4.1** docs anchor; [JVM embedding docs](https://graalpy.org/jvm-developers/docs/), [package matrix](https://graalpy.org/python-developers/compatibility/) | Existing VFS path is already modern; package/native-extension needs may require external resources or a POSIX backend choice | Package compatibility is OS/ABI/version specific. In disposable consumer compare current VFS and external resource deployment with the exact sample package pins; import and calculate a known result, capture install failures separately | **Experiment** only for a concrete package failure; **Reject** unconditional VFS replacement |
| Python **3.13 / GraalPy 25.3.4.1**; [release notes](https://www.graalvm.org/release-notes/25.3/) | Current GraalPy 25.0.2 follows Python 3.12; potential newer package availability | Language/stdlib/wheel migration can alter behavior. Re-run unchanged contract fixtures and selected pinned NumPy workload on the matched toolchain, compare values and errors; do not infer free-threading from Python version | **Defer** default migration until patch evaluation and package evidence |
| Startup tuning, **25.1.3**; [release notes](https://www.graalvm.org/release-notes/25.1/) | Launcher `-X jit=0/1/2` presets target startup/throughput; embedded helper is not the launcher | Do not copy launcher flags into Context options. Measure separate-process launch vs equivalent embedded cold/first/warm calls, verify supported embedding options for exact version | **Defer** tuning adoption; investigate only if startup measurements warrant it |
| Native Image, **25.3.4.1** and **25.4.4.1.1**; [25.3](https://www.graalvm.org/release-notes/25.3/), [25.4](https://www.graalvm.org/release-notes/25.4/) | Manual AOT sample exists; monitor optimizations may help serialized calls, footprint changes may help deployment | Reflection/resources/C extensions and crypto dependency changes can break images. Build/run unchanged sample on each matching tuple; compare build time, binary size, cold execution and RSS. 25.4 removes default BouncyCastle support; assess existing exclusions separately | **Experiment** after JVM checks, no broad AOT support claim |

The GIL, guest threads and executor monitor solve different problems. Retain serialized access to
one context. Shared caller contexts across executors require explicit coordination; ownership does
not provide a shared lock. No evidence here justifies free-threading, default pooling, automatic
reload, isolates, or sandbox support. Default trusted-script access remains unchanged.

## 5. Open Decisions And Deferred Scope

1. Resolve `JavaTypeRenderer` legacy break and `generateAst` JavaParser exposure; do not hide them
   by relabeling shipped public classes internal. Decide generator/mapper SPI and Spring factory
   boundaries using `docs/public-api.md` pending recommendations.
2. Agree japicmp supported-member allowlist, inherited-method coverage, explicit exclusions and
   baseline update rule. Neither annotation presence nor entire-package exclusion is sufficient.
3. Decide coordination policy for a caller sharing a Context; investigate conversion outside the
   executor lock before claiming a regression or choosing a remedy.
4. Choose patch-track artifact/edition tuple and applicable security response before upgrading;
   innovation-track host-JDK requirements and Python 3.13 are a separate decision.
5. Benchmark results must justify optimization; there is no measured latency/throughput target yet.

Defer new framework integrations, expanded JavaScript/runtime or codegen support, Java 17/Boot 3
matrices, automatic hot reload, multi-version isolation, context pools and execution-domain
expansion. These are not required for 1.0. See `NEXT-STEPS.md` for the next ten bounded tasks.
