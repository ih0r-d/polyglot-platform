# Next Steps

Working queue for `0.5.0-SNAPSHOT`, reconciled 2026-10-16 at `e1e913a` against `v0.4.0`.
Status/evidence and GraalVM research live in `ROADMAP.md`; this file owns the ordered task queue.
These tasks are proposed PRs, not implementation performed by the planning update.

## 1. Ordered Reviewable Tasks

### 1. Add japicmp for the two API artifacts — P0, ready

- Modules: `api/polyglot-annotations`, `api/polyglot-model`; root build configuration only as needed.
- Dependencies: agreed existing supported boundaries in `docs/public-api.md`; resolve published
  `0.4.0` artifacts explicitly (or reproducibly build that tag if unavailable), never auto-select latest.
- Acceptance: an opt-in compatibility profile compares both built jars against 0.4.0; fails on
  binary/source incompatibility, missing baseline or unresolved classes; excludes only explicitly
  scoped experimental/internal members. Removing a supported method or adding a required SPI
  method in a disposable validation copy proves failure. Compatible additions pass.
- Compatibility: no runtime change, no production dependency; avoid excluding the whole enum due
  to `SupportedLanguage.JS` or losing supported SPI implementor checks.
- Verification: targeted `./mvnw -B -ntp -pl api/polyglot-model -am verify -Pcompatibility`
  (proposed profile name), positive/negative probes and `task verify`; record plugin version.
- Rationale: [japicmp Maven plugin](https://siom79.github.io/japicmp/MavenPlugin.html) supports fixed
  artifacts, member filters and binary/source failure flags without another analyzer extension.

### 2. Decide codegen Java compatibility boundaries — P0, decision required

- Modules: codegen and its consumer-facing docs; no restoration is preselected.
- Dependencies: maintainer choice for renderer legacy methods, `generate/generateAst`, generator
  SPI/default constructor and mapper boundary; coordinate Spring factory classification separately.
- Acceptance: explicit decision and migration plan for each pending item; compile and link a tiny
  v0.4.0 renderer consumer to demonstrate the known break; choose compatibility remedy or an
  announced 0.5 minor break. Do not call generated-text restoration a Java API fix.
- Compatibility: source/binary break is confirmed; retrospective internal labeling is insufficient.
- Verification: old compiled consumer on new artifact, recompile probe, focused codegen/plugin
  tests if a separate remedy follows; docs build for a decision-only PR.

### 3. Extend the compatibility gate to runtime/starter — P0, depends on 1

- Modules: adapter, starter and compatibility build profile; codegen only after task 2 approval.
- Dependencies: task 1; confirm ambiguous public Spring factory boundary.
- Acceptance: supported constructors, exceptions, facade/customizer and inherited executor calls
  checked; deliberate removal of an inherited `bind` method is detected without promising protected
  subclassing hooks. Attach reports in CI; exclusions list rationale and do not ignore missing classes.
- Compatibility: no runtime change; configuration, behavior and generated text still need tests.
- Verification: focused positive/negative probes, profile on Java 21 and GraalVM 25, `task verify`.

### 4. Reproduce full-invocation concurrency boundaries — P0 investigation, ready

- Modules: adapter tests only initially.
- Dependencies: existing `AbstractPolyglotExecutor.bind`, `PyExecutor.evaluate` and serialization tests.
- Confirmed structure: evaluation runs under `withContextLock`, then proxy `isNull/as` conversion
  occurs after it returns; monitors are per executor even for one borrowed Context. A runtime failure
  has not been reproduced by this review; these are testable risks, not confirmed regressions.
- Acceptance: bounded deterministic probes exercise conversion vs concurrent close/invocation and
  two executors sharing a borrowed Context; report which usages are supported. If failing, open a
  separate minimal fix PR with the regression; do not introduce a lock registry or pool here.
- Compatibility: investigate existing guarantees before altering lock scope or shared-context rules.
- Verification: focused existing JUnit conventions, latches/time limits, real GraalPy where conversion
  needs it; `task verify` and `task quality` on pinned GraalVM 25 for any subsequent fix.

### 5. Close one real-context lifecycle coverage gap — P1, depends on 4 findings

- Modules: adapter tests.
- Dependencies: inspect existing real-context and unit tests first; ownership, preload, reload,
  cache invalidation, interruption and serialized invocation already have coverage.
- Acceptance: add one missing real-context ownership/close or failure-recovery case selected by
  evidence; borrowed close preserves caller Context and executor caches are released, or failure
  does not poison later supported access. No forced-GC timing assumption or duplicate mock test.
- Compatibility: tests preserve current ownership, weak-instance and explicit reload contracts.
- Verification: targeted adapter tests, `task verify`; `task samples` if runtime behavior changes.

### 6. Remove one proven internal duplication — P1, gated on evidence

- Modules: one adapter or starter internal implementation only.
- Dependencies: tasks 3–5 as relevant; identify duplicate logic and callers before proposing deletion.
- Acceptance: one responsibility/call path simplified with existing behavior preserved; no public
  signature, property/default, source validation or ownership change. If no safe duplication is
  demonstrated, close as no-op rather than force cleanup. `CommonUtils` removal is not this task.
- Compatibility: none intended; inherited/public behavior remains in scope of review.
- Verification: affected module tests, `task verify`, `task quality`; final API/config diff review.

### 7. Establish a small interop performance baseline — P2, ready after correctness review

- Modules: framework-neutral adapter benchmark consumer, exact location chosen with maintainer;
  do not introduce a benchmark module/framework without need.
- Dependencies: task 4 conclusions; current 25.0.2 artifacts and recorded host JVM.
- Acceptance: reproducible command and machine/version metadata; separate-process cold creation,
  first bind/call and warm loops; scalar and nested list/map direct Value vs proxy calls, result
  consumption and correctness assertions; at least five forks, warmup reported, median/spread and
  allocation/RSS limitations stated. No wall-clock unit-test threshold or performance promise.
- Compatibility: measurement only; generic return erasure is measured before mapping changes.
- Verification: rerun baseline twice; `task verify` if Java benchmark code is added.

### 8. Evaluate one aligned 25.0 patch tuple — P2 research, escalate security if justified

- Modules: disposable consumer first; later root/BOM/samples/CI only in a separately authorized PR.
- Dependencies: tasks 1, 3 and 7; official exact-version/artifact availability and advisories from
  `ROADMAP.md`; edition choice is unresolved. No permanent version change in the experiment.
- Acceptance: compare 25.0.2 with available aligned 25.0.4.1.1 candidate; report resolved dependency
  tree, package installation/import outcomes, unchanged contract results, cold/warm cost and SBOM
  exposure. Distinguish network/install failures from runtime regressions; reject if unavailable.
- Compatibility: preserve Java 21 compilation baseline; do not combine Python 3.13 migration or
  innovation-runtime host requirements. Any adoption updates all version/support claims together.
- Verification: targeted tests then verify/quality/samples on matching GraalVM; manual AOT smoke;
  no claim of native verification if no native toolchain is available.

### 9. Lock down one Spring Boot configuration/DX contract — P2, ready

- Modules: starter tests and relevant docs if necessary.
- Dependencies: existing Boot 4.1.0 `PolyglotProperties`, startup/client resolution tests and docs.
- Acceptance: select one uncovered property/default, customizer ordering or fail-fast diagnostic;
  existing configuration yields the same bean activation and exception cause. Improve a message
  only if a concrete missing context is demonstrated; no new property or framework adapter.
- Compatibility: preserve keys, defaults, startup/preload timing and exception categories.
- Verification: focused starter tests, `task verify`; `task samples` for public behavior changes;
  `task docs:build` for documentation edits.

### 10. Run a bounded 1.0 stabilization review — P1 release gate, blocked on evidence

- Modules: existing release/compatibility docs and CI reports across maintained modules.
- Dependencies: tasks 1–5 and benchmark evidence from 7; modernization adoption is not mandatory.
- Acceptance: all `1.0.0-EXIT-CRITERIA.md` gates have concrete evidence; pending API decisions
  resolved, explicit known limitations and deprecation migration paths; one stabilization cycle
  without new framework/JS scope. Do not mark ready merely because checks are configured.
- Compatibility: use `docs/compatibility.md` release/deprecation rules; no Java 17/Boot 3 promise.
- Verification: `task verify`, `task quality`, `task samples`, strict docs build; exact matrix results
  attached, manual native checks only if Native Image is included in support claims.

## 2. Queue Rules

Tasks can proceed independently where dependencies allow; numbering is recommended review order.
Task 1 is the smallest next implementation PR. Task 2 is a release-blocking decision, not a reason
to defer the agreed API-module gate. No broad refactor, upgrade, new framework or JavaScript work
is bundled into these tasks. Research follow-ups (buffers, GIL control, Engine sharing) remain
optional experiments from `ROADMAP.md` after measurements; they are not automatic feature backlog.
