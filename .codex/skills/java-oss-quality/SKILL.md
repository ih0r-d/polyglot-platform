---
name: java-oss-quality
description: Strict, evidence-based Java open-source code review and cleanup for libraries, Spring Boot, Maven plugins, and code generators. Use for Java quality audits, PR reviews, production-ready refactoring, naming and JavaDoc cleanup, API compatibility, and generated-code checks. Do not invoke for unrelated programming tasks.
---

# Java OSS Quality

Apply high standards associated with mature Java open-source libraries: readable code, defensible APIs, reproducible builds, safe refactoring, and contributor-friendly changes. Be **strict about defects and evidence**, not arbitrary style metrics. A passing formatter or quality gate does not replace human review.

## Operating contract

1. Read the repository's `../../../AGENTS.md`, contribution guide, formatter, build configuration, API/stability policy, and relevant tests. Repository rules take precedence over this general skill.
2. Determine intent: **audit/review** means read-only; **fix/refactor/cleanup** explicitly authorizes targeted edits. Never silently transform an audit into a rewrite.
3. Inspect `git status`, existing diffs, generated-source boundaries, and test baseline before editing. Preserve unrelated user changes; never commit, push, tag, or publish without authorization.
4. Identify the concrete user-facing or maintainer-facing cost of each finding. Classify P0 (correctness/security), P1 (real maintainability/API risk), P2 (local clarity); omit unproven nitpicks.
5. Use [quality-rubric.md](references/quality-rubric.md) for the enforceable review criteria; use [review-workflow.md](references/review-workflow.md) for sequencing and verification.
6. For changes, fix issues at their **source**, in small logical patches with matching regression tests. Do not modify generated outputs directly when a generator owns them.
7. Re-review final diffs for scope, behavioral changes, documentation accuracy, compatibility, and new complexity.

## Non-negotiable quality gates

- **Names explain semantics**. Flag `arg0`, `arg1`, `arg2`, `param1`, `tmp`, `obj`, `data1`, `result2` *when they are handwritten or generated identifiers with inferable meaning*. First check reflection metadata, bytecode `-parameters`, language-source metadata, and generator origin. Avoid cosmetic renames of legitimate framework-required or conventional variables.
- **One clear responsibility** per class/method; separate workflows only when the extracted boundaries improve comprehension or testability. No blanket method-length limit and no micro-method explosion.
- **No unjustified abstraction**: no speculative interfaces, factories, strategies, builders, wrappers, configuration switches, or dependency additions. A single well-factored class is often preferable.
- **`static` is intentional**, not forbidden. Stateless helpers/constants may be static; state/lifecycle-dependent behavior belongs to owned objects. Avoid mutable static state and hidden global registries.
- **Documentation is a contract**: add accurate Javadoc for externally visible, nontrivial APIs, especially lifecycle, thread safety, ownership, exceptions, nullability, version compatibility, and generated-code behavior. No filler Javadocs on obvious private helpers.
- **Preserve public contracts**: assess source, binary, behavioral, build-tool, configuration, and generated-source compatibility, not only compilation.
- **Correctness outranks appearance**: preserve exception causes, interrupt status, resource cleanup, concurrency invariants, diagnostics, and security boundaries.
- **Tests validate public behavior**: regressions, negative cases, consumer fixtures, golden snapshots where applicable. Never weaken tests or broaden static-analysis suppressions to claim PASS.
- **Readability beats mechanical rules**: do not force Java records, streams, `var`, patterns, dependency injection, or 20-line methods merely for style.
- **Scope discipline**: do not reformat unrelated files, rewrite architecture for visual consistency, or invent new build rules without owner authorization.

## Review output

For reviews, report findings **first**, each with severity, path:line, observed evidence, impact, and smallest safe fix. Explicitly say if no meaningful findings were confirmed. After findings, summarize unverified areas and next action.

For implementation, report: changed scope; actual tests/gates with PASS/FAIL/BLOCKED/NOT RUN; compatibility and generated-output checks; preserved user changes; remaining risks; Git status. Do not claim a clean repository or completed gate without evidence.

## Boundary of this skill

This skill is a *workflow and review standard*, not a substitute for the repository's formatter, Checkstyle/SpotBugs/SonarQube configuration, security review, Java language semantics, or release policy. Do not add custom enforcement scripts, dependencies, or CI jobs unless separately requested.
