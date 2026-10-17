# Internal Roadmap

Reviewed 2026-10-17 at `2a8ff35` (`main`, `0.5.0-SNAPSHOT`); compatibility baseline `v0.4.0`
(`6cb4a3e`). This is the existing internal roadmap, not a release promise. Only the three planning
files tracked by Git are maintained here; ignored local policy/vision notes are unchanged.

## 1. Current State And Reconciliation

| Status | Work | Evidence and remaining boundary |
| --- | --- | --- |
| Completed | Context ownership and path validation | `e5c4e5e`, `87a49ae`; ownership overloads and unsafe logical script-name rejection have focused tests; preserve legacy factory defaults |
| Completed | Developer task JDK detection | `3129dd4`; valid executable `JAVA_HOME` survives macOS `/usr/bin/java` resolution; shell tests run through Taskfile |
| Completed | Generated source compatibility | `0e30467`; v0.4.0 LF output fixtures cover empty interfaces, multiple methods and nested generics; legacy CRLF output may still drift |
| Completed | Build-time Python contract validation | `4669be1`, `dca0d2e`, `0e86cf4`; module/export/symbol and supported positional arity checks, plus annotated contract discovery; parsing is intentionally static and restricted |
| Completed | Spring startup fail-fast coverage | `3abc9b4`; real Python integration tests confirm eager binding validation; module evaluation and class initialization can run, but business methods are not called |
| Completed | Java API compatibility gate | `63c338c`, `dfb1556`, `9c99502`; opt-in Revapi profile and CI job cover annotations, model, adapter and Spring starter against published `0.4.0`; codegen Java API remains outside the gate |
| Completed | GraalVM/GraalPy patch alignment | `c0ab6da`; Maven artifacts and maintained samples use `25.0.4.1.1`; exact Oracle JVM patch was locally verified, while CI uses its separate GraalVM 25 runtime selector |
| Completed, exploratory | Java/Python JMH baseline | `2a8ff35`; standalone harness and raw results are checked in; one host only, no performance gate and no optimization adopted |
| Open decision | Codegen Java compatibility | `docs/public-api.md` records `JavaTypeRenderer` source/binary break since `v0.4.0`, and pending decisions for `generateAst`, generator and mapper surfaces |
| Investigation | Shared-context concurrency boundary | Executor locking is per executor; cross-executor use of one borrowed Context and conversion/close races need a deterministic support-boundary test before any fix |
| Deferred | Innovation GraalVM/GraalPy versions and new runtime features | Current supported line remains 25.0.4.1.1; buffers, GIL controls, Python 3.13 and newer Native Image behavior need independent use cases and experiments |

Historical verification is recorded with the corresponding implementation or benchmark evidence;
it is not a claim that every gate was rerun in this documentation update. No new runtime defect is
asserted here.

## 2. Roadmap Principles And Ownership

- Keep the Java core framework-independent, Python the primary path, and Spring Boot the main framework integration.
- `ROADMAP.md` owns status, priorities and open research; `NEXT-STEPS.md` owns reviewable work order; `STRATEGY.md` owns product scope.
- User-facing guarantees live in `docs/`, especially `public-api.md`, `runtime-semantics.md`, `stability.md` and `compatibility.md`.
- Prefer additive changes; reproduce behavior risks before changing runtime contracts.
- A completed item is not reopened because an old planning entry remains; obsolete tasks are removed from the queue and evidence retained here when useful.

## 3. Development Priorities

| Priority | Workstream | Current focus |
| --- | --- | --- |
| P0 | Compatibility and public boundaries | Resolve shipped codegen API break classification and migration path; preserve the four-module Revapi gate and its narrow diagnostic rules |
| P0 investigation / P1 coverage | Runtime correctness, lifecycle, concurrency and caching | Establish supported behavior for shared borrowed contexts and full invocation/close boundaries before proposing code changes |
| P1 | Safe internal cleanup | Remove one demonstrable duplication at a time; avoid API or behavior changes disguised as cleanup |
| P2 | GraalVM/GraalPy modernization | Hold 25.0.4.1.1; experiment with later lines only for a concrete package, performance, deployment or security need |
| P2 | Java/Python interoperability performance | Use checked-in JMH harness as a reproducible baseline; require repeated proxy A/B evidence before optimization |
| P2 | Spring Boot developer experience | Improve a documented property, diagnostic or lifecycle boundary only where tests identify a real gap |
| P3 deferred | New frameworks and expanded JavaScript support | Outside the stabilization horizon; no implementation queue |
| P1 release gate | 1.0 readiness | Resolve API decisions, pass compatibility/correctness gates, document supported subset and complete a stabilization cycle |

## 4. GraalVM / GraalPy Research

Current implementation and local verification use GraalVM/GraalPy Maven artifacts `25.0.4.1.1`.
The patch upgrade passed project verification, quality, Revapi, maintained sample builds and a real
Python package smoke test; see [compatibility](../docs/compatibility.md). The checked-in benchmark
uses Oracle GraalVM JDK `25.0.4.1.1+1-LTS-jvmci-b01` on macOS AArch64, but does not prove platform-
wide performance or Native Image support.

Later innovation-line candidates remain research, not upgrade recommendations:

| Candidate | Potential use | Minimal evidence needed | Current recommendation |
| --- | --- | --- | --- |
| 25.1+ interop GIL controls | May reduce lock transitions for short callbacks | Benchmark controlled callbacks; test blocking/reentrant cases and retain executor serialization | Experiment only if callback use case exists |
| Foreign buffers | Bulk numeric/binary transfer without element boxing | Compare copied arrays and buffer/memoryview paths; verify mutability, bounds, byte order, lifetime and allocation | Defer; zero-copy benefit is unproven |
| GraalPy 25.3+ / Python 3.13 | New language/library compatibility | Re-run supported contracts and real pinned packages on a matching GraalVM/JDK tuple | Defer; keep separate from patch updates |
| Later Native Image lines | Deployment/startup/footprint changes | Build and execute the maintained AOT sample on each exact matching distribution; record build time, binary size and runtime | Experiment only if Native Image remains a deployment requirement |

These do not justify changing defaults, introducing a pool, enabling parallel access to one Context,
adding JavaScript scope, or promising support for arbitrary GraalVM internals. The 25.0.4.1.1
baseline records aggregate call overhead and startup cost but does not isolate a safe optimization;
see [`benchmarks/BASELINE.md`](../benchmarks/BASELINE.md).

## 5. Open Decisions And Deferred Scope

1. Approve classification and migration handling for the `JavaTypeRenderer` break and `generateAst` / `ContractGenerator` Java integration surfaces in `docs/public-api.md`.
2. Determine whether sharing one caller-owned Context across separate executors is supported; add bounded tests before changing lock ownership or scope.
3. Decide whether any remaining untested context lifecycle or Spring property contract merits focused coverage after reviewing existing tests.
4. Consider a specialized primitive return-conversion experiment only after repeatable JMH A/B runs show a proxy-path improvement with behavior parity.
5. Begin the 1.0 stabilization review only after the API decision and runtime boundaries above have evidence.

Defer new framework integrations, expanded JavaScript/runtime or codegen support, Java 17/Boot 3
matrices, hot reload, multi-version isolation, context pools and sandbox claims. They are not
required for 1.0. See `NEXT-STEPS.md` for the current bounded task queue.
