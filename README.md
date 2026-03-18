# polyglot-adapter

`polyglot-adapter` provides runtime and build-time support for executing Python and JavaScript on the JVM through GraalVM Polyglot.

The repository is organized into three layers:

- `api`: shared annotations and contract model
- `runtime`: core executor API, Spring Boot integration, and BOM
- `build-tools`: contract parsing and Java interface generation

## Requirements

- Build tooling modules: JDK 21+, Maven 3.9+
- Runtime modules: JDK 25+, GraalVM 25.x+, Maven 3.9+
- CI and local quality checks assume the Maven wrapper: `./mvnw`

## Modules

- `api/polyglot-annotations`: public annotations such as `@PolyglotClient`
- `api/polyglot-model`: contract model, parser SPI, and configuration abstractions
- `runtime/polyglot-adapter`: framework-neutral GraalVM execution layer
- `runtime/polyglot-spring-boot-starter`: Spring Boot auto-configuration, client binding, health, and metrics
- `runtime/polyglot-bom`: dependency management for runtime consumers
- `build-tools/polyglot-codegen`: contract parser and Java source generator
- `build-tools/polyglot-codegen-maven-plugin`: Maven integration for code generation

## Runtime Quick Start

Import the runtime BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.ih0r-d</groupId>
      <artifactId>polyglot-bom</artifactId>
      <version>${polyglot.version}</version>
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

## Documentation

The project documentation is maintained in [`docs/`](docs/):

- [`docs/index.md`](docs/index.md)
- [`docs/overview.md`](docs/overview.md)
- [`docs/architecture.md`](docs/architecture.md)
- [`docs/compatibility.md`](docs/compatibility.md)
- [`docs/runtime.md`](docs/runtime.md)
- [`docs/codegen.md`](docs/codegen.md)
- [`docs/concepts.md`](docs/concepts.md)
- [`docs/release-process.md`](docs/release-process.md)
- [`docs/roadmap.md`](docs/roadmap.md)

## Build

Run the full build:

```bash
./mvnw clean verify
```

Run the stricter local quality gate:

```bash
./mvnw -B -ntp -Pquality verify
```

## Samples

The `samples/` directory contains example applications. Those modules are demonstrative only and are not the canonical source of documentation.

## OSS Project Policies

- Contribution guide: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Security policy: [`SECURITY.md`](SECURITY.md)
- Changelog: [`CHANGELOG.md`](CHANGELOG.md)
- Code of conduct: [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)

## License

Licensed under the Apache License 2.0.
