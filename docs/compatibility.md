# Compatibility

`polyglot-adapter` spans API modules, runtime modules, build tools, maintained samples, and CI.
This page defines what the repository currently verifies, what is only a target, and what must
change before stronger support claims are made.

## Current Verified Support

The current `0.3.1-SNAPSHOT` repository line verifies the following:

- Maven wrapper and CI workflows on Maven 3.9+
- pinned local SDKMAN runtime: Java `25-graalce`
- root CI workflows on Java 25
- `api/*` and `build-tools/*` compiled with `maven.compiler.release=21`
- `runtime/*` compiled with `maven.compiler.release=25`
- root enforcer minimum Java version: 21
- GraalVM dependency line: `25.0.2`
- Spring Boot repository dependency-management line: `4.0.4`
- maintained Spring sample currently aligned to Spring Boot `4.0.4`
- maintained sample smoke verification on GraalVM JDK 25 for:
  - `samples/java-maven-example`
  - `samples/java-maven-codegen-example`
  - `samples/spring-boot-example`

This means the repository currently verifies a mixed Java baseline:

- Java 21+ for `api/*` and `build-tools/*`
- Java 25 / GraalVM 25.x for `runtime/*` and maintained sample verification

It does not currently verify a Java 17 baseline, and it does not currently verify a Spring Boot
3.x line.

The repository root pins the expected local Java runtime in [`.sdkmanrc`](../.sdkmanrc). For
runtime work, maintained samples, and full test execution, contributors are expected to run
`sdk env` before using the Maven wrapper.

## Current Verified Ranges And Caveats

| Area                                 | Current verified line | Evidence in repository                                              | Notes                                                                       |
|--------------------------------------|-----------------------|---------------------------------------------------------------------|-----------------------------------------------------------------------------|
| Maven                                | 3.9+                  | root enforcer and wrapper usage                                     | Release and CI assume the wrapper                                           |
| Java for `api/*`                     | 21+                   | `api/pom.xml`, root enforcer, CI on 25                              | Verified at 21+ compile level, exercised in CI on 25                        |
| Java for `build-tools/*`             | 21+                   | `build-tools/pom.xml`, root enforcer, CI on 25                      | Same current story as `api/*`                                               |
| Java for `runtime/*`                 | 25                    | root `maven.compiler.release=25`, CI on 25                          | This is the current runtime baseline                                        |
| GraalVM runtime deps                 | 25.0.2                | root dependency management, sample POMs, sample workflows           | Current maintained runtime line                                             |
| Spring Boot starter line             | 4.0.4                 | root dependency management, starter build, maintained Spring sample | Verified in the current repository line, not a broad historical range claim |
| Maintained sample smoke verification | Java 25 / GraalVM 25  | `samples.yml`, sample POMs                                          | Samples are verification inputs, not independent support guarantees         |

## Target Support, Not Yet Verified

The stabilization path toward `1.0.0` may still aim for:

- Java 17 minimum support
- Spring Boot 3.x support

These are targets only. They should not be described as supported until the repository actually
verifies them.

Required work before claiming Java 17 minimum support:

1. lower the intended compiler/enforcer baseline where needed
2. compile all affected modules on Java 17
3. run the relevant tests on Java 17
4. verify maintained samples that are part of the support story on Java 17
5. add CI coverage for that baseline

Required work before claiming Spring Boot 3.x support:

1. move the starter and maintained Spring sample onto a verified Boot 3.x line
2. run starter tests on that line
3. run the maintained Spring sample on that line
4. add CI coverage that exercises that support line
5. update docs to name the exact verified range

## What Should Not Yet Be Claimed

Do not currently claim:

- Java 17 minimum support
- Spring Boot 3.x support
- a single uniform Java baseline across all modules
- support for GraalVM lines other than the currently exercised 25.x line

Do not infer support from:

- successful local builds on one contributor machine
- sample existence by itself
- code that looks source-compatible without CI verification

## Experimental And Unsupported Surface

- JavaScript runtime binding remains experimental and should not be used to broaden compatibility
  claims for the Python-first stable path
- JavaScript code generation remains unsupported
- unsupported runtime areas such as hot reload or multi-version source isolation are not part of
  compatibility promises

## Compatibility Contract

- The root build must keep explicit Maven plugin versions and reproducible build settings.
- Public artifacts in `api/` and stable runtime modules should preserve backward compatibility
  within a minor release line unless release notes state otherwise.
- Samples are reference verification inputs, not compatibility guarantees by themselves.
- Experimental or incubating APIs should be documented as such before release.
- Support claims must follow verified automation, not aspiration.

## When You Change the Baseline

If you raise or lower any supported version:

1. Update the root `pom.xml` properties and enforcer rules.
2. Update `README.md`.
3. Update CI workflows under `.github/workflows/`.
4. Update sample applications affected by the change.
5. Call out the compatibility change in release notes and changelog entries.
6. Update this page to distinguish the new verified support from any still-unverified targets.
