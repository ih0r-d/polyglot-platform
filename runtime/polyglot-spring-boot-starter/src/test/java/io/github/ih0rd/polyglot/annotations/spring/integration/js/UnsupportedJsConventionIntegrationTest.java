package io.github.ih0rd.polyglot.annotations.spring.integration.js;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.client.EnablePolyglotClients;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotAutoConfiguration;

class UnsupportedJsConventionIntegrationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PolyglotAutoConfiguration.class))
          .withUserConfiguration(UnsupportedJsConventionConfig.class)
          .withPropertyValues(
              "polyglot.core.enabled=true",
              "polyglot.core.fail-fast=true",
              "polyglot.core.log-metadata-on-startup=false");

  @Test
  void unsupportedJsConventionFailsThroughSpringStarterPath() {
    contextRunner
        .withBean(
            JsExecutor.class,
            () -> {
              JsExecutor executor = mock(JsExecutor.class);
              doThrow(new BindingException("unsupported convention"))
                  .when(executor)
                  .validateBinding(UnsupportedJsClient.class, Convention.BY_INTERFACE_EXPORT);
              return executor;
            })
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(BindingException.class);
            });
  }
}

@Configuration(proxyBeanMethods = false)
@EnablePolyglotClients
class UnsupportedJsConventionConfig {}

@PolyglotClient(languages = SupportedLanguage.JS, convention = Convention.BY_INTERFACE_EXPORT)
interface UnsupportedJsClient {
  String hello();
}
