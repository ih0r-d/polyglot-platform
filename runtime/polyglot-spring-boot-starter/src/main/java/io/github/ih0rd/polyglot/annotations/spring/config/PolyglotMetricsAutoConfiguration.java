package io.github.ih0rd.polyglot.annotations.spring.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.metrics.PolyglotMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;

@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "polyglot.metrics", name = "enabled", matchIfMissing = true)
public class PolyglotMetricsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public PolyglotMetricsBinder polyglotMetricsBinder(
      ObjectProvider<PyExecutor> py, ObjectProvider<JsExecutor> js) {
    return new PolyglotMetricsBinder(py, js);
  }
}
