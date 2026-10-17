# Next Steps

Working queue for `0.5.0-SNAPSHOT`, reconciled 2026-10-17 at `2a8ff35` against `v0.4.0`.
Completed compatibility automation, GraalVM patch alignment, build-time validation and JMH baseline
are recorded in `ROADMAP.md`; these are the remaining proposed tasks, not implementation approval.

## 1. Ordered Reviewable Tasks

### 1. Decide the shipped codegen Java API boundaries — P0, maintainer decision

- Modules: `build-tools/polyglot-codegen`; docs and changelog if a migration decision is made.
- Dependencies: review the `v0.4.0` to HEAD history and pending table in `docs/public-api.md`.
- Acceptance: explicitly classify `JavaTypeRenderer`, `generateAst`, `ContractGenerator`, and mapper extension capability; choose compatibility restoration or an announced break/migration path. Do not relabel already shipped signatures internal without assessing consumers.
- Compatibility: confirmed `JavaTypeRenderer` source and binary break; codegen is not currently in Revapi scope.
- Verification: old-consumer compile/link probe for whichever remedy is selected, focused codegen tests, `task docs:build`; extend Revapi only after the supported boundary is agreed.

### 2. Define cross-executor borrowed-Context concurrency support — P0 investigation

- Modules: `runtime/polyglot-adapter` tests and `docs/runtime-semantics.md` if behavior is clarified.
- Dependencies: inspect current per-executor locking, context ownership and close tests.
- Acceptance: deterministic bounded tests exercise two executors sharing one borrowed Context and invocation/result conversion racing with close; document which usage is supported. If a failure reproduces, make the correction a separate focused PR.
- Compatibility: do not introduce global locks, pools, or changed ownership defaults as part of the investigation.
- Verification: targeted tests on GraalVM 25.0.4.1.1, then `task verify` and `task quality` for any implementation change.

### 3. Close one evidence-backed lifecycle coverage gap — P1, depends on 2

- Modules: adapter tests, or starter tests if the confirmed gap is in startup lifecycle.
- Dependencies: inventory existing ownership, close, preload, reload, interruption and failure-recovery tests first.
- Acceptance: add one missing real-context scenario and assert the established ownership/cache/error contract; avoid forced-GC timing assumptions and duplicate unit coverage.
- Compatibility: tests only unless an observed failure warrants a separate fix.
- Verification: targeted module tests, `task verify`; `task samples` if runtime behavior changes.

### 4. Resolve one documented Spring Boot configuration gap — P1/P2, evidence required

- Modules: Spring starter tests and the specific existing user documentation page.
- Dependencies: review `PolyglotProperties`, auto-configuration conditions, and current real integration tests.
- Acceptance: select one concrete default, property activation, customizer ordering or diagnostic gap; test enabled and disabled behavior and keep defaults and exception causes unchanged.
- Compatibility: no new property or framework integration.
- Verification: focused starter tests, `task verify`, `task samples` for public behavior changes and `task docs:build` for docs changes.

### 5. Remove one proven internal duplication — P1, only if found

- Modules: one adapter or starter internal implementation.
- Dependencies: concrete repeated responsibility and caller evidence; do not target `CommonUtils` by default.
- Acceptance: simplify one call path without changing public signatures, configuration, ownership, cache or source-resolution behavior. Close as no-op if no safe duplication is demonstrated.
- Compatibility: none intended; inherited public methods remain covered by Revapi.
- Verification: focused module tests, `task verify`, `task quality`, and API/config diff review.

### 6. Run a primitive return-conversion benchmark experiment — P2, depends on evidence

- Modules: `benchmarks` and possibly one isolated adapter implementation/test PR after a positive prototype.
- Dependencies: current checked-in JMH baseline; do not change runtime caches, locks or defaults.
- Acceptance: repeat direct and bound-client A/B runs on the pinned JDK, report confidence intervals and allocations, and demonstrate semantic equivalence for nulls, conversion failures and guest exceptions before proposing production code.
- Compatibility: benchmark-only first; any behavior change requires a separate design and API/runtime review.
- Verification: rerun the documented benchmark commands on an idle host and record raw results; no CI latency threshold.

### 7. Consider later GraalVM/GraalPy capabilities — P2 research, use-case gated

- Modules: disposable consumer experiment first; production version files only in a separately reviewed upgrade.
- Dependencies: concrete package, performance, deployment or security need; exact runtime and language artifact tuple.
- Acceptance: compare against the supported 25.0.4.1.1 baseline, capture package compatibility and runtime behavior, and assess GIL/buffer/context lifecycle risks. Python 3.13 or Native Image changes are separate experiments.
- Compatibility: no automatic upgrade and no expansion of JavaScript support.
- Verification: `task verify`, `task quality`, `task samples`, compatibility profile and relevant package/AOT smoke on the exact runtime; report unavailable platforms.

### 8. Run a bounded 1.0 stabilization review — P1 release gate

- Modules: existing API, runtime, starter, codegen, CI, docs and maintained samples.
- Dependencies: resolve task 1; obtain evidence from tasks 2–4; no modernization adoption is required.
- Acceptance: supported boundaries and limitations are explicit, compatibility and CI gates pass, migration notes exist for approved breaks, and one stabilization cycle completes without new framework/JS scope.
- Compatibility: use `docs/compatibility.md`; do not infer Java 17, Boot 3 or platform-wide Native Image support.
- Verification: `task verify`, `task quality`, full Revapi profile, `task samples`, `task docs:build`, and exact manual checks for any support claim.

## 2. Queue Rules

The numbering is recommended review order, not a mandate to implement speculative work. Decision and
investigation tasks should close with evidence even when they produce no code. Keep each fix in its
own reviewable change, preserve the default Python-first runtime behavior, and defer new frameworks
and expanded JavaScript support.
