package io.github.ih0rd.polyglot.annotations.spring.config;

import org.graalvm.polyglot.Context;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.context.SpringPolyglotContextFactory;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;
import io.github.ih0rd.polyglot.annotations.spring.script.SpringResourceScriptSource;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/**
 * Spring Boot auto-configuration for the Python executor integration.
 *
 * <p>This configuration binds a Python-specific {@link ScriptSource} and creates a {@link
 * PyExecutor} backed by a GraalVM {@link Context} produced by the starter's shared context factory.
 */
@AutoConfiguration
@ConditionalOnClass(PyExecutor.class)
@ConditionalOnProperty(prefix = "polyglot.python", name = "enabled", havingValue = "true")
public class PolyglotPythonAutoConfiguration {

  /** Creates the Python auto-configuration bean container. */
  public PolyglotPythonAutoConfiguration() {}

  /**
   * Creates the Python script source backed by Spring resources.
   *
   * @param resourceLoader Spring resource loader
   * @param properties starter properties
   * @return Python script source
   */
  @Bean
  @ConditionalOnMissingBean(name = "pyScriptSource")
  public ScriptSource pyScriptSource(ResourceLoader resourceLoader, PolyglotProperties properties) {

    return new SpringResourceScriptSource(
        resourceLoader, SupportedLanguage.PYTHON, properties.python().resourcesPath());
  }

  /**
   * Creates the Python executor.
   *
   * @param contextFactory context factory used to build the GraalVM context
   * @param pyScriptSource resolved Python script source
   * @return Python executor
   */
  @Bean
  @ConditionalOnMissingBean
  public PyExecutor pyExecutor(
      SpringPolyglotContextFactory contextFactory, ScriptSource pyScriptSource) {

    Context context = contextFactory.create(SupportedLanguage.PYTHON);
    return new PyExecutor(context, pyScriptSource);
  }
}
