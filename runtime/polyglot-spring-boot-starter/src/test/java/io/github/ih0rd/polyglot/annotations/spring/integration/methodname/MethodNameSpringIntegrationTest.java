package io.github.ih0rd.polyglot.annotations.spring.integration.methodname;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.client.EnablePolyglotClients;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotPythonAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.context.SpringPolyglotContextFactory;

class MethodNameSpringIntegrationTest {

  @TempDir Path scripts;
  private Context python;
  private MethodNameSpringClient client;

  @AfterEach
  void springClosesTheRealPythonContext() {
    if (python != null) {
      try {
        assertThatThrownBy(() -> python.eval("python", "1"))
            .isInstanceOf(IllegalStateException.class);
        if (client != null) {
          assertThatThrownBy(client::callCount).isInstanceOf(IllegalStateException.class);
        }
      } finally {
        python.close();
      }
    }
  }

  @Test
  void failFastBoundMethodNameClientEvaluatesModuleOnce() throws Exception {
    Files.writeString(
        scripts.resolve("method_name_spring_client.py"),
        "try:\n"
            + "    module_eval_count = module_eval_count + 1\n"
            + "except NameError:\n"
            + "    module_eval_count = 1\n"
            + "def callCount():\n"
            + "    return module_eval_count\n");

    runner()
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              client = application.getBean(MethodNameSpringClient.class);
              assertThat(client.callCount()).isEqualTo(1);
              assertThat(client.callCount()).isEqualTo(1);
              assertThat(application.getBean(PyExecutor.class).isClosed()).isFalse();
            });
  }

  private ApplicationContextRunner runner() {
    SpringPolyglotContextFactory factory = mock(SpringPolyglotContextFactory.class);
    python = Context.newBuilder(SupportedLanguage.PYTHON.id()).allowAllAccess(true).build();
    when(factory.create(SupportedLanguage.PYTHON)).thenReturn(python);
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotAutoConfiguration.class, PolyglotPythonAutoConfiguration.class))
        .withUserConfiguration(MethodNameClientConfiguration.class)
        .withBean(SpringPolyglotContextFactory.class, () -> factory)
        .withPropertyValues(
            "polyglot.core.enabled=true",
            "polyglot.core.fail-fast=true",
            "polyglot.core.log-metadata-on-startup=false",
            "polyglot.python.enabled=true",
            "polyglot.python.resources-path=" + scripts.toUri());
  }

  @Configuration(proxyBeanMethods = false)
  @EnablePolyglotClients
  static class MethodNameClientConfiguration {}
}
