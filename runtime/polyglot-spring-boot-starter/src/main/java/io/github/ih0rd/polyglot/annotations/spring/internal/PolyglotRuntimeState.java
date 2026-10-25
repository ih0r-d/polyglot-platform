package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.util.concurrent.atomic.AtomicReference;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;

/** Internal runtime state exposed to actuator and Micrometer integrations. */
public final class PolyglotRuntimeState {

  /** Lifecycle-derived state used by the optional Actuator health contributor. */
  public enum HealthState {
    UNKNOWN,
    UP,
    DOWN
  }

  private final AtomicReference<Snapshot> snapshot =
      new AtomicReference<>(new Snapshot(-1, 0, false, false, HealthState.UNKNOWN));

  /** Creates an empty runtime state snapshot. */
  public PolyglotRuntimeState() {
    // State starts in the "not recorded yet" sentinel state.
  }

  /**
   * Records startup state after executor warmup and validation have completed.
   *
   * @param pyExecutor Python executor, if available
   * @param jsExecutor JavaScript executor, if available
   * @param startupMs measured startup duration in milliseconds
   */
  public void recordStartup(PyExecutor pyExecutor, JsExecutor jsExecutor, long startupMs) {
    recordStartup(pyExecutor, jsExecutor, startupMs, true);
  }

  /**
   * Records whether startup actually exercised a guest runtime, rather than just creating beans.
   */
  public void recordStartup(
      PyExecutor pyExecutor, JsExecutor jsExecutor, long startupMs, boolean initialized) {
    int count = 0;
    if (pyExecutor != null) {
      count++;
    }
    if (jsExecutor != null) {
      count++;
    }
    HealthState healthState = HealthState.UNKNOWN;
    if (count == 0) {
      healthState = HealthState.DOWN;
    } else if (initialized) {
      healthState = HealthState.UP;
    }
    snapshot.set(
        new Snapshot(startupMs, count, pyExecutor != null, jsExecutor != null, healthState));
  }

  /** Records a non-fatal startup failure so the health contributor can report it as DOWN. */
  public void recordStartupFailure() {
    snapshot.set(new Snapshot(-1, 0, false, false, HealthState.DOWN));
  }

  /** Resets startup state to the initial "not recorded yet" values. */
  public void clearStartup() {
    snapshot.set(new Snapshot(-1, 0, false, false, HealthState.UNKNOWN));
  }

  /**
   * Returns the recorded startup duration.
   *
   * @return startup duration in milliseconds, or a negative value if startup was not recorded
   */
  public long startupDurationMs() {
    return snapshot.get().startupDurationMs();
  }

  /**
   * Returns the number of executors available at startup.
   *
   * @return number of available executors
   */
  public int availableExecutors() {
    return snapshot.get().availableExecutors();
  }

  /** Returns the latest lifecycle-derived health state without resolving an executor bean. */
  public HealthState healthState() {
    return snapshot.get().healthState();
  }

  /** Returns one consistent lifecycle snapshot for health reads concurrent with shutdown. */
  public Snapshot snapshot() {
    return snapshot.get();
  }

  /** Immutable lifecycle state; no executor or Context is retained. */
  public record Snapshot(
      long startupDurationMs,
      int availableExecutors,
      boolean pythonAvailable,
      boolean jsAvailable,
      HealthState healthState) {}
}
