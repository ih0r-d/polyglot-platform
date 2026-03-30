package io.github.ih0rd.polyglot.annotations.spring.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.context.PolyglotContextCustomizer;
import io.github.ih0rd.polyglot.annotations.spring.context.SpringPolyglotContextFactory;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotRuntimeState;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotStartupLifecycle;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

/**
 * Core Spring Boot auto-configuration for the polyglot starter.
 *
 * <p>This configuration exposes the shared executor facade, the context factory, and the startup
 * lifecycle hooks used by the language-specific auto-configurations.
 */
@AutoConfiguration
@EnableConfigurationProperties(PolyglotProperties.class)
@ConditionalOnProperty(
    prefix = "polyglot.core",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolyglotAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public PolyglotRuntimeState polyglotRuntimeState() {
    return new PolyglotRuntimeState();
  }

  /**
   * Creates the facade that exposes whichever executors were configured for the application.
   *
   * @param py optional Python executor
   * @param js optional JavaScript executor
   * @return facade wrapping the available executors
   */
  @Bean
  @ConditionalOnMissingBean
  public PolyglotExecutors polyglotExecutors(
      ObjectProvider<PyExecutor> py, ObjectProvider<JsExecutor> js) {

    return PolyglotExecutors.fromProviders(py, js);
  }

  /**
   * Creates the Spring-aware context factory used by the starter.
   *
   * @param customizers ordered customizer provider
   * @return context factory
   */
  @Bean
  @ConditionalOnMissingBean
  public SpringPolyglotContextFactory polyglotContextFactory(
      PolyglotProperties properties, ObjectProvider<PolyglotContextCustomizer> customizers) {

    return new SpringPolyglotContextFactory(properties, customizers);
  }

  /**
   * Creates the internal lifecycle bean that performs warmup and startup logging.
   *
   * @param properties starter properties
   * @param pyExecutor optional Python executor
   * @param jsExecutor optional JavaScript executor
   * @return startup lifecycle bean
   */
  @Bean
  @ConditionalOnMissingBean
  PolyglotStartupLifecycle polyglotStartupLifecycle(
      PolyglotProperties properties,
      org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory,
      PolyglotRuntimeState runtimeState,
      ObjectProvider<PyExecutor> pyExecutor,
      ObjectProvider<JsExecutor> jsExecutor) {

    return new PolyglotStartupLifecycle(
        properties, beanFactory, runtimeState, pyExecutor, jsExecutor);
  }
}
