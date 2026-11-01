package io.github.ih0rd.polyglot.annotations.spring.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotInvocationTracer;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotInvocationTracing;
import io.micrometer.tracing.Tracer;

/** Optional tracing, independent of metrics and Actuator HTTP exposure. */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration",
      "org.springframework.boot.micrometer.tracing.brave.autoconfigure.BraveAutoConfiguration"
    })
@ConditionalOnClass(Tracer.class)
@ConditionalOnSingleCandidate(Tracer.class)
@Conditional(PolyglotCoreEnabledCondition.class)
@ConditionalOnProperty(prefix = "management.tracing", name = "enabled", matchIfMissing = true)
public class PolyglotTracingAutoConfiguration {
  /** Creates the optional tracing configuration. */
  public PolyglotTracingAutoConfiguration() {}

  @Bean
  @ConditionalOnMissingBean(PolyglotInvocationTracing.class)
  PolyglotInvocationTracing polyglotInvocationTracing(Tracer tracer) {
    return new PolyglotInvocationTracer(tracer);
  }
}
