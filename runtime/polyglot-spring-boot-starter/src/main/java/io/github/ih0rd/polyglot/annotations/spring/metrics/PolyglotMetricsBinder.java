package io.github.ih0rd.polyglot.annotations.spring.metrics;

import java.util.Collection;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotRuntimeState;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Micrometer binder that exposes coarse-grained adapter metrics for the configured executors.
 *
 * <p>The binder is intentionally read-only and derives all values from executor metadata snapshots.
 */
public class PolyglotMetricsBinder implements SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(PolyglotMetricsBinder.class);

  private final ObjectProvider<PyExecutor> pyExecutor;
  private final ObjectProvider<JsExecutor> jsExecutor;
  private final ObjectProvider<MeterRegistry> meterRegistry;
  private final PolyglotProperties properties;
  private final PolyglotRuntimeState runtimeState;

  /**
   * Creates the binder.
   *
   * @param pyExecutor provider for the optional Python executor
   * @param jsExecutor provider for the optional JavaScript executor
   * @param meterRegistry Micrometer registry used for registration
   * @param properties starter properties
   * @param runtimeState runtime state holder
   */
  public PolyglotMetricsBinder(
      ObjectProvider<PyExecutor> pyExecutor,
      ObjectProvider<JsExecutor> jsExecutor,
      ObjectProvider<MeterRegistry> meterRegistry,
      PolyglotProperties properties,
      PolyglotRuntimeState runtimeState) {
    this.pyExecutor = pyExecutor;
    this.jsExecutor = jsExecutor;
    this.meterRegistry = meterRegistry;
    this.properties = properties;
    this.runtimeState = runtimeState;
  }

  @Override
  public void afterSingletonsInstantiated() {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    if (registry != null) {
      bindTo(registry);
    }
  }

  /**
   * Registers all applicable meters against the given registry.
   *
   * @param registry registry that receives the created meters
   */
  public void bindTo(@NonNull MeterRegistry registry) {
    Gauge.builder(
            "polyglot.executor.available.count", runtimeState, state -> state.availableExecutors())
        .description("Number of currently available polyglot executors")
        .register(registry);

    Gauge.builder(
            "polyglot.executor.configured.count", this, binder -> binder.configuredExecutors())
        .description("Number of polyglot executors enabled by configuration")
        .register(registry);

    Gauge.builder(
            "polyglot.startup.duration",
            runtimeState,
            state -> nonNegative(state.startupDurationMs()))
        .description("Polyglot startup initialization duration in milliseconds")
        .baseUnit("milliseconds")
        .register(registry);

    int registeredLanguages = 0;
    if (properties.python().enabled()) {
      bindPython(registry);
      registeredLanguages++;
    }
    if (properties.js().enabled()) {
      bindJs(registry);
      registeredLanguages++;
    }
    if (registeredLanguages > 0 && log.isDebugEnabled()) {
      log.debug(
          "[Polyglot][Metrics] Registered metrics for {} language(s): executor.enabled, "
              + "executor.source.cache.size, executor.contract.cache.size",
          registeredLanguages);
    }
  }

  private void bindPython(MeterRegistry registry) {
    Tags tags = baseTags(SupportedLanguage.PYTHON);

    Gauge.builder(
            "polyglot.executor.enabled", pyExecutor, provider -> enabled(provider.getIfAvailable()))
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

    Gauge.builder(
            "polyglot.executor.contract.cache.size",
            pyExecutor,
            provider -> number(metadata(provider.getIfAvailable()), "instanceCacheSize"))
        .description("Number of cached polyglot contract bindings")
        .tags(tags)
        .register(registry);
  }

  private void bindJs(MeterRegistry registry) {
    Tags tags = baseTags(SupportedLanguage.JS);

    Gauge.builder(
            "polyglot.executor.enabled", jsExecutor, provider -> enabled(provider.getIfAvailable()))
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

    Gauge.builder(
            "polyglot.executor.contract.cache.size",
            jsExecutor,
            provider -> size(metadata(provider.getIfAvailable()), "loadedInterfaces"))
        .description("Number of cached polyglot contract bindings")
        .tags(tags)
        .register(registry);
  }

  private Tags baseTags(SupportedLanguage language) {
    return Tags.of("language", language.id());
  }

  private int configuredExecutors() {
    int configured = 0;
    if (properties.python().enabled()) {
      configured++;
    }
    if (properties.js().enabled()) {
      configured++;
    }
    return configured;
  }

  private static double enabled(Object executor) {
    return executor != null ? 1.0 : 0.0;
  }

  private static double nonNegative(long value) {
    return value >= 0 ? value : 0.0;
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
