# Strict Java OSS review rubric

Apply to relevant production source, public interfaces, test utilities, Maven integrations, and code generators. Record issues only when observed; follow existing local conventions.

## 1. Naming and source clarity

- Classes name a coherent concept; methods name effects or queries; booleans communicate predicates; constants describe units and limits.
- Check vague variables (`arg0`, `arg2`, `tmp`, `obj`, `data1`, `result2`, `thing`, `stuff`) where the role is discoverable. A short loop index `i`, coordinate `x`, or lambda `e` in a tiny context is not inherently bad.
- Distinguish bad source names from reflection's synthesized parameter names. When reflection sees `arg0`, inspect `javac -parameters`, build plugin settings, and JavaParser/GraalPy metadata before renaming source.
- Avoid misleading names like `validate` for a method that mutates, `get` for expensive IO, `isReady` that initializes Context.
- Avoid needless synonyms for the same entity across modules. Do not perform mass renames across public API surfaces without compatibility approval.

## 2. Methods, branching, and OO design

- Flag methods mixing discovery, parsing, validation, mutation, persistence, logging, and lifecycle in one opaque block.
- Flag high cognitive complexity, deeply nested paths, boolean-control flags, inconsistent failure modes, redundant overloads, hidden side effects.
- Prefer guard clauses and meaningful private operations if they expose a domain concept. Do not extract 1-line helpers mechanically.
- Use static for truly stateless logic; object methods for state/identity/overridable behavior. Avoid converting static ↔ instance merely to satisfy aesthetics.
- Avoid feature envy, mutable shared globals, god classes, needless singleton, inheritance without a real polymorphic boundary, and configurable abstractions with only hypothetical consumers.
- Use `var` only when type is unambiguous nearby. Prefer simple iteration over unreadable stream chains; do not forbid streams.

## 3. Contracts, encapsulation, and errors

- Keep APIs as narrow as consumers permit. Avoid exposing internal parser, runtime, transport, or library-specific types unnecessarily.
- Review annotation attributes, defaults, retention and targets; exported generated classes; Maven goals; configuration properties; exception contracts.
- Assess binary/source compatibility and behavior separately. Changing parameter names can affect frameworks using reflection and generated named parameters.
- Preserve failure causality, interrupt flag, resource ownership, transactional boundaries and concurrency synchronization.
- Distinguish null, missing, empty, defaulted and unknown cases clearly. Avoid pretending generic type conversion is verified when it is not.

## 4. Javadoc and comments

- Public library APIs deserve meaningful documentation; private trivial logic usually does not.
- Explain preconditions, invariants, ownership, cache/reload behavior, threading, exceptional cases, and compatibility when not obvious.
- Remove stale, contradictory or misleading comments. Avoid comments that merely translate the implementation into English.
- Ensure code, code comments, API docs and identifiers use English; user-facing review language follows repository instructions.

## 5. Generators and language tooling

- Treat generated code as product output. Check meaningful parameter names, deterministic ordering, valid imports, nullability/types, package names, and call-site compatibility.
- Fix generators/metadata rather than editing generated files. Use golden snapshots for expected output and real downstream consumers for compile/run verification.
- Do not reimplement a language grammar using fragile regex when an established parser is available; semantic contract rules may remain project-specific.
- Never execute untrusted input to "validate" static syntax; if project-specific tooling uses an embedded parser, distinguish parse from guest module evaluation.

## 6. Tests and quality tools

- Readable test names, high-signal assertions, coverage of failure modes, stable fixtures and reproducible environmental prerequisites.
- Prefer behavior-driven tests at the relevant boundary; avoid mocks of the only component whose behavior is under test.
- A clean formatter/Sonar/SpotBugs report is evidence, not proof of clear design. Report code smells and untested assumptions separately.
- Do not suppress warnings broadly, rewrite tests to pass broken behavior, or inflate coverage with redundant assertions.

## 7. OSS contributor experience

- Build and test commands must be documented and reproducible from a clean checkout.
- New abstractions must have a real user, tests, and a clear maintenance owner; minimize dependency/API surface.
- Error messages identify what failed, where, and how to correct it without exposing secrets or user source.
- Keep patches easy for maintainers to reason about: focused changes, consistent conventions, accurate changelog when behavior changes.
