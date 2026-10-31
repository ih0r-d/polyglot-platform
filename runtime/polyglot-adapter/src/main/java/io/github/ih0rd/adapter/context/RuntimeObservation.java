package io.github.ih0rd.adapter.context;

import io.github.ih0rd.adapter.observability.InvalidationReason;
import io.github.ih0rd.adapter.observability.RuntimeCache;
import io.github.ih0rd.adapter.observability.RuntimeObserver;
import io.github.ih0rd.adapter.observability.RuntimeOperation;

/** Best-effort callbacks; no clock reads or per-call observation objects when disabled. */
final class RuntimeObservation {
  private static final System.Logger LOG = System.getLogger(RuntimeObservation.class.getName());
  private static final String CALLBACK_FAILURE = "Runtime observer callback failed";

  private RuntimeObservation() {}

  static long start(RuntimeObserver observer, RuntimeOperation operation) {
    if (observer == null) return 0;
    try {
      observer.started(operation);
    } catch (RuntimeException failure) {
      LOG.log(System.Logger.Level.DEBUG, CALLBACK_FAILURE, failure);
    }
    return System.nanoTime();
  }

  static void finish(
      RuntimeObserver observer, RuntimeOperation operation, long started, boolean failed) {
    if (observer == null) return;
    long elapsed = System.nanoTime() - started;
    try {
      observer.completed(operation, Math.max(0, elapsed), failed);
    } catch (RuntimeException failure) {
      LOG.log(System.Logger.Level.DEBUG, CALLBACK_FAILURE, failure);
    }
  }

  static void cache(RuntimeObserver observer, RuntimeCache cache, boolean hit) {
    if (observer == null) return;
    try {
      observer.cacheAccess(cache, hit);
    } catch (RuntimeException failure) {
      LOG.log(System.Logger.Level.DEBUG, CALLBACK_FAILURE, failure);
    }
  }

  static void invalidate(
      RuntimeObserver observer, RuntimeCache cache, InvalidationReason reason, int count) {
    if (observer == null || count == 0) return;
    try {
      observer.cacheInvalidated(cache, reason, count);
    } catch (RuntimeException failure) {
      LOG.log(System.Logger.Level.DEBUG, CALLBACK_FAILURE, failure);
    }
  }

  static void closed(RuntimeObserver observer) {
    if (observer == null) return;
    try {
      observer.closed();
    } catch (RuntimeException failure) {
      LOG.log(System.Logger.Level.DEBUG, CALLBACK_FAILURE, failure);
    }
  }
}
