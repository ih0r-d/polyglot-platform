# Runtime Semantics

This page defines the repository's current runtime contract for the Python-first execution path.

It is intentionally narrower than a full platform specification. The goal is to make the current
behavior explicit enough for users, contributors, and release reviews.

## Scope

The semantics below describe what the repository currently treats as the supported runtime model
for:

- `runtime/polyglot-adapter`
- `runtime/polyglot-spring-boot-starter`
- Python bindings driven by `PyExecutor`

JavaScript support remains narrower and is documented separately in [`runtime.md`](runtime.md).

## Contract Naming

The default Python runtime convention is:

- Java interface name: `ForecastService`
- Python module name: `forecast_service.py`
- exported Python value: `ForecastService`

This remains the primary, documented contract model for the project.

## Source Cache Semantics

- Source cache entries are keyed by Java interface type.
- Source cache does not track file versions, checksums, or live source mutation.
- Script changes are not detected automatically after an interface has been resolved.
- `clearSourceCache()` removes cached `Source` objects only.

Implication:

- if the underlying script changes, callers must invalidate or reload the affected contract, or
  recreate the executor.

## Python Instance Cache Semantics

- Resolved Python targets are cached per Java interface type.
- Python targets are stored via weak references.
- A cached Python target may disappear after garbage collection.
- If a weakly cached target is reclaimed, the executor may recreate it on the next invocation.

Implication:

- the project guarantees reuse when the cached target is still reachable;
- it does not guarantee process-wide singleton lifecycle for exported Python objects.

## Evaluation and Binding Semantics

- Preload is raw script evaluation only.
- Preload does not bind Java interfaces.
- Preload does not hydrate per-interface source or instance caches.
- Later binding may evaluate the same script again.
- Repeated script side effects remain possible when preload and later binding both evaluate the same
  source.

Implication:

- preload is a startup warmup/visibility feature, not a hot-reload or prebinding feature.

## Reload and Invalidation

- `invalidateContractCache(iface)` evicts source and Python instance cache entries for one
  contract.
- `reloadContract(iface)` evicts one contract and eagerly re-validates it.
- `reloadContracts(...)` performs the same operation for multiple contracts in one serialized
  operation.
- `clearAllCaches()` is a coarse reset, not a source-version management feature.

## Concurrency Model

- Executor access to the underlying GraalVM `Context` is serialized.
- The current runtime model optimizes for safety of a shared executor instance.
- It does not provide high-throughput parallel execution on one shared context.
- It does not provide multi-version source isolation.

Implication:

- the runtime is safe-by-serialization, not designed as a parallel execution engine.

## Fail-Fast Semantics

- `validateBinding(...)` is the authoritative eager binding check.
- In the Spring starter, `polyglot.core.fail-fast=true` eagerly instantiates discovered
  `@PolyglotClient` beans during startup.
- Startup failure is expected behavior when documented binding requirements are not met.

## Explicit Non-Goals

The current runtime contract does not guarantee:

- hostile sandboxing
- hot reload
- source version tracking
- automatic pickup of changed guest-language code
- JavaScript parity with Python lifecycle behavior
- parallel throughput scaling from one shared executor instance
