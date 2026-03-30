package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;

/**
 * Internal runtime state exposed to actuator and Micrometer integrations.
 */
public final class PolyglotRuntimeState {

  private final AtomicLong startupDurationMs = new AtomicLong(-1);
  private final AtomicInteger availableExecutors = new AtomicInteger();

  /** Creates an empty runtime state snapshot. */
  public PolyglotRuntimeState() {}

  /**
   * Records startup state after executor warmup and validation have completed.
   *
   * @param pyExecutor Python executor, if available
   * @param jsExecutor JavaScript executor, if available
   * @param startupMs measured startup duration in milliseconds
   */
  public void recordStartup(PyExecutor pyExecutor, JsExecutor jsExecutor, long startupMs) {
    startupDurationMs.set(startupMs);
    int count = 0;
    if (pyExecutor != null) {
      count++;
    }
    if (jsExecutor != null) {
      count++;
    }
    availableExecutors.set(count);
  }

  /**
   * Returns the recorded startup duration.
   *
   * @return startup duration in milliseconds, or a negative value if startup was not recorded
   */
  public long startupDurationMs() {
    return startupDurationMs.get();
  }

  /**
   * Returns the number of executors available at startup.
   *
   * @return number of available executors
   */
  public int availableExecutors() {
    return availableExecutors.get();
  }
}
