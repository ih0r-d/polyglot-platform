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

/// # PolyglotJsAutoConfiguration
///
/// Spring Boot autoconfiguration for {@link JsExecutor}.
///
/// Responsibilities:
/// - Create language-bound {@link ScriptSource} for JavaScript
/// - Create {@link JsExecutor} using {@link SpringPolyglotContextFactory}
/// - No warmup or lifecycle orchestration (handled internally)
///
@AutoConfiguration
@ConditionalOnClass(JsExecutor.class)
@ConditionalOnProperty(prefix = "polyglot.js", name = "enabled", havingValue = "true")
public class PolyglotJsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "jsScriptSource")
  public ScriptSource jsScriptSource(ResourceLoader resourceLoader, PolyglotProperties properties) {

    return new SpringResourceScriptSource(
        resourceLoader, SupportedLanguage.JS, properties.js().resourcesPath());
  }

  @Bean
  @ConditionalOnMissingBean
  public JsExecutor jsExecutor(
      SpringPolyglotContextFactory contextFactory, ScriptSource jsScriptSource) {

    Context context = contextFactory.create(SupportedLanguage.JS);
    return new JsExecutor(context, jsScriptSource);
  }
}
