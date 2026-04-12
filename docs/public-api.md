# Public API

This page defines the repository's intended public surface for the path to `1.0.0`.

It is not a promise that every item below is already fully stabilized. It is the list of surfaces
that should be treated as user-facing and reviewed with compatibility discipline.

## Intended Public Surface

### Core API

- `@PolyglotClient`
- `Convention`
- `SupportedLanguage`
- `ScriptSource`

### Runtime API

- `PyExecutor`
- `JsExecutor`
- runtime binding methods such as `bind(...)` and `validateBinding(...)`

### Spring Boot API

- `@EnablePolyglotClients`
- starter configuration under `polyglot.*`
- `PolyglotExecutors` as the Spring-facing runtime facade

### Build-Time API

- `polyglot-codegen-maven-plugin` goals:
  `generate`, `check`, `doctor`
- documented plugin parameters such as `strictMode`, `skipUnchanged`, and `failOnContractDrift`

## Internal Surface

The following should not be treated as stable third-party extension points:

- `AbstractPolyglotExecutor`
- starter internal classes under `internal`
- parser implementation internals
- `metadata()` map shape as a serialized compatibility contract

## Stability Direction

- Python-first runtime and starter behavior are the primary stabilization target.
- Python-oriented code generation is a stabilization target within the documented supported subset.
- JavaScript remains experimental and is not a parity target before `1.0.0`.

## Compatibility Rule

If a change modifies one of the public surfaces above, it should update:

1. documentation
2. tests
3. changelog or release notes when user-visible behavior changes
