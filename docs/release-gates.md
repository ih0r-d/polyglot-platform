# Release Gates

This page defines the decision gates for release readiness.

Unlike [`release-checklist.md`](release-checklist.md), these are not procedural steps. They are the
conditions that should be true before a release is considered credible.

## Gate Types

### Build Gate

- the repository compiles in the pinned toolchain
- quality checks that are part of the release policy are green
- release automation remains functional

### Product Gate

- the release story matches the implemented scope
- docs, samples, and changelog describe the same product
- unsupported or experimental areas are clearly marked

### Compatibility Gate

- public API changes are intentional
- runtime behavior changes are documented
- build-time workflow changes are documented

## `0.3.0` Gates

`0.3.0` should not ship unless all of the following are true:

1. Python runtime semantics are explicit enough to be trusted.
2. Cache, reload, preload, and fail-fast behavior are documented honestly.
3. Code generation strictness and drift-check workflows are release-quality, not hidden features.
4. Samples and docs no longer drift on versioning or expected behavior.
5. JavaScript remains clearly marked as experimental bounded support.

## `1.0.0` Gates

`1.0.0` should not ship unless all of the following are true:

1. The Python-first public API surface is intentionally stable.
2. The Spring Boot path is stable enough to be treated as contract.
3. The Python-oriented codegen path is stable within its documented supported subset.
4. Experimental areas are either still clearly marked or deliberately promoted with stronger
   guarantees.
5. The repository has gone through at least one stabilization cycle without major scope drift.
