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
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotStartupLifecycle;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

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
  public PolyglotExecutors polyglotExecutors(
      ObjectProvider<PyExecutor> py, ObjectProvider<JsExecutor> js) {

    return new PolyglotExecutors(py.getIfAvailable(), js.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean
  public SpringPolyglotContextFactory polyglotContextFactory(
      ObjectProvider<PolyglotContextCustomizer> customizers) {

    return new SpringPolyglotContextFactory(customizers);
  }

  @Bean
  @ConditionalOnMissingBean
  PolyglotStartupLifecycle polyglotStartupLifecycle(
      PolyglotProperties properties,
      ObjectProvider<PyExecutor> pyExecutor,
      ObjectProvider<JsExecutor> jsExecutor) {

    return new PolyglotStartupLifecycle(
        properties, pyExecutor.getIfAvailable(), jsExecutor.getIfAvailable());
  }
}
