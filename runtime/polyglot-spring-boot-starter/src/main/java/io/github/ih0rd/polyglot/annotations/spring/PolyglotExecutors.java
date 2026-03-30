package io.github.ih0rd.polyglot.annotations.spring;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotObjectProviders;

/**
 * Spring-facing facade exposing the executors created by the starter.
 *
 * <p>This avoids leaking optional bean lookups throughout the rest of the Spring integration.
 */
public final class PolyglotExecutors {

  private final ObjectProvider<PyExecutor> python;
  private final ObjectProvider<JsExecutor> js;

  /**
   * Creates the facade.
   *
   * @param python Python executor, if enabled
   * @param js JavaScript executor, if enabled
   */
  public PolyglotExecutors(@Nullable PyExecutor python, @Nullable JsExecutor js) {
    this(PolyglotObjectProviders.providerOf(python), PolyglotObjectProviders.providerOf(js));
  }

  /** Creates the facade from lazy executor providers. */
  public static PolyglotExecutors fromProviders(
      ObjectProvider<PyExecutor> python, ObjectProvider<JsExecutor> js) {
    return new PolyglotExecutors(python, js);
  }

  private PolyglotExecutors(ObjectProvider<PyExecutor> python, ObjectProvider<JsExecutor> js) {
    this.python = python;
    this.js = js;
  }

  /** Returns the Python executor when the Python runtime integration is enabled. */
  public Optional<PyExecutor> python() {
    return Optional.ofNullable(python.getIfAvailable());
  }

  /** Returns the JavaScript executor when the JavaScript runtime integration is enabled. */
  public Optional<JsExecutor> js() {
    return Optional.ofNullable(js.getIfAvailable());
  }

  /**
   * Returns the Python executor or throws when Python support is not available.
   *
   * @return configured Python executor
   * @throws IllegalStateException if Python support is disabled
   */
  public PyExecutor requirePython() {
    return Optional.ofNullable(python.getIfAvailable())
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
    return Optional.ofNullable(js.getIfAvailable())
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
    PyExecutor pythonExecutor = python.getIfAvailable();
    if (pythonExecutor != null) {
      result.put("python", pythonExecutor.metadata());
    }
    JsExecutor jsExecutor = js.getIfAvailable();
    if (jsExecutor != null) {
      result.put("js", jsExecutor.metadata());
    }
    return Map.copyOf(result);
  }

  /** Returns whether the Python executor bean is currently available. */
  public boolean isPythonEnabled() {
    return python.getIfAvailable() != null;
  }

  /** Returns whether the JavaScript executor bean is currently available. */
  public boolean isJsEnabled() {
    return js.getIfAvailable() != null;
  }
}
