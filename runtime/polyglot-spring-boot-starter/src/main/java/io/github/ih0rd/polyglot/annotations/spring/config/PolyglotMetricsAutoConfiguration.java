package io.github.ih0rd.polyglot.annotations.spring.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotInvocationMetrics;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotInvocationObserver;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotRuntimeState;
import io.github.ih0rd.polyglot.annotations.spring.metrics.PolyglotMetricsBinder;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;
import io.micrometer.core.instrument.MeterRegistry;

/** Auto-configuration that registers Micrometer meters for the configured polyglot executors. */
@AutoConfiguration(
    after = PolyglotAutoConfiguration.class,
    afterName =
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration")
@EnableConfigurationProperties(PolyglotProperties.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "polyglot.metrics", name = "enabled", matchIfMissing = true)
public class PolyglotMetricsAutoConfiguration {

  /** Creates the metrics auto-configuration bean container. */
  public PolyglotMetricsAutoConfiguration() {
    // Default constructor required for Spring Boot auto-configuration instantiation.
  }

  /**
   * Creates the metrics binder.
   *
   * @param py optional Python executor
   * @param js optional JavaScript executor
   * @param meterRegistry Micrometer registry used for meter registration
   * @param properties starter properties
   * @param runtimeState runtime state holder
   * @return metrics binder
   */
  @Bean
  @ConditionalOnBean(PolyglotRuntimeState.class)
  @ConditionalOnMissingBean
  PolyglotMetricsBinder polyglotMetricsBinder(
      ObjectProvider<PyExecutor> py,
      ObjectProvider<JsExecutor> js,
      ObjectProvider<MeterRegistry> meterRegistry,
      PolyglotProperties properties,
      PolyglotRuntimeState runtimeState) {
    return new PolyglotMetricsBinder(
        py,
        js,
        meterRegistry,
        properties,
        runtimeState::availableExecutors,
        runtimeState::startupDurationMs);
  }

  /** Creates the optional observer used to time Spring-managed client method calls. */
  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean(PolyglotInvocationObserver.class)
  PolyglotInvocationObserver polyglotInvocationObserver(MeterRegistry registry) {
    return new PolyglotInvocationMetrics(registry);
  }
}
