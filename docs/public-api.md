# Public API

This page defines the repository's intended public surface for the path to `1.0.0`.

It is not a promise that every item below is already fully stabilized. It is the list of surfaces
that should be treated as user-facing and reviewed with compatibility discipline.

For the current `0.5.x` release-preparation line:

- Java 21 is the minimum repository baseline
- Java 21 and Java 25 are the exercised verification lanes
- GraalVM 25.x is the verified runtime line
- Spring Boot support is verified on the current Boot 4.1.0 line only
- Python is the primary stabilization path
- JavaScript runtime paths remain experimental, including facades without `@ExperimentalApi`

## Intended Public Surface

### Core API

- `@PolyglotClient`
- `Convention`
- `SupportedLanguage`
- `@ExperimentalApi` as the repository marker for bounded published surface
- `ScriptSource`

`ScriptSource` accepts logical script names, not raw filesystem paths or resource locations. A
logical script name is a relative `/`-separated name without the language extension, for example
`forecast_service` or `scripts/utils/helper`. Path traversal, absolute paths, Windows drive or UNC
paths, backslash separators, and URI/resource prefixes such as `file:` or `classpath:` are rejected
by built-in implementations.

### Runtime API

- `PyExecutor`
- `JsExecutor` as an experimental runtime type
- `ContextOwnership`
- runtime binding methods such as `bind(...)` and `validateBinding(...)`

### Spring Boot API

- `@EnablePolyglotClients`
- starter configuration under `polyglot.*`
- `PolyglotExecutors` as the Spring-facing runtime facade
- documented actuator and metrics integration published by the starter

### Build-Time API

- `polyglot-codegen-maven-plugin` goals:
  `generate`, `check`, `doctor`, `validate-contracts`
- documented plugin parameters and defaults, including `onlyIncludedMethods`, `strictMode`,
  `failOnNoContracts`, `skipUnchanged`, `failOnContractDrift`, and the opt-in `validateContracts`,
  `discoverContracts`, `contracts`, `inputDirectory` and `sourceDirectory` settings for Python
  contract validation

## Module Boundaries

“Supported” below identifies a user-facing contract subject to the
[compatibility policy](compatibility.md#compatibility-contract), not a claim of `1.0.0` maturity.
Java `public` visibility alone does not establish support. Entries marked pending below are
recommendations requiring maintainer approval, not newly established guarantees.

| Module | Supported API / SPI | Internal or bounded surface |
| --- | --- | --- |
| `polyglot-annotations` | `@PolyglotClient`, `Convention`, `SupportedLanguage`, `@ExperimentalApi` | `SupportedLanguage.JS` is experimental; the enum's Python path is not |
| `polyglot-model` | Shared `ContractModel`, `ContractClass`, `ContractMethod`, `ContractParam`, `PolyType` variants, `CodegenConfig`, `ScriptDescriptor`; `ScriptSource` and `LanguageParser` SPI | `ScriptNames` is a validation helper, not an extension point; model values do not imply a stable serialized format |
| `polyglot-adapter` | `PyExecutor`, `ContextOwnership`, documented runtime exceptions; `PolyglotHelper.newContext(...)`; classpath, filesystem, in-memory and composite `ScriptSource` implementations | `JsExecutor` is experimental; executor base-class extension and utility implementation details are internal |
| `polyglot-spring-boot-starter` | `@EnablePolyglotClients`, `PolyglotExecutors`, `PolyglotProperties` configuration, documented actuator/metrics integration; `PolyglotContextCustomizer` SPI and `SpringResourceScriptSource` | JavaScript paths are experimental; Spring wiring classes are implementation details |
| `polyglot-codegen` | Documented Python generation workflow and CLI invocation; Java entry-point decisions below are pending | Parser, validator, mapper and rendering helpers are not automatically supported because they are public |
| `polyglot-codegen-maven-plugin` | `generate`, `check`, `doctor` goals, documented parameters/defaults and generated-source registration | Mojo classes, support classes and `model` records are implementation details, not Java extension APIs |
| `polyglot-bom` | Documented dependency alignment for consumers | No Java API; parent POMs and samples are build/reference inputs |

### Runtime Calls And Extension Points

The supported `PyExecutor` calling surface includes constructors/factories, `languageId()`,
`bind(...)`, `validateBinding(...)`, `evaluate(String)`, preload, documented cache invalidation
and explicit reload methods, `metadata()`, `isClosed()` and `close()`. This includes public methods
inherited from `AbstractPolyglotExecutor`. Their compatibility must be checked even though the base
class's protected fields, hooks and subclassing model are internal. `metadata()` is diagnostic;
its arbitrary map contents are not a versioned serialization schema.

`PolyglotHelper` is documented for context creation; its Python entry points and documented
customization behavior are user-facing. GraalVM implementation internals are not covered.
Runtime semantics, including ownership, serialization and explicit reload limitations, remain in
[runtime semantics](runtime-semantics.md).

`ScriptSource` supports application implementations: logical names follow the rules above and
`open(...)` returns a fresh reader that the caller may close. `LanguageParser` is a declared
static-parser SPI with `language()` and `parse(...)`; it does not imply automatic discovery or
registration in the default generator or Maven plugin. `PolyglotContextCustomizer` is the starter
callback SPI; its default order is zero and lower orders execute first.

Spring auto-configuration classes, client registrar/factory beans, actuator contributors, metrics
binders and `internal` components implement the documented integration. Public visibility does not
make their constructors, lifecycle hooks or inheritance supported extension points. The factory
boundary remains pending below. Documented properties, defaults, facade calls and operational behavior
remain user-facing independently of those implementation classes.

### Pending Java API Decisions

These decisions need explicit maintainer approval before a supported Java API allowlist is finalized.
Existing consumers and release notes must be considered; an internal recommendation does not erase
an already shipped incompatibility.

| Surface | Verified usage / current documentation | Recommendation and compatibility implication |
| --- | --- | --- |
| `JavaTypeRenderer` | Used by `JavaInterfaceGenerator`; public helper without an explicit internal marker. The v0.4.0 `String render(PolyType)`, `getImports()` and `reset()` signatures are retained; JavaParser AST rendering is package-private | Recommend explicitly classifying as internal for future use, while retaining the released signatures for compatibility. The maintainer still needs to decide whether any direct helper usage is supported |
| `JavaInterfaceGenerator.generate(...)` | Public deterministic source entry point used by Maven tooling | Recommend supported Java generation entry point; confirm constructor, method and generated-text scope explicitly |
| `JavaInterfaceGenerator.generateAst(...)` | Public Javadoc explicitly invites build integrations and tests; returns JavaParser `CompilationUnit` | Recommend an explicitly bounded integration API pending approval, not silently internal. Changing its signature or JavaParser dependency types can break consumers |
| `ContractGenerator` / `DefaultContractGenerator` | Documented generation pipeline; public `generate(...)` and default constructor. Parser-map constructor is package-private and the default class is final | Recommend supporting the interface as a programmatic contract and the default constructor/generation path. Approve whether third-party implementations are a supported SPI; there is currently no public parser-registration mechanism |
| Other codegen helpers | `LanguageTypeMapper` is a public language-specific mapping interface; parser, validator and CLI helper classes are publicly accessible implementation parts | Recommend internal implementations; confirm any mapper SPI commitment separately. Do not infer default-generator injection support or subclassing guarantees |
| `AbstractPolyglotExecutor` | Architecture docs already exclude stable third-party subclassing; concrete executors inherit its public calls | Retain the internal subclassing boundary; no new approval is needed to restate it. Removing inherited public calls still breaks supported consumers and follows the compatibility policy |
| Public Spring implementation classes | Auto-configuration and client/operational classes provide the documented integration. `SpringPolyglotContextFactory` has public constructor/create methods and is named in architecture docs, without an explicit internal marker | Recommend implementation-only Java APIs, retaining supported configuration and customizer behavior. Maintainer approval is needed for the ambiguous factory boundary; internal classification does not undo binary/source impact on existing direct callers |
| `CommonUtils` | Javadoc explicitly excludes it from supported API; retained for source/binary compatibility, `@Deprecated(since = "0.3.1", forRemoval = true)` | Keep legacy status, not a new supported API. Removal still breaks callers and needs an explicit maintainer release/migration decision; no removal release is currently specified |

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

### Experimental console module

`polyglot-spring-boot-console` is an optional Java 25 artifact. Its three `polyglot.console.*`
properties are experimental configuration. `autoconfigure.ConsoleAutoConfiguration` is framework wiring;
controllers, DTOs, data collection, asset paths and browser API are internal, without public
Java/SPI compatibility guarantees. This module does not extend the supported API of the regular
starter. It has no 0.4.0 artifact, so its Maven Revapi execution is explicitly skipped; the four
existing compatibility checks remain active and unchanged.
