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

public class PolyglotMetricsBinder implements MeterBinder {

  private static final Logger log = LoggerFactory.getLogger(PolyglotMetricsBinder.class);

  private final ObjectProvider<PyExecutor> pyExecutor;
  private final ObjectProvider<JsExecutor> jsExecutor;

  public PolyglotMetricsBinder(
      ObjectProvider<PyExecutor> pyExecutor, ObjectProvider<JsExecutor> jsExecutor) {
    this.pyExecutor = pyExecutor;
    this.jsExecutor = jsExecutor;
  }

  @Override
  public void bindTo(@NonNull MeterRegistry registry) {
    pyExecutor.ifAvailable(py -> bindPython(registry, py));
    jsExecutor.ifAvailable(js -> bindJs(registry, js));
  }

  private void bindPython(MeterRegistry registry, PyExecutor executor) {
    Tags tags = baseTags(SupportedLanguage.PYTHON);

    Gauge.builder("polyglot.executor.enabled", executor, ignored -> 1)
        .description("Whether the Python executor is enabled")
        .tags(tags)
        .register(registry);

    Gauge.builder(
            "polyglot.executor.source.cache.size",
            executor,
            ex -> number(ex.metadata(), "sourceCacheSize"))
        .description("Number of cached source units in the Python executor")
        .tags(tags)
        .register(registry);

    Gauge.builder(
            "polyglot.python.instance.cache.size",
            executor,
            ex -> number(ex.metadata(), "instanceCacheSize"))
        .description("Number of cached Python object instances")
        .tags(tags)
        .register(registry);

    Gauge.builder(
            "polyglot.python.bound.interfaces.count",
            executor,
            ex -> size(ex.metadata(), "cachedInterfaces"))
        .description("Number of Java interfaces bound to Python")
        .tags(tags)
        .register(registry);

    log.info(
        "[Polyglot][Metrics] Python metrics registered: "
            + "sourceCacheSize, instanceCacheSize, boundInterfaces");
  }

  private void bindJs(MeterRegistry registry, JsExecutor executor) {
    Tags tags = baseTags(SupportedLanguage.JS);

    Gauge.builder("polyglot.executor.enabled", () -> 1)
        .description("Whether the JavaScript executor is enabled")
        .tags(tags)
        .register(registry);

    Gauge.builder(
            "polyglot.executor.source.cache.size",
            executor,
            ex -> number(ex.metadata(), "sourceCacheSize"))
        .description("Number of cached source units in the JS executor")
        .tags(tags)
        .register(registry);

    Gauge.builder(
            "polyglot.js.loaded.interfaces.count",
            executor,
            ex -> size(ex.metadata(), "loadedInterfaces"))
        .description("Number of Java interfaces loaded in JS")
        .tags(tags)
        .register(registry);

    log.info("[Polyglot][Metrics] JS metrics registered: sourceCacheSize, loadedInterfaces");
  }

  private Tags baseTags(SupportedLanguage language) {
    return Tags.of("language", language.id());
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
