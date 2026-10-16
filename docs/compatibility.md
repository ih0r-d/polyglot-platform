# Compatibility

`polyglot-adapter` spans API modules, runtime modules, build tools, maintained samples, and CI.
This page defines what the repository currently verifies, what is only a target, and what must
change before stronger support claims are made.

## Current Verified Support

The current `0.5.x` release-preparation line verifies the following:

- Maven wrapper and CI workflows on Maven 3.9+
- pinned local SDKMAN runtime: Java `25-graalce`
- repository minimum Java version: 21
- root CI baseline workflow on Java 21
- root CI runtime workflow on GraalVM JDK 25
- all modules compiled with `maven.compiler.release=21`
- root enforcer minimum Java version: 21
- GraalVM dependency line: `25.0.2`
- Spring Boot repository dependency-management line: `4.1.0`
- maintained Spring sample currently aligned to Spring Boot `4.1.0`
- maintained sample smoke verification on GraalVM JDK 25 for:
  - `samples/java-maven-example`
  - `samples/java-maven-codegen-example`
  - `samples/spring-boot-example`

This means the repository currently carries a unified minimum Java baseline with two exercised
verification lines:

- Java 21 as the repository minimum compile baseline
- Java 25 / GraalVM 25.x as the currently exercised runtime and maintained-sample verification line

It now verifies the current Spring Boot 4.1.0 line through the starter tests and the maintained
Spring sample.

The repository root pins the expected local Java runtime in `.sdkmanrc`. For
runtime work, maintained samples, and full test execution, contributors are expected to run
`sdk env` before using the Maven wrapper.

## Current Verified Ranges And Caveats

| Area                                 | Current verified line | Evidence in repository                                              | Notes                                                                       |
|--------------------------------------|-----------------------|---------------------------------------------------------------------|-----------------------------------------------------------------------------|
| Maven                                | 3.9+                  | root enforcer and wrapper usage                                     | Release and CI assume the wrapper                                           |
| Java minimum for repository modules  | 21                    | root `pom.xml`, module POMs, Java 21 CI build                       | The repository contract now uses Java 21 as the minimum baseline            |
| Java runtime verification line       | 25                    | `.sdkmanrc`, GraalVM 25 CI runtime job, sample verification on 25   | GraalVM 25.x remains the current verified runtime lane                      |
| GraalVM runtime deps                 | 25.0.2                | root dependency management, sample POMs, sample workflows           | Current maintained runtime line                                             |
| Spring Boot starter line             | 4.1.0                 | root dependency management, starter tests, maintained Spring sample | Verified in the current repository line, not a broad historical range claim |
| Maintained sample smoke verification | Java 25 / GraalVM 25  | `ci.yml`, sample POMs                                               | Samples are verification inputs, not independent support guarantees         |

## Target Support, Not Yet Verified

The stabilization path toward `1.0.0` may still aim for:

- broader Spring Boot compatibility claims beyond the currently verified `4.1.0` line

This remains a target only. It should not be described as supported until the repository actually
verifies it.

## What Should Not Yet Be Claimed

Do not currently claim:

- a broad Spring Boot 4.x compatibility range beyond the currently verified `4.1.0` line
- Spring Boot 3.x support
- support for GraalVM lines other than the currently exercised 25.x line
- broad runtime verification on Java lines other than the currently exercised 25 line

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

This policy applies to the supported boundaries in [public API](public-api.md). Pending
classifications there remain unresolved recommendations, not retroactive guarantees. The current
release comparison baseline is `v0.4.0`; a policy alone does not establish that HEAD is fully
compatible or that an automated API gate exists.

| Contract | Required compatibility review |
| --- | --- |
| Binary | Existing compiled consumers must link against supported replacement artifacts; preserve accessible signatures, including inherited executor methods |
| Source | Existing valid supported usage must recompile; review overload ambiguity, generics, annotations and checked exceptions as well as method presence |
| Behavior | Preserve documented naming/export resolution, exception categories/causes, context ownership, cache, concurrency and lifecycle semantics; bug/security corrections need explicit release notes and migration guidance when observable |
| Configuration | Preserve documented Spring property names, types, defaults and activation behavior, plus plugin goal names, parameters/defaults and lifecycle/source registration |
| SPI | Preserve existing implementations as well as callers; adding an abstract method can break implementors even when ordinary callers still compile |
| Generated source / Maven workflow | Unchanged valid supported contracts and configuration must produce deterministic compatible source; review text, checksums and `check` drift behavior as well as Java signatures |

The generated-source fix preserves the tested `v0.4.0` LF layout for empty interfaces, multiple
methods and nested generics. It is not proof of byte compatibility for every historical input.
Current output uses LF; legacy CRLF files can still drift. The `JavaTypeRenderer` Java API break is
separate and unresolved; see the pending decisions in [public API](public-api.md).

These guarantees do not cover GraalVM implementation internals, arbitrary serialized model or
metadata values, unsupported runtime capabilities or experimental JavaScript behavior. Supported
version claims remain limited to the verification matrix above. BOM alignment and successful
samples do not establish compatibility with every external dependency combination.

### Versioning

- **Before `1.0.0`:** PATCH releases preserve supported contracts. Breaking changes between MINOR
  releases require an explicit compatibility decision, release notes identifying affected usage,
  and migration guidance; a `0.x` version is not permission for silent breaks. Experimental
  changes must communicate their scope and limitations. Internal implementation details carry no
  public compatibility guarantee.
- **From `1.0.0`:** follow Semantic Versioning for supported contracts: PATCH for compatible fixes,
  MINOR for backward-compatible additions and deprecations, MAJOR for breaking changes. A change
  affecting source, binary, documented behavior, configuration, SPI or build workflow compatibility
  must be evaluated, not just a Java signature diff. Experimental limitations must remain explicit.

### Deprecation And Removal

Deprecation must name the replacement or explain why none exists, identify the affected usage and
provide migration guidance. Use `@Deprecated(since = ...)` for Java API and document configuration
or workflow deprecations in release notes. `forRemoval = true` signals intent, not a removal date.

For supported pre-1.0 APIs, retain deprecated behavior through at least one released MINOR line
before removal in a later MINOR release. An exceptional correctness/security break requires an
explicit maintainer decision and documented migration impact. In `1.x` and later, supported API
removal requires a MAJOR release and prior deprecation in at least one released MINOR version.
Deprecations must remain usable during their transition; adding a warning does not justify an
earlier incompatible implementation change.

Legacy unsupported utilities such as `CommonUtils` are not newly covered by that retention promise;
their actual removal still needs an explicit release decision and notice to known consumers.

The root build must retain explicit Maven plugin versions and reproducible build settings.
Compatibility changes require focused tests and user-visible changelog/release notes. These are
release-review requirements. The opt-in `api-compat` Revapi gate checks the two API artifacts
against published `0.4.0`; runtime, starter and codegen are not yet covered. See
[the verification command](developer-tooling.md#java-api-compatibility).

## Current Java Support Story

- The repository minimum Java baseline is 21.
- Repository modules compile against Java 21.
- Runtime execution, starter verification, and maintained samples are currently exercised on
  GraalVM JDK 25.
- Java 25 remains the pinned local SDKMAN runtime for full repository work because it matches the
  current verified GraalVM runtime line.

## When You Change the Baseline

If you raise or lower any supported version:

1. Update the root `pom.xml` properties and enforcer rules.
2. Update `README.md`.
3. Update CI workflows under `.github/workflows/`.
4. Update sample applications affected by the change.
5. Call out the compatibility change in release notes and changelog entries.
6. Update this page to distinguish the new verified support from any still-unverified targets.
