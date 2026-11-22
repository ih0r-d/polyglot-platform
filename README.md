# Polyglot Platform

**Typed Python integration for Java applications, powered by GraalVM.**

[![CI](https://github.com/ih0r-d/polyglot-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ih0r-d/polyglot-platform/actions/workflows/ci.yml)
[![SonarCloud Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=ih0r-d_polyglot-adapter&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=ih0r-d_polyglot-adapter)
[![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=ih0r-d_polyglot-adapter&metric=coverage)](https://sonarcloud.io/summary/new_code?id=ih0r-d_polyglot-adapter)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ih0r-d/polyglot-adapter?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.ih0r-d/polyglot-adapter)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-007396)](docs/compatibility.md)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Stand with Ukraine](https://img.shields.io/badge/Stand%20with-Ukraine-0057B7?labelColor=FFD700)](https://u24.gov.ua/)

[**Documentation**](https://ih0r-d.github.io/polyglot-platform/) · [Architecture](docs/architecture.md) · [Examples](#examples) · [Contributing](CONTRIBUTING.md) · [Releases](https://github.com/ih0r-d/polyglot-platform/releases)

> 🇺🇦 **Слава Україні! Героям слава!**
>
> Stand with Ukraine.

## Overview

Polyglot Platform lets Java applications work with Python through **typed Java contracts**, rather than scattering low-level polyglot calls throughout application code. It provides a framework-neutral execution layer, an optional Spring Boot starter, and Maven tooling for contract generation and validation.

The platform is **Python-first**. JavaScript execution is available as an experimental runtime path; JavaScript code generation is not supported. Supported APIs and behavior are explicitly documented rather than implied by the presence of an implementation.

This README is the **repository overview and entry point**. Detailed setup instructions, API contracts, runtime behavior, and examples live in [`docs/`](docs/index.md) and [`samples/`](samples/).

## Capabilities

| Area | What it provides | Learn more |
| --- | --- | --- |
| **Java ↔ Python runtime** | Embedded execution through GraalVM, `PyExecutor`, and configurable `ScriptSource` implementations | [Runtime](docs/runtime.md) |
| **Typed contracts** | Java interface binding, argument/result conversion, and explicit binding conventions | [Runtime semantics](docs/runtime-semantics.md) |
| **Spring Boot** | Declarative `@PolyglotClient` beans, auto-configuration, lifecycle management, and startup validation | [Spring integration](docs/runtime.md) |
| **Code generation** | Java interfaces generated from Python source using GraalPy's Python AST and deterministic Java source generation | [Codegen](docs/codegen.md) |
| **Build-time checks** | Opt-in validation of Python modules, symbols, and supported signatures through the Maven plugin | [Contract validation](docs/codegen.md#opt-in-java-to-python-contract-validation) |
| **Observability** | Optional Micrometer metrics, Actuator health/info, and tracing integration | [Runtime](docs/runtime.md) · [Tracing](docs/tracing.md) |
| **Runtime console** | Experimental read-only dashboard for supported Spring Boot MVC applications | [Console](docs/runtime.md#optional-experimental-console-java-25) |
| **Engineering tooling** | Maven BOM, consumer samples, API compatibility checks, quality gates, and performance benchmarks | [Developer tooling](docs/developer-tooling.md) |

### Design principles

- **Framework-neutral core:** the execution adapter does not require Spring Boot.
- **Explicit contracts:** stable APIs, runtime behavior, and unsupported capabilities are documented.
- **Predictable lifecycle:** context ownership, serialization, caching, and shutdown semantics are part of the contract.
- **Optional integration:** Spring Boot, observability, console, and code-generation tooling are selected as needed.
- **Build-time analysis without application execution:** Python contracts are analyzed with GraalPy `ast.parse`; application modules are not imported during static validation.

Runtime evaluation is different: module loading and startup validation **may execute Python initialization code**. Consult [runtime semantics](docs/runtime-semantics.md) before relying on fail-fast, caching, or reload behavior.

## Project status and compatibility

The `main` branch contains ongoing **`0.5.0-SNAPSHOT`** development. The published **`0.4.0`** Maven Central line is distinct and may not contain APIs or features described for `main`, including newer annotation-based client tooling. Always verify the release-specific contract before adopting an API.

| Component | Development / verification status |
| --- | --- |
| Java compilation baseline | **Java 21** |
| Maintained runtime verification | **GraalVM JDK 25.x** |
| Maven | **3.9+**, with `./mvnw` for this repository |
| GraalVM / GraalPy dependencies | **`25.0.4.1.1`** in the current development line |
| Spring Boot starter | Maintained and verified against **Spring Boot 4.1.2** in the development line |
| Python integration | Primary supported development path |
| JavaScript integration | Experimental runtime support; code generation unsupported |
| Runtime console | Experimental; **Java 25 / Spring Boot 4.1 MVC** |
| Native Image | Experimental; executable-level verification required |

Compilation alone does **not** establish runtime compatibility. For tested versions, supported extension points, known restrictions, and experimental features, use [Compatibility](docs/compatibility.md), [Stability](docs/stability.md), and [Public API](docs/public-api.md).

## Architecture and modules

The repository separates portable API contracts, runtime execution, and build-time tooling.

```text
api/
  polyglot-annotations/           Public annotations and language conventions
  polyglot-model/                 Shared contracts, configuration, and parser/source SPIs
runtime/
  polyglot-adapter/               Framework-neutral GraalVM execution
  polyglot-spring-boot-starter/   Spring Boot integration and client lifecycle
  polyglot-bom/                   Runtime dependency alignment
  polyglot-spring-boot-console/   Optional experimental runtime dashboard
build-tools/
  polyglot-codegen/               Python AST analysis and Java source generation
  polyglot-codegen-maven-plugin/  Maven code-generation and validation goals
samples/                          Consumer examples and experiments
docs/                             User, architecture, and maintainer documentation
```

**Dependency direction:** portable API → runtime and build tooling; Spring Boot integration depends on the framework-neutral runtime, never the reverse. The Maven plugin uses the code-generation layer for static contract inspection.

The precise module responsibilities, supported extension points, and lifecycle boundaries are described in [Architecture](docs/architecture.md) and [Public API](docs/public-api.md).

## Getting started

Choose the integration path that matches your application rather than enabling every component.

| Goal | Recommended starting point |
| --- | --- |
| Call Python from a plain Java application | [Core runtime guide](docs/runtime.md) · [Java sample](samples/java-maven-example) |
| Use Python-backed interfaces in Spring Boot | [Spring Boot guide](docs/runtime.md) · [Spring sample](samples/spring-boot-example) |
| Generate Java contracts from Python | [Code-generation guide](docs/codegen.md) · [Codegen sample](samples/java-maven-codegen-example) |
| Validate Java-to-Python contracts during the build | [Opt-in Maven validation](docs/codegen.md#opt-in-java-to-python-contract-validation) |
| Configure metrics, health checks, or tracing | [Runtime guide](docs/runtime.md) · [Tracing guide](docs/tracing.md) |
| Explore the experimental console | [Console setup and security](docs/runtime.md#optional-experimental-console-java-25) |

### Consuming released artifacts

Published artifacts use the Maven group ID **`io.github.ih0r-d`**:

| Artifact | Purpose |
| --- | --- |
| `polyglot-bom` | Dependency alignment for runtime consumers |
| `polyglot-adapter` | Core runtime |
| `polyglot-spring-boot-starter` | Spring Boot integration |

Import the BOM for the **version you have chosen** and add only the modules and language runtimes you need. The latest available release can be checked on [Maven Central](https://central.sonatype.com/artifact/io.github.ih0r-d/polyglot-adapter).

The [runtime documentation](docs/runtime.md) covers setup and configuration; [codegen documentation](docs/codegen.md) covers the separate Maven plugin. Do not assume that unreleased `main` APIs work with the published release.

### Building the development version

If you need functionality currently available only on `main`, build the repository with its [documented toolchain](docs/developer-tooling.md#pinned-oracle-graalvm):

```bash
git clone https://github.com/ih0r-d/polyglot-platform.git
cd polyglot-platform
./mvnw -DskipTests install
```

For project-specific JDK selection, SDKMAN configuration, dependency requirements, and complete consumer setup, follow [Developer Tooling](docs/developer-tooling.md) and the maintained [samples](samples/).

## Examples

The repository includes runnable consumers rather than only isolated code snippets.

| Sample | Demonstrates |
| --- | --- |
| [Java + Maven](samples/java-maven-example) | Framework-neutral integration with a file-backed script source |
| [Java/Python codegen](samples/java-maven-codegen-example) | Generated interfaces, contract validation, and compatibility checks |
| [Spring Boot](samples/spring-boot-example) | Annotated clients, application lifecycle, metrics, and Actuator |
| [Java/Python AOT adapter](samples/java-python-aot-adapter) | Packaging experiments and Native Image verification |
| [Polyglot AI demo](samples/polyglot-ai-demo) | Multi-contract Python execution pipeline |

Check each sample's README for version and setup requirements. Experimental samples should not be treated as general compatibility guarantees.

## Build and development

The project uses Maven as its build system and Taskfile as a contributor-friendly command entry point.

```bash
# Repository build and tests
./mvnw clean verify

# Strict local quality profile
./mvnw -B -ntp -Pquality verify
```

For contributor workflows:

```bash
task dev:setup
task verify
task quality
task samples
task docs:build
```

`task dev:setup` configures repository-local development hooks; it does not publish artifacts. Select the supported JDK using `JAVA_HOME` or `sdk env` before running verification. More commands, configuration details, and troubleshooting are in [Developer Tooling](docs/developer-tooling.md).

### CI and quality gates

The main workflow is [`.github/workflows/ci.yml`](.github/workflows/ci.yml). It separates repository checks from release and publication workflows.

- **Compatibility:** Java 21 baseline and maintained GraalVM 25 runtime verification.
- **Security:** dependency review on pull requests, OSV analysis of the generated Maven SBOM, and CodeQL.
- **Quality:** tests, coverage, formatting/static analysis, API compatibility checks, and SonarCloud reporting.
- **Consumers:** documentation and sample validation as part of the established pipeline.
- **Release:** tag-driven GitHub release workflow; Maven Central publication remains separately controlled and manual.

The exact dependency order, optional gates, and release conditions are documented in [Developer Tooling](docs/developer-tooling.md), [Release Gates](docs/release-gates.md), and [Release Process](docs/release-process.md). An experimental AOT or Native Image check is not proof of general production support.

### Performance

Standalone JMH benchmarks are provided for exploratory performance measurements. Their recorded results are **not** a production latency guarantee or CI performance gate. See [Benchmarks](benchmarks/README.md) and [Baseline](benchmarks/BASELINE.md).

## Documentation

The full documentation is maintained under [`docs/`](docs/index.md). Start with the area relevant to your task.

**For application developers**

| Guide | Contents |
| --- | --- |
| [Overview](docs/overview.md) | Intended use cases and major capabilities |
| [Concepts](docs/concepts.md) | Core terminology and mental model |
| [Runtime](docs/runtime.md) | Integration, configuration, client binding, metrics, and health |
| [Code generation](docs/codegen.md) | Python contracts, Java generation, and Maven validation |
| [Troubleshooting](docs/troubleshooting.md) | Common configuration and runtime issues |
| [Compatibility](docs/compatibility.md) | Verified environments and unsupported combinations |

**For contributors and maintainers**

| Guide | Contents |
| --- | --- |
| [Architecture](docs/architecture.md) | Module boundaries, dependency direction, and key decisions |
| [Public API](docs/public-api.md) | Stable surfaces and compatibility policy |
| [Stability](docs/stability.md) | Stable, experimental, and unsupported features |
| [Runtime semantics](docs/runtime-semantics.md) | Evaluation, caching, synchronization, and resource ownership |
| [Developer tooling](docs/developer-tooling.md) | Toolchains, build commands, CI, and local quality checks |
| [Release checklist](docs/release-checklist.md) | Prerequisites before shipping |
| [Release process](docs/release-process.md) | Release coordination and publishing boundaries |
| [Roadmap](docs/roadmap.md) | Planned and exploratory work |

See the [documentation index](docs/index.md) for the complete list.

## Security and runtime boundaries

Python runs within the application's configured GraalVM environment. The default **`allowAllAccess(true)` is not a sandbox** and must not be used to run untrusted scripts. The starter accesses shared GraalVM contexts serially; this ensures correctness under the documented lifecycle model, not parallel execution throughput.

Review [Runtime Semantics](docs/runtime-semantics.md) for context ownership and shutdown responsibilities. Report vulnerabilities according to [SECURITY.md](SECURITY.md), not through public issue comments containing sensitive details.

## Contributing

Contributions, issue reports, and technical feedback are welcome. Before making changes, review:

- [Contributing guide](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security Policy](SECURITY.md)
- [Changelog](CHANGELOG.md)

Keep changes focused, verify affected contracts, and use the repository's established tests and quality gates. Reflect user-visible changes in the relevant documentation.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

---

🇺🇦 **Слава Україні! Героям слава!**
