package io.github.ih0rd.polyglot.annotations.spring.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotRuntimeMetricsPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;

/** Optional runtime observations, using the existing global Polyglot metrics switch. */
@AutoConfiguration(after = PolyglotMetricsAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@Conditional(PolyglotCoreEnabledCondition.class)
@ConditionalOnProperty(prefix = "polyglot.metrics", name = "enabled", matchIfMissing = true)
public class PolyglotRuntimeMetricsAutoConfiguration {
  /** Creates the optional runtime metrics configuration. */
  public PolyglotRuntimeMetricsAutoConfiguration() {}

  @Bean
  @ConditionalOnMissingBean
  static PolyglotRuntimeMetricsPostProcessor polyglotRuntimeMetricsPostProcessor(
      ObjectProvider<MeterRegistry> registries) {
    return new PolyglotRuntimeMetricsPostProcessor(registries);
  }
}
