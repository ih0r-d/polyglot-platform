# Roadmap

## Current Scope

The repository currently provides:

- Python and JavaScript runtime execution
- Spring Boot integration for typed clients
- Python contract parsing and Java interface generation
- module-level separation between runtime and build tooling

## Documentation Goals

The immediate documentation goals are:

- keep `docs/` as the canonical documentation location
- keep the root `README.md` as the single repository entry point
- keep sample documentation aligned with the current implementation
- avoid duplicate module documentation that drifts from code

## Near-Term Technical Work

Likely areas of future expansion include:

- richer contract type inference
- JavaScript contract generation
- broader Spring reference documentation
- real script preload behavior for startup warmup
- expanded runtime configuration examples
- stronger alignment of sample code with the published module coordinates

## Contribution Guidance

When extending documentation:

- prefer reference-style pages over marketing-style copy
- document public behavior rather than planned internals unless clearly marked
- keep examples aligned with current module names and Maven coordinates
- call out reserved or not-yet-active configuration flags explicitly instead of documenting them as implemented
