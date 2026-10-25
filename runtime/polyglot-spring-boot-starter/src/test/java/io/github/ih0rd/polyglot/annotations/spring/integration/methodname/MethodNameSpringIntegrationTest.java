package io.github.ih0rd.polyglot.annotations.spring.integration.methodname;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.client.EnablePolyglotClients;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotActuatorAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotMetricsAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotPythonAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.context.SpringPolyglotContextFactory;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotRuntimeState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MethodNameSpringIntegrationTest {

  @TempDir Path scripts;
  private Context python;
  private MethodNameSpringClient client;

  @BeforeEach
  void writeValidPythonModules() throws Exception {
    Files.writeString(
        scripts.resolve("method_name_spring_client.py"),
        "def callCount():\n    return 1\n"
            + "def failNow():\n    raise ValueError('expected failure')\n");
    Files.writeString(
        scripts.resolve("second_method_name_spring_client.py"),
        "def secondCall():\n    return 42\n");
  }

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
            + "    return module_eval_count\n"
            + "def failNow():\n"
            + "    raise ValueError('expected failure')\n");
    runner()
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              client = application.getBean(MethodNameSpringClient.class);
              SecondMethodNameSpringClient secondClient =
                  application.getBean(SecondMethodNameSpringClient.class);
              assertThat(client.callCount()).isEqualTo(1);
              assertThat(client.callCount()).isEqualTo(1);
              assertThat(secondClient.secondCall()).isEqualTo(42);
              assertThat(application.getBean(PyExecutor.class).isClosed()).isFalse();
            });
  }

  @Test
  void recordsSuccessfulAndFailedCallsAcrossClientsWithBoundedTags() throws Exception {
    AtomicReference<PolyglotRuntimeState> state = new AtomicReference<>();
    metricsRunner()
        .withConfiguration(AutoConfigurations.of(PolyglotActuatorAutoConfiguration.class))
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              state.set(application.getBean(PolyglotRuntimeState.class));
              assertThat(application.getBean(HealthIndicator.class).health().getStatus().getCode())
                  .isEqualTo("UP");
              MethodNameSpringClient first = application.getBean(MethodNameSpringClient.class);
              SecondMethodNameSpringClient second =
                  application.getBean(SecondMethodNameSpringClient.class);
              MeterRegistry registry = application.getBean(MeterRegistry.class);

              assertThat(first.callCount()).isEqualTo(1);
              assertThat(second.secondCall()).isEqualTo(42);
              assertThatThrownBy(first::failNow).isInstanceOf(InvocationException.class);

              Timer success =
                  registry
                      .find("polyglot.client.invocations")
                      .tags("language", "python", "outcome", "success")
                      .timer();
              Timer failure =
                  registry
                      .find("polyglot.client.invocations")
                      .tags("language", "python", "outcome", "failure")
                      .timer();
              Timer unusedJsSuccess =
                  registry
                      .find("polyglot.client.invocations")
                      .tags("language", "js", "outcome", "success")
                      .timer();
              assertThat(success).isNotNull();
              assertThat(success.count()).isEqualTo(2);
              assertThat(failure).isNotNull();
              assertThat(failure.count()).isEqualTo(1);
              assertThat(success.getId().getTags()).hasSize(2);
              assertThat(
                      registry.getMeters().stream()
                          .filter(
                              meter ->
                                  meter.getId().getName().equals("polyglot.client.invocations"))
                          .count())
                  .isEqualTo(4);
              assertThat(unusedJsSuccess).isNotNull();
              assertThat(unusedJsSuccess.count()).isZero();
            });
    assertThat(state.get().healthState()).isEqualTo(PolyglotRuntimeState.HealthState.UNKNOWN);
  }

  @Test
  void clientBindingWorksWithoutMicrometerOrActuatorOnTheClasspath() {
    runner()
        .withClassLoader(
            new FilteredClassLoader(
                "io.micrometer",
                "org.springframework.boot.actuate",
                "org.springframework.boot.health"))
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotMetricsAutoConfiguration.class, PolyglotActuatorAutoConfiguration.class))
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              client = application.getBean(MethodNameSpringClient.class);
              assertThat(client.callCount()).isEqualTo(1);
            });
  }

  @Test
  void disabledMetricsPreserveLazyClientInitializationAndFailureBehavior() throws Exception {
    Files.writeString(scripts.resolve("method_name_spring_client.py"), "invalid python syntax !");
    metricsRunner()
        .withPropertyValues("polyglot.core.fail-fast=false", "polyglot.metrics.enabled=false")
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              assertThat(application.getBean(PyExecutor.class).metadata())
                  .containsEntry("instanceCacheSize", 0);
              assertThat(
                      application
                          .getBean(MeterRegistry.class)
                          .find("polyglot.client.invocations")
                          .timers())
                  .isEmpty();
              assertThatThrownBy(() -> application.getBean(MethodNameSpringClient.class))
                  .hasRootCauseInstanceOf(org.graalvm.polyglot.PolyglotException.class);
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

  private ApplicationContextRunner metricsRunner() {
    return runner()
        .withConfiguration(AutoConfigurations.of(PolyglotMetricsAutoConfiguration.class))
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new);
  }

  @Configuration(proxyBeanMethods = false)
  @EnablePolyglotClients
  static class MethodNameClientConfiguration {}
}
