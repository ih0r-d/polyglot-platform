# Public API

This page defines the repository's intended public surface for the path to `1.0.0`.

It is not a promise that every item below is already fully stabilized. It is the list of surfaces
that should be treated as user-facing and reviewed with compatibility discipline.

For the current `0.3.1-SNAPSHOT` line:

- Java 21 is the minimum repository baseline
- Java 21 and Java 25 are the exercised verification lanes
- GraalVM 25.x is the verified runtime line
- Spring Boot support is verified on the current Boot 4.0.4 line only
- Python is the primary stabilization path
- JavaScript remains experimental where marked with `@ExperimentalApi`

## Intended Public Surface

### Core API

- `@PolyglotClient`
- `Convention`
- `SupportedLanguage`
- `@ExperimentalApi` as the repository marker for bounded published surface
- `ScriptSource`

### Runtime API

- `PyExecutor`
- `JsExecutor` as an experimental runtime type
- runtime binding methods such as `bind(...)` and `validateBinding(...)`

### Spring Boot API

- `@EnablePolyglotClients`
- starter configuration under `polyglot.*`
- `PolyglotExecutors` as the Spring-facing runtime facade
- documented actuator and metrics integration published by the starter

### Build-Time API

- `polyglot-codegen-maven-plugin` goals:
  `generate`, `check`, `doctor`
- documented plugin parameters such as `strictMode`, `skipUnchanged`, and `failOnContractDrift`

## Public Boundary Rules

- Public API means documented types and configuration that repository docs tell users to depend on directly.
- Experimental API is still public, but it does not carry the same stabilization target or parity expectation.
- Public API changes require doc review, compatibility review, and test review before release.
- Documentation is part of the product contract; undocumented internal behavior should not be treated as stable.

## Internal Surface

The following should not be treated as stable third-party extension points:

- `AbstractPolyglotExecutor`
- starter internal classes under `internal`
- package-private or implementation-only runtime helpers
- parser implementation internals
- `metadata()` map shape as a serialized compatibility contract

## Stability Direction

- Python-first runtime and starter behavior are the primary stabilization target.
- Python-oriented code generation is a stabilization target within the documented supported subset.
- JavaScript remains experimental and is not a parity target before `1.0.0`.
- Public Spring starter integration is part of the stabilization path; starter internals are not.

## Compatibility Rule

If a change modifies one of the public surfaces above, it should update:

1. documentation
2. tests
3. changelog or release notes when user-visible behavior changes
