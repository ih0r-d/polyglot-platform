# Examples

The repository contains three maintained sample projects that demonstrate how the runtime adapter is used in practice.
The `samples/polyglot-ai-demo` module is intentionally excluded from the documentation set.

Examples are included here as supporting material after the main onboarding and conceptual documentation.

> Note
> Use the samples to understand usage patterns. Use the rest of the documentation as the source of truth for current
> terminology and runtime behavior.

## Example Matrix

| Example                                                                        | What it demonstrates                                                                    | Integration style                 |
|--------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|-----------------------------------|
| [`samples/java-maven-example`](https://github.com/ih0r-d/polyglot-platform/tree/main/samples/java-maven-example)                 | Direct use of `PyExecutor` and `JsExecutor` with explicit `ScriptSource` wiring         | Framework-neutral runtime adapter |
| [`samples/java-maven-codegen-example`](https://github.com/ih0r-d/polyglot-platform/tree/main/samples/java-maven-codegen-example) | Build-time Java interface generation from Python contracts, followed by runtime binding | Code generation + runtime adapter |
| [`samples/spring-boot-example`](https://github.com/ih0r-d/polyglot-platform/tree/main/samples/spring-boot-example)               | Spring Boot auto-configuration, `@PolyglotClient` registration, actuator, and metrics   | Spring Boot integration layer     |

## What Each Example Helps You Learn

### Java Maven Example

Use this example to understand the core adapter API without framework abstraction.

It demonstrates:

- manual executor creation
- filesystem-backed script loading
- Python and JavaScript execution
- binding validation and metadata

### Java Maven Codegen Example

Use this example to understand how build-time tooling and runtime execution fit together.

It demonstrates:

- Python contract parsing
- generated Java interfaces
- class-style and dictionary-style Python exports
- classpath-based runtime binding

### Spring Boot Example

Use this example to understand the adapter in an application framework.

It demonstrates:

- `@EnablePolyglotClients`
- `@PolyglotClient`
- Spring-managed executors
- actuator and metrics integration

## Notes About Sample Drift

The samples are useful reference material, but some of them still contain older package names or coordinates that do not
fully match the current repository structure.

Use the documentation in this `docs/` directory as the source of truth for:

- terminology
- architecture
- module responsibilities
- current runtime adapter behavior
