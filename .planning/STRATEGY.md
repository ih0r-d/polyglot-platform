# Internal Strategy

This note is for local planning only. It is not intended as external product positioning.

## 1. Product Definition

Today `polyglot-adapter` is:

- a Python-first runtime adapter around GraalVM polyglot execution
- a Spring Boot starter for convention-based guest-language client binding
- a small build-time toolchain for contract/code generation, primarily meaningful for Python

It is not yet a broad polyglot platform.

## 2. Value Proposition

The clearest current value proposition is:

`polyglot-adapter` gives JVM applications a more structured, testable, and Spring-friendly way to
bind Python code to Java interfaces without forcing every application to hand-roll GraalVM
`Context`, `Value`, script loading, and startup-validation logic.

Why that matters:

- it reduces repeated low-level GraalVM integration code
- it gives teams a convention-based runtime model
- it gives Spring applications a coherent starter story
- it makes fail-fast validation and operational reporting easier

## 3. Target Users

Likely current target users:

- JVM/Spring engineers embedding trusted Python scripts into an application runtime
- teams that want convention-based guest-language integration rather than direct GraalVM plumbing
- maintainers who value startup validation, operational visibility, and testability

Less suitable today:

- users expecting first-class JavaScript parity
- users expecting sandboxing or hostile multi-tenant isolation
- users expecting hot reload or dynamic source-version management

## 4. Current Maturity

Current maturity is best described as:

- late-beta for the Python-first runtime/starter path
- experimental and still limited for JavaScript
- not yet ready to present itself as a full “polyglot platform”

Current strengths:

- small and increasingly honest public API
- coherent starter model
- clearer fail-fast and warmup/preload behavior
- stronger build/release discipline
- opt-in static contract validation and annotation discovery in Maven
- API compatibility checks for the two API modules, runtime adapter and Spring starter
- a reproducible, exploratory Java/Python JMH baseline

Current weaknesses:

- runtime semantics still need to stay explicit and stable
- cache invalidation and source identity semantics are intentionally coarse
- preload is easy to misunderstand without careful docs
- codegen remains intentionally narrow and parser-fragile outside the documented Python subset
- JavaScript support is real but explicitly experimental and narrow
- codegen Java API compatibility decisions remain open; `JavaTypeRenderer` already changed since
  `v0.4.0` and codegen is outside the current Revapi gate

## 5. Product Priorities

Near-term product priorities should be:

1. Make the Python-first path boring, predictable, and trustworthy.
2. Keep Spring Boot integration coherent and honest.
3. Reduce ambiguity around runtime behavior.
4. Position JavaScript support conservatively, accurately, and explicitly as experimental.
5. Avoid new promises that outpace implementation maturity.

This means product value should come from:

- clarity
- operational trust
- maintainable runtime semantics

not from adding more feature categories.

## 6. Engineering Priorities

Engineering should optimize for:

- explicit runtime semantics
- tests that verify meaningful behavior instead of only mocked flows
- clear cache/lifecycle boundaries
- internal simplicity
- scoped changes with low blast radius

Engineering should not optimize for:

- breadth of feature checklist
- parity claims across languages
- early architectural generalization

## 7. Scope Discipline

The near-term scope needs to stay intentionally narrow.

Keep in scope:

- Python runtime semantics
- preload and cache clarity
- Spring startup correctness
- codegen reliability for the supported Python subset
- documentation honesty
- targeted tests and small operational improvements

Keep out of scope:

- database-adjacent execution targets
- broad platform expansion
- large JS feature work
- concurrency redesign without a proven need
- hot reload / live replacement semantics

## 8. Positioning Toward `0.5.0`

Reviewed at `2a8ff35` on 2026-10-17. Earlier lifecycle, strictness, sample-CI, Revapi, build-time
contract-validation, GraalVM patch-upgrade and benchmark-baseline work is implemented; do not
reopen those tasks without new evidence. Current work should resolve remaining API decisions and
investigate concrete runtime gaps.

Keep Java core framework-independent and Spring Boot the primary integration. Safe cleanup follows
correctness evidence; performance follows repeatable measurements. GraalVM/GraalPy changes require
an applicable use case, compatible toolchain and measured results rather than version freshness.

Status/research live in `ROADMAP.md`; the reviewable task queue lives in `NEXT-STEPS.md`. Do not add
another roadmap or copy the queue into this strategy note. New framework integrations and expanded
JavaScript support remain deferred.

## 9. Positioning Toward `1.0.0`

The path to `1.0.0` should be:

1. resolve supported API boundaries and the existing codegen compatibility break
2. verify runtime concurrency, context lifecycle and configuration contracts
3. keep docs, samples, compatibility automation and release evidence aligned
4. evaluate additional scope only when a concrete use case and tests justify it

`1.0.0` should mean:

- stable public contracts
- stable runtime expectations
- honest support boundaries
- no major known ambiguity in the core product story

Until that point, the project should act like a disciplined Python-first library under active
hardening, not like a broad mature ecosystem.
