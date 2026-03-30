# polyglot-adapter

[![CI](https://github.com/ih0r-d/polyglot-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ih0r-d/polyglot-platform/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=ih0r-d_polyglot-adapter&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=ih0r-d_polyglot-adapter)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=ih0r-d_polyglot-adapter&metric=coverage)](https://sonarcloud.io/summary/new_code?id=ih0r-d_polyglot-adapter)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

`polyglot-adapter` provides runtime and build-time support for executing Python and JavaScript on the JVM through GraalVM Polyglot.

It provides:

- runtime execution APIs for embedded polyglot calls
- Spring Boot integration for declarative client binding
- build-time code generation from contracts

## Table of Contents

- [Requirements](#requirements)
- [Repository Layout](#repository-layout)
- [Modules](#modules)
- [Installation](#installation)
- [Runtime Quick Start](#runtime-quick-start)
- [Build](#build)
- [Development](#development)
- [Documentation](#documentation)
- [Samples](#samples)
- [OSS Project Policies](#oss-project-policies)
- [License](#license)

## Requirements

- Build tooling modules: JDK 21+, Maven 3.9+
- Runtime modules: JDK 25+, GraalVM 25.x+, Maven 3.9+
- CI and local quality checks assume the Maven wrapper: `./mvnw`

## Repository Layout

The repository is organized into three layers:

- `api`: shared annotations and contract model
- `runtime`: core executor API, Spring Boot integration, and BOM
- `build-tools`: contract parsing and Java interface generation

## Modules

- `api/polyglot-annotations`: public annotations such as `@PolyglotClient`
- `api/polyglot-model`: contract model, parser SPI, and configuration abstractions
- `runtime/polyglot-adapter`: framework-neutral GraalVM execution layer
- `runtime/polyglot-spring-boot-starter`: Spring Boot auto-configuration, client binding, health, and metrics
- `runtime/polyglot-bom`: dependency management for runtime consumers
- `build-tools/polyglot-codegen`: contract parser and Java source generator
- `build-tools/polyglot-codegen-maven-plugin`: Maven integration for code generation

## Installation

Artifacts are published under:

- Group ID: `io.github.ih0r-d`
- BOM: `polyglot-bom`
- Core runtime: `polyglot-adapter`
- Spring Boot starter: `polyglot-spring-boot-starter`

Import the runtime BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.ih0r-d</groupId>
      <artifactId>polyglot-bom</artifactId>
      <version>0.2.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Add the core adapter:

```xml
<dependency>
  <groupId>io.github.ih0r-d</groupId>
  <artifactId>polyglot-adapter</artifactId>
</dependency>
```

Add the Spring Boot starter if needed:

```xml
<dependency>
  <groupId>io.github.ih0r-d</groupId>
  <artifactId>polyglot-spring-boot-starter</artifactId>
</dependency>
```

## Runtime Quick Start

Typical Spring Boot happy path:

```java
@SpringBootApplication
@EnablePolyglotClients
class DemoApplication {}
```

```java
@PolyglotClient
public interface GreetingService {
  String hello(String name);
}
```

Minimal Spring Boot configuration:

```yaml
polyglot:
  core:
    fail-fast: true
  python:
    enabled: true
    resources-path: classpath:python
    warmup-on-startup: true
```

When `@EnablePolyglotClients` does not declare `basePackages`, the starter scans the package of the importing configuration class.

Add only the language runtimes you enable:

```xml
<dependency>
  <groupId>org.graalvm.python</groupId>
  <artifactId>python-embedding</artifactId>
</dependency>
<dependency>
  <groupId>org.graalvm.python</groupId>
  <artifactId>python-launcher</artifactId>
</dependency>
<dependency>
  <groupId>org.graalvm.js</groupId>
  <artifactId>js</artifactId>
  <type>pom</type>
</dependency>
```

## Build

Run the full build:

```bash
./mvnw clean verify
```

Run the stricter local quality gate:

```bash
./mvnw -B -ntp -Pquality verify
```

## Development

Local developer tooling and helper scripts are available in [`.dev/`](.dev/).

If present, start with [`.dev/README.md`](.dev/README.md) for local workflows, helper commands, and repository-specific development utilities.

Main project commands use the Maven wrapper:

```bash
./mvnw clean verify
./mvnw -B -ntp -Pquality verify
```

## Documentation

Project documentation is maintained in [`docs/`](docs/). Start with [`docs/index.md`](docs/index.md).

- [`docs/index.md`](docs/index.md)
- [`docs/overview.md`](docs/overview.md)
- [`docs/architecture.md`](docs/architecture.md)
- [`docs/compatibility.md`](docs/compatibility.md)
- [`docs/runtime.md`](docs/runtime.md)
- [`docs/codegen.md`](docs/codegen.md)
- [`docs/concepts.md`](docs/concepts.md)
- [`docs/release-process.md`](docs/release-process.md)
- [`docs/roadmap.md`](docs/roadmap.md)

## Samples

The `samples/` directory contains maintained example applications pinned to `io.github.ih0r-d` version `0.2.0`.

- `samples/java-maven-example`: framework-neutral runtime API with `FileSystemScriptSource`
- `samples/java-maven-codegen-example`: code generation plus runtime binding
- `samples/spring-boot-example`: Spring Boot starter, `@PolyglotClient`, actuator, and metrics
- `samples/java-python-aot-adapter`: fat JAR and native-image packaging for a Python-backed contract
- `samples/polyglot-ai-demo`: multi-contract Python runtime pipeline using handwritten Java interfaces

Canonical behavior and supported configuration are documented in [`docs/`](docs/).

## OSS Project Policies

- Contribution guide: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Security policy: [`SECURITY.md`](SECURITY.md)
- Changelog: [`CHANGELOG.md`](CHANGELOG.md)
- Code of conduct: [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)

## License

Licensed under the Apache License 2.0.
