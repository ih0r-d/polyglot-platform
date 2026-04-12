# Stability

This page defines the current stability expectations for the repository.

It exists to separate:

- stable targets the project is actively preparing for long-term support
- bounded support that exists but is not on the path to parity before `1.0.0`
- explicitly unsupported areas

## Stability Matrix

| Area | Status | Notes |
| --- | --- | --- |
| Python runtime binding via `PyExecutor` | target for stabilization | Primary path for the `0.3.x` to `1.0.0` journey |
| Spring Boot starter on the Python-first path | target for stabilization | Includes fail-fast, preload, warmup, actuator, and metrics behavior |
| Python-oriented code generation | target for stabilization | Limited to the documented supported parser subset |
| JavaScript runtime binding via `JsExecutor` | experimental | Supported as a bounded runtime path, not a parity target before `1.0.0` |
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

## `1.0.0` Direction

The intended path to `1.0.0` is:

1. stabilize the Python-first runtime contract
2. stabilize the Spring Boot behavior around that contract
3. stabilize the Python-oriented build-time/codegen workflow
4. keep JavaScript explicitly bounded unless it later earns a broader support claim

The project should not expand scope aggressively while those steps remain unfinished.
