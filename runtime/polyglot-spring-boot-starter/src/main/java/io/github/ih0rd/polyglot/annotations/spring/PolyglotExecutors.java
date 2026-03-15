package io.github.ih0rd.polyglot.annotations.spring;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;

/**
 * Spring-facing facade exposing the executors created by the starter.
 *
 * <p>This avoids leaking optional bean lookups throughout the rest of the Spring integration.
 */
public final class PolyglotExecutors {

  private final @Nullable PyExecutor python;
  private final @Nullable JsExecutor js;

  /**
   * Creates the facade.
   *
   * @param python Python executor, if enabled
   * @param js JavaScript executor, if enabled
   */
  public PolyglotExecutors(@Nullable PyExecutor python, @Nullable JsExecutor js) {
    this.python = python;
    this.js = js;
  }

  /// Returns the Python executor, if present.
  public Optional<PyExecutor> python() {
    return Optional.ofNullable(python);
  }

  /// Returns the JavaScript executor, if present.
  public Optional<JsExecutor> js() {
    return Optional.ofNullable(js);
  }

  /// Returns the Python executor or fails.
  public PyExecutor requirePython() {
    return Optional.ofNullable(python)
        .orElseThrow(
            () -> new IllegalStateException("Python executor is not enabled or not configured"));
  }

  /// Returns the JavaScript executor or fails.
  public JsExecutor requireJs() {
    return Optional.ofNullable(js)
        .orElseThrow(
            () -> new IllegalStateException("JS executor is not enabled or not configured"));
  }

  /** Returns aggregated metadata from all available executors. */
  public Map<String, Object> metadata() {
    Map<String, Object> result = new HashMap<>();
    if (python != null) {
      result.put("python", python.metadata());
    }
    if (js != null) {
      result.put("js", js.metadata());
    }
    return Map.copyOf(result);
  }

  public boolean isPythonEnabled() {
    return python != null;
  }

  public boolean isJsEnabled() {
    return js != null;
  }
}
