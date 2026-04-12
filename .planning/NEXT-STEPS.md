# Next Steps

This is the working note for the current `0.3.0-SNAPSHOT` iteration.

## 1. Immediate Priorities

- Close the remaining docs/version/sample drift around the `0.3.0` line.
- Keep Python runtime semantics explicit and stable.
- Finish positioning codegen strictness/drift checks as part of the release story.
- Add sample smoke verification so public examples stay credible.
- Avoid broad redesign unless a current behavior is truly blocking credibility.

## 2. Next Engineering Batches

### Batch 1: Release Hygiene and OSS Credibility

Goal:

- make the repository describe the same product it currently implements

Focus:

- README and docs version alignment
- sample version alignment
- changelog cleanup
- sample smoke verification in CI

Expected outcome:

- fewer user-facing contradictions
- more credible release preparation

### Batch 2: Python Runtime Semantics

Goal:

- keep the current Python runtime contract explicit enough to trust

Focus:

- object instantiation behavior for callable exports
- reuse behavior for cached instances
- recreation behavior after weak-reference loss
- repeated evaluation side effects

Expected outcome:

- documented expectations
- targeted tests
- fewer ambiguous runtime assumptions

### Batch 3: Codegen Reliability and DX

Goal:

- make build-time contract generation safer to use in CI and release flows

Focus:

- strict mode messaging
- contract drift workflow
- documented supported Python parser subset
- keeping codegen claims narrower than implementation risk

Expected outcome:

- stronger build-time confidence

## 3. Documentation And DX Work

Documentation work that should happen in this iteration:

- keep README wording Python-first and honest about JavaScript limits
- keep preload wording impossible to misread as contract prebinding
- document the runtime semantics contract in one explicit reference page
- document codegen parser limits and strict-mode expectations
- remove stale version references from docs and sample notes

DX work that should happen in this iteration:

- ensure examples and docs do not imply behavior stronger than the runtime actually guarantees
- ensure sample CI catches drift in maintained examples

## 4. Test And Runtime-Semantics Work

Code/test/CI work:

- keep focused tests for Python weak-reference lifecycle behavior
- add tests for any cache invalidation refinements that are introduced
- keep codegen strictness/drift behavior covered
- add sample smoke verification instead of broad new integration matrices

What not to do:

- do not inflate test count with low-value repetition
- do not build a large integration matrix before the semantics are settled

## 5. What Should Happen Before `0.3.0`

- Python lifecycle semantics should remain written down in code/docs/tests.
- Preload semantics should stay fully explicit.
- Cache behavior should remain clearer and slightly more controllable than in `0.2.x`.
- README, docs, changelog, and samples should all describe the same release line.
- Codegen strict mode and drift-check workflows should be documented as part of the release story.
- Maintained examples should have smoke verification in CI.

## 6. What Should Wait Until After `0.3.0`

- JavaScript code generation.
- Broader JavaScript export/binding models.
- Large executor concurrency redesign.
- Hot reload / live script replacement semantics.
- Broad feature-category expansion.

## 7. Prioritized Action List

1. Tighten README/runtime docs around Python-first positioning, JS limits, preload semantics, and
   serialized execution.
2. Align samples, docs, and changelog with the `0.3.0-SNAPSHOT` development line.
3. Keep targeted Python lifecycle coverage and runtime semantics documentation explicit.
4. Document codegen strict mode, parser limits, and drift-check workflow as release-quality
   features.
5. Add sample smoke CI and use the result to tighten the exact `0.3.0` release gate.
