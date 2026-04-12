# Contributing to polyglot-adapter

`polyglot-adapter` is a multi-module OSS library for GraalVM-based polyglot execution, Spring Boot integration, and Java code generation. Contributions are expected to preserve API clarity, compatibility discipline, and build reproducibility.

## Before You Start

1. Fork the repository and clone your fork:
   ```bash
   git clone https://github.com/ih0r-d/polyglot-platform.git
   cd polyglot-platform
   ```
2. Build the repository with the Maven wrapper:
   ```bash
   sdk env
   ./mvnw clean verify
   ```
3. Install local pre-commit checks if you use them:
   ```bash
   pip install pre-commit
   pre-commit install
   ```

## Toolchain Baseline

- Maven: 3.9+
- Build tooling modules: JDK 21+
- Runtime modules: JDK 25+
- GraalVM runtime integrations: GraalVM 25.x

The repository root includes [`.sdkmanrc`](.sdkmanrc). For full test execution and any runtime or
sample work, use:

```bash
sdk env
```

If you change the supported matrix, update [`README.md`](README.md), [`docs/compatibility.md`](docs/compatibility.md), and CI workflows in the same change.

## Repository Layout

- `api/`: annotations, contracts, and public model types
- `runtime/`: execution/runtime modules and the runtime BOM
- `build-tools/`: code generation libraries and Maven plugin integration
- `samples/`: reference examples and smoke-test applications
- `docs/`: user-facing documentation
- `.dev/`: maintainer scripts and local automation helpers

## Development Workflow

Prefer the Maven wrapper for all contributor-facing commands:

```bash
sdk env
./mvnw clean verify
./mvnw -B -ntp -Pquality verify
./mvnw spotless:apply
```

Optional maintainer conveniences live in `.dev/`, including release and pre-commit helper scripts.

## Change Expectations

- Keep public API modules minimal and stable.
- Do not introduce cyclic module dependencies.
- Add or update tests for each behavioral change.
- Update documentation when changing public API, configuration, build flow, or compatibility guarantees.
- Avoid mixing refactors with unrelated behavior changes in the same PR.

## Pull Requests

Before opening a pull request:

1. Run `./mvnw clean verify`.
2. Run `./mvnw -B -ntp -Pquality verify` for changes that affect runtime, build tooling, or release logic.
3. Run the relevant sample smoke build when changing public runtime, starter, or codegen behavior.
4. Update docs, samples, or changelog entries when the change affects users.
5. Describe the motivation, scope, and compatibility impact in the PR.

PRs should target `main` unless you are asked to contribute to a release branch.

## Commit Guidance

Use clear, imperative commit messages. Conventional-style prefixes are preferred:

```text
feat(runtime): add script source caching hook
fix(codegen): preserve generic return types in generated client
docs(readme): clarify JDK and GraalVM requirements
```

Keep commits logically grouped and reviewable.

## Reporting Bugs and Security Issues

- Bug reports and feature requests: use GitHub Issues
- Security reports: follow [`SECURITY.md`](SECURITY.md) and do not open public issues for undisclosed vulnerabilities

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0.
