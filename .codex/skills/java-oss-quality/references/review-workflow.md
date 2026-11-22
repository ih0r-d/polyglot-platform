# Review and cleanup workflow

## Preparation

1. Determine user intent (`review only`, `fix focused findings`, `broad cleanup`) and bounded scope.
2. Read repository AGENTS.md and the nearest module-specific conventions. Identify build tool, formatter, compatibility baseline, and public API policies.
3. Inspect git status and diffs; protect user-owned unrelated files.
4. Inventory handwritten versus generated code and identify generator ownership.

## Evidence-driven review

Inspect in this order: correctness and resource safety; public compatibility; generator/source fidelity; naming; complexity and cohesion; Javadoc; duplication and dependencies; tests and contributor workflow.

For each finding, use:

`P1 | path/to/File.java:line | Evidence -> Impact -> Smallest justified correction -> Required test`

Do not flag a long method solely for line count; include the comprehension or maintenance cost. Do not flag `static` solely by modifier. Do not insist on JavaDoc on every private method. Avoid claims from search hits without reading relevant implementations.

## Changes when authorized

1. Choose a focused group of findings; document intended behavioral invariants.
2. Make smallest readable changes; never rewrite the entire repository by default.
3. Adjust tests for behavior; update Javadocs/consumer examples as needed.
4. For changed generated output, update the generator and compare golden files and compiled consumer behavior.
5. Assess external APIs and compatibility with the previous release when relevant.
6. Run focused tests first, then relevant repository gates; don't run full gates after every rename.
7. Re-read final diff, check unrelated files, perform `git diff --check`, and report remaining concerns and exact verification status.

## Negative examples: do NOT do these

- Rename every `i`, `e`, `id`, or `ctx` without inspecting context.
- Extract each conditional into its own method to hit an arbitrary line limit.
- Convert all utility functions to instance methods or all instance methods to `static`.
- Introduce an interface for every class, an abstraction per method, or unrelated dependency injection.
- Add `/** Returns the result. */` JavaDoc to obvious private functions.
- Edit generated code directly or rename public methods without compatibility review.
- Run the formatter across unrelated dirty files.
- Automatically commit/push changes, or claim Sonar success without checking the server-side gate.

## Suggested test invocations for skill evaluation

- Review a public Java API with excellent names but missing ownership/thread-safety Javadocs: flag the missing contract only.
- Review a generated `arg2` identifier: trace it to source metadata/reflection before suggesting a name.
- Review a cohesive 90-line parsing method: avoid splitting unless complexity or separable duties justify it.
- Review a 12-line method with hidden mutable static state: flag the state despite short length.
- Review a repository with uncommitted unrelated files: preserve them during cleanup.
