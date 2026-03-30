package io.github.ih0rd.polyglot.annotations.spring.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.metrics.PolyglotMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;

/** Auto-configuration that registers Micrometer meters for the configured polyglot executors. */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "polyglot.metrics", name = "enabled", matchIfMissing = true)
public class PolyglotMetricsAutoConfiguration {

  /**
   * Creates the metrics binder.
   *
   * @param py optional Python executor
   * @param js optional JavaScript executor
   * @return metrics binder
   */
  @Bean
  @ConditionalOnMissingBean
  public PolyglotMetricsBinder polyglotMetricsBinder(
      ObjectProvider<PyExecutor> py,
      ObjectProvider<JsExecutor> js,
      ListableBeanFactory beanFactory) {
    return new PolyglotMetricsBinder(
        py,
        js,
        beanFactory.getBeanNamesForType(PyExecutor.class, false, false).length > 0,
        beanFactory.getBeanNamesForType(JsExecutor.class, false, false).length > 0);
  }
}
