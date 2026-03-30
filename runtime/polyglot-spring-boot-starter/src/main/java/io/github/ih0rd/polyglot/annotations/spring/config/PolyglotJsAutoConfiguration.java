package io.github.ih0rd.polyglot.annotations.spring.config;

import org.graalvm.polyglot.Context;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.context.SpringPolyglotContextFactory;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;
import io.github.ih0rd.polyglot.annotations.spring.script.SpringResourceScriptSource;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/**
 * Spring Boot auto-configuration for the JavaScript executor integration.
 *
 * <p>This configuration binds a JavaScript-specific {@link ScriptSource} and creates a {@link
 * JsExecutor} backed by a context from {@link SpringPolyglotContextFactory}.
 */
@AutoConfiguration
@ConditionalOnClass(JsExecutor.class)
@ConditionalOnProperty(prefix = "polyglot.js", name = "enabled", havingValue = "true")
public class PolyglotJsAutoConfiguration {

  /** Creates the JavaScript auto-configuration bean container. */
  public PolyglotJsAutoConfiguration() {}

  /**
   * Creates the JavaScript script source backed by Spring resources.
   *
   * @param resourceLoader Spring resource loader
   * @param properties starter properties
   * @return JavaScript script source
   */
  @Bean
  @ConditionalOnMissingBean(name = "jsScriptSource")
  public ScriptSource jsScriptSource(ResourceLoader resourceLoader, PolyglotProperties properties) {

    return new SpringResourceScriptSource(
        resourceLoader, SupportedLanguage.JS, properties.js().resourcesPath());
  }

  /**
   * Creates the JavaScript executor.
   *
   * @param contextFactory context factory used to build the GraalVM context
   * @param jsScriptSource resolved JavaScript script source
   * @return JavaScript executor
   */
  @Bean
  @ConditionalOnMissingBean
  public JsExecutor jsExecutor(
      SpringPolyglotContextFactory contextFactory, ScriptSource jsScriptSource) {

    Context context = contextFactory.create(SupportedLanguage.JS);
    return new JsExecutor(context, jsScriptSource);
  }
}
