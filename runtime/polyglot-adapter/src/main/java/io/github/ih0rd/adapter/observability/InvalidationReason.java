package io.github.ih0rd.adapter.observability;

/** Reason for explicit eviction of stored cache entries, not GC or rollback bookkeeping. */
public enum InvalidationReason {
  EXPLICIT,
  CLEAR,
  RELOAD,
  SHUTDOWN
}
