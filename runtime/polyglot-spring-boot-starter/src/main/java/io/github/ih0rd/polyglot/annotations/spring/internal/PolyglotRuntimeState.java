package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;

/** Internal runtime state exposed to actuator and Micrometer integrations. */
public final class PolyglotRuntimeState {

  private final AtomicLong startupDurationMs = new AtomicLong(-1);
  private final AtomicInteger availableExecutors = new AtomicInteger();

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

  public long startupDurationMs() {
    return startupDurationMs.get();
  }

  public int availableExecutors() {
    return availableExecutors.get();
  }
}
