# Roadmap

## Current Scope

The repository currently provides:

- Python runtime execution and experimental JavaScript runtime execution
- Spring Boot integration for typed clients
- explicit Python contract reload and optional runtime metrics / developer console
- Python contract parsing and Java interface generation
- module-level separation between runtime and build tooling

Current positioning is intentionally conservative:

- Python is the primary and more mature runtime path.
- JavaScript support remains narrower, runtime-focused, and experimental.
- Build-time code generation is real and growing more reliable, but still intentionally scoped to a
  Python-oriented contract subset.

## Documentation Goals

The immediate documentation goals are:

- keep `docs/` as the canonical documentation location
- keep the root `README.md` as the single repository entry point
- keep sample documentation aligned with the current implementation
- avoid duplicate module documentation that drifts from code

## Near-Term Technical Work

The current `0.5.0-SNAPSHOT` line has completed the main incremental hardening work: GraalVM/GraalPy
is aligned to `25.0.4.1.1`, Revapi checks the four API/runtime artifacts against `v0.4.0`, Spring
startup fail-fast behavior has real-Python integration coverage, and the Maven plugin can opt into
static Python module/symbol/positional-signature validation with annotation discovery. A standalone
JMH baseline records invocation, transfer, import and context-startup costs.

The remaining work is compatibility boundary decisions, evidence-driven runtime/lifecycle tests,
small safe cleanups, and a bounded stabilization review. This public roadmap describes direction,
not product or release guarantees. Personal planning belongs in the ignored `.local/planning/`.

Deferred:

- JavaScript code generation
- JavaScript parity work
- concurrency redesign
- automatic file watching and source reload (explicit contract reload is already supported)
- broad “platform” expansion claims

## Contribution Guidance

When extending documentation:

- prefer reference-style pages over marketing-style copy
- document public behavior rather than planned internals unless clearly marked
- keep examples aligned with current module names and Maven coordinates
- call out reserved or not-yet-active configuration flags explicitly instead of documenting them as implemented
