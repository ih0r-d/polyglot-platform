# Release Checklist

This page is the operational checklist for preparing a release candidate.

Use it together with [`release-process.md`](release-process.md) and
[`release-gates.md`](release-gates.md).

## Universal Checklist

1. Confirm the target version and release intent are explicit.
2. Confirm `CHANGELOG.md` reflects the release scope honestly.
3. Confirm docs, samples, and version references are aligned with the target release line.
4. Confirm the repository builds under the pinned Java 25 / GraalVM 25 environment.
5. Confirm the relevant CI workflows are green.
6. Confirm no accidental scope drift remains in roadmap or planning notes.
7. Confirm release notes describe public behavior changes and compatibility impact.

## `0.3.0` Checklist

1. Python runtime semantics remain explicit in docs, tests, and code comments.
2. Spring fail-fast, preload, and warmup behavior remain aligned with those semantics.
3. Code generation strict mode, drift checks, and doctor/check workflows are documented and tested.
4. JavaScript is still documented as experimental and non-parity.
5. Maintained samples are aligned with `0.3.0-SNAPSHOT` and have smoke verification in CI.
6. Release-facing docs do not imply broader platform scope than the implementation supports.

## `1.0.0` Pre-Checklist

Do not prepare `1.0.0` until:

1. the Python-first public surface is intentionally stable
2. the starter behavior is stable enough to document as contract, not current behavior
3. the Python-oriented codegen subset is stable enough to support long-term
4. JavaScript status is still either explicitly experimental or deliberately reclassified with
   stronger guarantees
