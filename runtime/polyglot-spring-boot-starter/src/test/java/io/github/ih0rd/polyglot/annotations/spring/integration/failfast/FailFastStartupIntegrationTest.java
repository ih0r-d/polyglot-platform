package io.github.ih0rd.polyglot.annotations.spring.integration.failfast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.client.EnablePolyglotClients;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotAutoConfiguration;

class FailFastStartupIntegrationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PolyglotAutoConfiguration.class))
          .withUserConfiguration(FailFastClientConfig.class)
          .withPropertyValues(
              "polyglot.core.enabled=true",
              "polyglot.core.fail-fast=true",
              "polyglot.core.log-metadata-on-startup=false");

  @Test
  void invalidDiscoveredClientFailsContextStartupWhenFailFastIsEnabled() {
    contextRunner
        .withBean(
            PyExecutor.class,
            () -> {
              PyExecutor executor = mock(PyExecutor.class);
              doThrow(new BindingException("broken contract"))
                  .when(executor)
                  .validateBinding(FailingPythonClient.class, Convention.DEFAULT);
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
class FailFastClientConfig {}

@PolyglotClient(languages = SupportedLanguage.PYTHON)
interface FailingPythonClient {
  String hello();
}
