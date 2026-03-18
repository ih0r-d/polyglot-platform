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

  /** Returns the Python executor when the Python runtime integration is enabled. */
  public Optional<PyExecutor> python() {
    return Optional.ofNullable(python);
  }

  /** Returns the JavaScript executor when the JavaScript runtime integration is enabled. */
  public Optional<JsExecutor> js() {
    return Optional.ofNullable(js);
  }

  /**
   * Returns the Python executor or throws when Python support is not available.
   *
   * @return configured Python executor
   * @throws IllegalStateException if Python support is disabled
   */
  public PyExecutor requirePython() {
    return Optional.ofNullable(python)
        .orElseThrow(
            () -> new IllegalStateException("Python executor is not enabled or not configured"));
  }

  /**
   * Returns the JavaScript executor or throws when JavaScript support is not available.
   *
   * @return configured JavaScript executor
   * @throws IllegalStateException if JavaScript support is disabled
   */
  public JsExecutor requireJs() {
    return Optional.ofNullable(js)
        .orElseThrow(
            () -> new IllegalStateException("JS executor is not enabled or not configured"));
  }

  /**
   * Returns immutable metadata contributed by all available executors.
   *
   * @return executor metadata keyed by language id
   */
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

  /** Returns whether the Python executor bean is currently available. */
  public boolean isPythonEnabled() {
    return python != null;
  }

  /** Returns whether the JavaScript executor bean is currently available. */
  public boolean isJsEnabled() {
    return js != null;
  }
}
