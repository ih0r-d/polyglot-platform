package io.github.ih0rd.polyglot.annotations.spring.metrics;

import java.util.Collection;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Micrometer binder that exposes coarse-grained adapter metrics for the configured executors.
 *
 * <p>The binder is intentionally read-only and derives all values from executor metadata snapshots.
 */
public class PolyglotMetricsBinder implements MeterBinder {

  private static final Logger log = LoggerFactory.getLogger(PolyglotMetricsBinder.class);

  private final ObjectProvider<PyExecutor> pyExecutor;
  private final ObjectProvider<JsExecutor> jsExecutor;
  private final boolean pythonConfigured;
  private final boolean jsConfigured;

  /**
   * Creates the binder.
   *
   * @param pyExecutor provider for the optional Python executor
   * @param jsExecutor provider for the optional JavaScript executor
   */
  public PolyglotMetricsBinder(
      ObjectProvider<PyExecutor> pyExecutor,
      ObjectProvider<JsExecutor> jsExecutor,
      boolean pythonConfigured,
      boolean jsConfigured) {
    this.pyExecutor = pyExecutor;
    this.jsExecutor = jsExecutor;
    this.pythonConfigured = pythonConfigured;
    this.jsConfigured = jsConfigured;
  }

  /** Registers all applicable meters against the given registry. */
  @Override
  public void bindTo(@NonNull MeterRegistry registry) {
    if (pythonConfigured) {
      bindPython(registry);
    }
    if (jsConfigured) {
      bindJs(registry);
    }
  }

  private void bindPython(MeterRegistry registry) {
    Tags tags = baseTags(SupportedLanguage.PYTHON);

    Gauge.builder("polyglot.executor.enabled", pyExecutor, provider -> enabled(provider.getIfAvailable()))
        .description("Whether the Python executor is enabled")
        .tags(tags)
        .register(registry);

    Gauge.builder(
            "polyglot.executor.source.cache.size",
            pyExecutor,
            provider -> number(metadata(provider.getIfAvailable()), "sourceCacheSize"))
        .description("Number of cached source units in the Python executor")
        .tags(tags)
        .register(registry);

    Gauge.builder(
            "polyglot.python.instance.cache.size",
            pyExecutor,
            provider -> number(metadata(provider.getIfAvailable()), "instanceCacheSize"))
        .description("Number of cached Python object instances")
        .tags(tags)
        .register(registry);

    Gauge.builder(
            "polyglot.python.bound.interfaces.count",
            pyExecutor,
            provider -> size(metadata(provider.getIfAvailable()), "cachedInterfaces"))
        .description("Number of Java interfaces bound to Python")
        .tags(tags)
        .register(registry);

    log.info(
        "[Polyglot][Metrics] Python metrics registered: "
            + "sourceCacheSize, instanceCacheSize, boundInterfaces");
  }

  private void bindJs(MeterRegistry registry) {
    Tags tags = baseTags(SupportedLanguage.JS);

    Gauge.builder("polyglot.executor.enabled", jsExecutor, provider -> enabled(provider.getIfAvailable()))
        .description("Whether the JavaScript executor is enabled")
        .tags(tags)
        .register(registry);

    Gauge.builder(
            "polyglot.executor.source.cache.size",
            jsExecutor,
            provider -> number(metadata(provider.getIfAvailable()), "sourceCacheSize"))
        .description("Number of cached source units in the JS executor")
        .tags(tags)
        .register(registry);

    Gauge.builder(
            "polyglot.js.loaded.interfaces.count",
            jsExecutor,
            provider -> size(metadata(provider.getIfAvailable()), "loadedInterfaces"))
        .description("Number of Java interfaces loaded in JS")
        .tags(tags)
        .register(registry);

    log.info("[Polyglot][Metrics] JS metrics registered: sourceCacheSize, loadedInterfaces");
  }

  private Tags baseTags(SupportedLanguage language) {
    return Tags.of("language", language.id());
  }

  private static double enabled(Object executor) {
    return executor != null ? 1.0 : 0.0;
  }

  private static Map<String, Object> metadata(PyExecutor executor) {
    return executor != null ? executor.metadata() : Map.of();
  }

  private static Map<String, Object> metadata(JsExecutor executor) {
    return executor != null ? executor.metadata() : Map.of();
  }

  private double number(Map<String, Object> metadata, String key) {
    return metadata.get(key) instanceof Number n ? n.doubleValue() : 0.0;
  }

  private static double size(Map<String, Object> metadata, String key) {
    if (metadata.get(key) instanceof Collection<?> collection) {
      return collection.size();
    }
    return 0.0;
  }
}
