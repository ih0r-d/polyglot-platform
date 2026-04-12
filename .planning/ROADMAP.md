# Internal Roadmap

This roadmap is for local planning only. It reflects the current codebase on the `0.3.0-SNAPSHOT`
development line.

## 1. Current State

The project is now a credible Python-first adapter/starter library around GraalVM polyglot
execution.

What is already strong:

- Small public API surface around `@PolyglotClient`, `Convention`, `SupportedLanguage`, and
  `ScriptSource`.
- Coherent Spring Boot starter with autoconfiguration, client scanning, fail-fast startup,
  warmup/preload hooks, actuator integration, and metrics.
- Much more honest runtime documentation than before.
- Python runtime path is clearly the mature path.
- CI, coverage, static analysis, and release flow are materially better than earlier project
  stages.

What is still weak:

- Python instance lifecycle semantics are still implicit rather than deliberately specified.
- Preload behavior is intentionally raw evaluation, but this still needs stronger operational
  framing.
- Cache invalidation and source identity semantics remain coarse.
- JavaScript support is usable only in a narrower runtime-only sense and should stay described that
  way as experimental support before `1.0.0`.
- Executor concurrency is safe via serialization, but still intentionally limited.

## 2. Roadmap Principles

1. Favor credibility over feature count.
2. Keep the project Python-first until JavaScript support earns broader claims.
3. Prefer explicit semantics over accidental behavior.
4. Tighten runtime and operational clarity before expanding product scope.
5. Use `0.3.0` as a semantics/stability release, not a platform-expansion release.
6. Defer large redesign unless current behavior blocks credible usage.

## 3. Recently Landed Work

The immediate post-`0.2.1` runtime-hardening batch is largely done:

- Python lifecycle semantics are clearer in code, docs, and targeted tests.
- Preload semantics are now documented more honestly.
- Cache invalidation gained a more focused runtime API shape.
- JavaScript support is positioned more conservatively and explicitly marked experimental.
- A focused real-context Python lifecycle test has been added.

That batch should now be treated as completed baseline, not as open roadmap scope.

## 4. `0.3.0`

Primary goal: ship a credibility release that combines Python runtime semantics hardening with
code-generation reliability improvements.

Theme:

- Python runtime semantics and build-time contract reliability.

Must-focus topics:

- Python instance lifecycle:
  when exported objects are instantiated, reused, discarded, and recreated.
- Cache semantics:
  what is keyed by interface, what can be invalidated, and what remains coarse by design.
- Preload semantics:
  what it guarantees and what it explicitly does not.
- Fail-fast behavior:
  retain strict startup validation where it improves correctness.
- Codegen reliability:
  strict mode, contract drift detection, and a documented supported parser subset.
- JavaScript support boundaries:
  freeze and document the supported runtime surface honestly as experimental support.

What `0.3.0` should deliver:

- Better specified Python runtime contract.
- Better tests around lifecycle, caching, and startup behavior.
- Better internal and external docs on runtime semantics.
- Better codegen workflows for CI and release quality.
- Possibly narrower cache-control APIs if the shape is obvious and low-risk.

What `0.3.0` should avoid:

- JavaScript parity work.
- Large concurrency redesign.
- Hot reload or multi-version source semantics.
- New execution domains or broad feature expansion.

## 5. Likely Next Minor Versions

After a successful `0.3.0`, likely minor-version themes should be incremental and narrow.

Potential `0.4.x` direction:

- Refine operational ergonomics around cache control, diagnostics, and runtime observability.
- Improve Python contract/codegen coherence.
- Expand sample verification and contributor DX where it improves OSS credibility.

Potential `0.5.x+` direction:

- Revisit JavaScript only if runtime semantics, docs, and tests justify broader support claims.
- Expand only after the Python-first contract is stable and boring.

## 6. Path to `1.0.0`

`1.0.0` should be earned through stabilization, not declared through ambition.

Before `1.0.0`, the project should have:

- A clearly stable public API surface.
- Deliberately specified runtime semantics for Python.
- Honest, bounded JavaScript support claims, with experimental status made explicit.
- Predictable Spring Boot behavior under fail-fast and warmup/preload scenarios.
- Strong enough tests to trust behavior changes.
- Documentation that matches the implementation closely.

`1.0.0` is a stabilization target, not a scope-expansion milestone.

## 7. Explicitly Out Of Scope

The following are explicitly out of scope for this planning horizon:

- Database execution or database-native scripting integration.
- Oracle MLE, PostgreSQL, or similar execution-target expansion.
- Broad platform expansion.
- Hot reload as a first-class runtime feature.
- Multi-version source isolation.
- Aggressive executor parallelism redesign.
- JavaScript parity promises before the runtime actually supports them.

## 8. Prioritization Matrix

### Highest Priority

- Python lifecycle semantics
  Impact: very high
  Effort: medium
  Risk: medium
  Rationale: this is the most important weakness in the strongest product path.

- Cache/source identity clarity
  Impact: high
  Effort: medium
  Risk: medium
  Rationale: current behavior is workable but still coarse and easy to misunderstand.

- Codegen strictness and drift enforcement
  Impact: high
  Effort: medium
  Risk: medium
  Rationale: this is now part of the actual release trajectory and improves CI credibility.

- Documentation honesty around JS and preload
  Impact: high
  Effort: low
  Risk: low
  Rationale: low-cost credibility win.

### Medium Priority

- Additional real-context integration tests
  Impact: medium
  Effort: medium
  Risk: low
  Rationale: useful if tightly selected, wasteful if expanded too broadly.

- Better diagnostics around runtime state and caches
  Impact: medium
  Effort: medium
  Risk: low
  Rationale: helpful after semantics are clarified.

- Sample smoke coverage and docs/version alignment
  Impact: medium
  Effort: medium
  Risk: low
  Rationale: needed for OSS trust, but should not dominate product scope.

### Lower Priority For Now

- Deeper JS runtime support
  Impact: uncertain
  Effort: medium to high
  Risk: high
  Rationale: too easy to over-scope relative to current maturity.

- Concurrency redesign
  Impact: potentially high
  Effort: high
  Risk: high
  Rationale: not justified yet versus current serialized-but-safe model.

## 9. Risks

- Overreacting to current rough edges with a large runtime redesign.
- Treating JavaScript as strategically equal to Python before it is operationally equal.
- Expanding features faster than semantics are defined.
- Letting docs drift back into optimistic language not supported by the code.
- Turning `0.3.0` into a mixed bag rather than a focused credibility release.
