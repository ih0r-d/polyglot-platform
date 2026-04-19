# Stability

This page defines the current stability expectations for the repository.

It exists to separate:

- stable targets the project is actively preparing for long-term support
- bounded support that exists but is not on the path to parity before `1.0.0`
- explicitly unsupported areas

For the current `0.3.x` line, stability work is anchored to the verified Java 21 baseline, the
verified GraalVM 25.x runtime lane, and the currently verified Spring Boot 4.0.4 starter/sample
line.

## Stability Matrix

| Area | Status | Notes |
| --- | --- | --- |
| Python runtime binding via `PyExecutor` | target for stabilization | Primary path for the `0.3.x` to `1.0.0` journey |
| Spring Boot starter on the Python-first path | target for stabilization | Verified on the current Boot 4.0.4 line; includes fail-fast, preload, warmup, actuator, and metrics behavior |
| Python-oriented code generation | target for stabilization | Limited to the documented supported parser subset |
| JavaScript runtime binding via `JsExecutor` | experimental | Supported as a bounded runtime path, not a parity target before `1.0.0` |
| Public API in non-experimental documented Python-first paths | stability-disciplined | Changes should be narrow, reviewed, and reflected in docs and tests |
| JavaScript code generation | unsupported | Not implemented in the current release line |
| Hot reload / live source replacement | unsupported | Not part of the current runtime contract |
| Multi-version source isolation | unsupported | Not part of the current runtime contract |
| Throughput-oriented shared-context parallelism | unsupported | Current model is safe-by-serialization |

## What Experimental Means Here

For this repository, `experimental` means:

- the capability exists and is tested at a bounded level
- bugs in the documented behavior should still be fixed
- the surface is not a parity target before `1.0.0`
- the contract may still be narrowed, clarified, or explicitly limited before stabilization
- support claims for that surface should stay narrower than the Python-first path

## `1.0.0` Direction

The intended path to `1.0.0` is:

1. stabilize the Python-first runtime contract
2. stabilize the Spring Boot behavior around that contract
3. stabilize the Python-oriented build-time/codegen workflow
4. keep JavaScript explicitly bounded unless it later earns a broader support claim

The project should not expand scope aggressively while those steps remain unfinished.
