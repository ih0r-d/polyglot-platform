# Compatibility

`polyglot-adapter` supports multiple layers of the Java and GraalVM ecosystem. This page defines the repository baseline and what must be updated together when that baseline changes.

## Toolchain Baseline

- Maven: 3.9+
- Build tooling modules: JDK 21+
- Runtime modules: JDK 25+
- GraalVM runtime dependencies: 25.x

The repository root pins the expected local Java runtime in `.sdkmanrc`. For runtime work,
examples, and full test execution, contributors are expected to run `sdk env` before using the
Maven wrapper.

## Compatibility Contract

- The root build must keep explicit Maven plugin versions and reproducible build settings.
- Public artifacts in `api/` and stable runtime modules should preserve backward compatibility within a minor release line unless release notes state otherwise.
- Samples are reference applications, not compatibility guarantees.
- Experimental or incubating APIs should be documented as such before release.

## Modules and Expectations

| Area                                   | Baseline              | Notes                                                                        |
|----------------------------------------|-----------------------|------------------------------------------------------------------------------|
| `api/*`                                | JDK 21+               | Public API surface should remain small and stable                            |
| `build-tools/*`                        | JDK 21+               | Maven plugin and codegen behavior changes should be documented               |
| `runtime/polyglot-adapter`             | JDK 25+, GraalVM 25.x | Core execution layer                                                         |
| `runtime/polyglot-spring-boot-starter` | JDK 25+, GraalVM 25.x | Aligns with current Spring Boot baseline                                     |
| `samples/*`                            | JDK 25+, GraalVM 25.x | Maintained smoke-test applications aligned with the current development line |

## When You Change the Baseline

If you raise or lower any supported version:

1. Update the root `pom.xml` properties and enforcer rules.
2. Update `README.md`.
3. Update CI workflows under `.github/workflows/`.
4. Update sample applications affected by the change.
5. Call out the compatibility change in release notes and changelog entries.
