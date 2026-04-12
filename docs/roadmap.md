# Roadmap

## Current Scope

The repository currently provides:

- Python and JavaScript runtime execution
- Spring Boot integration for typed clients
- Python contract parsing and Java interface generation
- module-level separation between runtime and build tooling

Current positioning is intentionally conservative:

- Python is the primary and more mature runtime path.
- JavaScript support remains narrower and runtime-focused.
- Build-time code generation is real and growing more reliable, but still intentionally scoped to a
  Python-oriented contract subset.

## Documentation Goals

The immediate documentation goals are:

- keep `docs/` as the canonical documentation location
- keep the root `README.md` as the single repository entry point
- keep sample documentation aligned with the current implementation
- avoid duplicate module documentation that drifts from code

## Near-Term Technical Work

The next release line is focused on credibility and release quality, not platform expansion.

Current `0.3.0` direction:

- make Python runtime semantics explicit enough to be trusted
- keep Spring startup/fail-fast/preload behavior aligned with those semantics
- improve code generation reliability with stricter type handling and drift checks
- keep JavaScript claims bounded to the runtime surface that actually exists

Not the focus of `0.3.0`:

- JavaScript code generation
- JavaScript parity work
- concurrency redesign
- hot reload or live source replacement semantics
- broad “platform” expansion claims

## Contribution Guidance

When extending documentation:

- prefer reference-style pages over marketing-style copy
- document public behavior rather than planned internals unless clearly marked
- keep examples aligned with current module names and Maven coordinates
- call out reserved or not-yet-active configuration flags explicitly instead of documenting them as implemented
