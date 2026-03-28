package io.github.ih0rd.polyglot.annotations.spring.integration.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.client.EnablePolyglotClients;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotAutoConfiguration;

class DefaultPackageScanIntegrationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PolyglotAutoConfiguration.class))
          .withUserConfiguration(DefaultPackageScanConfig.class)
          .withPropertyValues(
              "polyglot.core.enabled=true",
              "polyglot.core.fail-fast=true",
              "polyglot.core.log-metadata-on-startup=false");

  @Test
  void omittedBasePackagesStillRegistersClientInRealSpringContext() {
    DefaultScannedClient proxy = mock(DefaultScannedClient.class);

    contextRunner
        .withBean(
            PyExecutor.class,
            () -> {
              PyExecutor executor = mock(PyExecutor.class);
              when(executor.bind(DefaultScannedClient.class, Convention.DEFAULT)).thenReturn(proxy);
              return executor;
            })
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(DefaultScannedClient.class);
              assertThat(context.getBean(DefaultScannedClient.class)).isSameAs(proxy);
            });
  }
}

@Configuration(proxyBeanMethods = false)
@EnablePolyglotClients
class DefaultPackageScanConfig {}

@PolyglotClient(languages = SupportedLanguage.PYTHON)
interface DefaultScannedClient {
  String hello();
}
