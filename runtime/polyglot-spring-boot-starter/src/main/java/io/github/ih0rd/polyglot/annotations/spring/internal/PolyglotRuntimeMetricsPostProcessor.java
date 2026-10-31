package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

import io.github.ih0rd.adapter.context.AbstractPolyglotExecutor;
import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Attaches before client validation without resolving executors or creating additional contexts.
 */
public final class PolyglotRuntimeMetricsPostProcessor implements BeanPostProcessor {
  private final ObjectProvider<MeterRegistry> registries;
  private final Map<SupportedLanguage, PolyglotRuntimeMetrics> metrics = new ConcurrentHashMap<>();

  /** Retains only a lazy provider for the application registry. */
  public PolyglotRuntimeMetricsPostProcessor(ObjectProvider<MeterRegistry> registries) {
    this.registries = registries;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof PyExecutor python) {
      attach(python, SupportedLanguage.PYTHON);
    } else if (bean instanceof JsExecutor js) {
      attach(js, SupportedLanguage.JS);
    }
    return bean;
  }

  private void attach(AbstractPolyglotExecutor executor, SupportedLanguage language) {
    MeterRegistry registry = registries.getIfUnique();
    if (registry != null && !executor.isClosed()) {
      executor.attachRuntimeObserver(
          () ->
              metrics.computeIfAbsent(language, key -> new PolyglotRuntimeMetrics(registry, key)));
    }
  }
}
