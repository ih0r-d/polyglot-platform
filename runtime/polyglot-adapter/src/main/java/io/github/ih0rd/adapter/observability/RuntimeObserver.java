package io.github.ih0rd.adapter.observability;

/**
 * Optional executor observations, independent of any metrics framework.
 *
 * <p>Callbacks must be thread-safe, nonblocking and must not invoke guest code. They receive no
 * contract names, paths or values. Started/completed notifications balance even on failure. Runtime
 * callback failures are isolated from guest execution; VM Errors are not intercepted.
 */
public interface RuntimeObserver {
  /** Signals the start of an operation; reentrant invocations do not emit WAIT. */
  void started(RuntimeOperation operation);

  /** Reports elapsed monotonic nanoseconds and outcome; nested durations are not additive. */
  void completed(RuntimeOperation operation, long elapsedNanos, boolean failed);

  /** Reports a real cache reuse or attempted lookup that did not yield a reusable entry. */
  void cacheAccess(RuntimeCache cache, boolean hit);

  /** Reports actual stored entries evicted; a failed reload may subsequently restore them. */
  void cacheInvalidated(RuntimeCache cache, InvalidationReason reason, int entries);

  /** Reports executor closure, independently of Context ownership. */
  void closed();
}
