package io.github.ih0rd.polyglot.annotations.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotRuntimeMetricsPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PolyglotRuntimeMetricsAutoConfigurationTest {
  interface MetricsApi {
    int value();
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PolyglotRuntimeMetricsAutoConfiguration.class));

  @Test
  void noRegistryDoesNotCreateExecutorsOrRegisterMeters() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(PyExecutor.class);
          assertThat(context).hasSingleBean(PolyglotRuntimeMetricsPostProcessor.class);
        });
  }

  @Test
  void absentMicrometerBacksOff() {
    runner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(PolyglotRuntimeMetricsPostProcessor.class);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"polyglot.metrics.enabled=false", "polyglot.core.enabled=false"})
  void existingDisableSwitchDisablesRuntimeInstrumentation(String disabledProperty) {
    runner
        .withPropertyValues(disabledProperty)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withBean(PyExecutor.class, PolyglotRuntimeMetricsAutoConfigurationTest::executor)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(PolyglotRuntimeMetricsPostProcessor.class);
              context
                  .getBean(PyExecutor.class)
                  .bind(MetricsApi.class, Convention.BY_METHOD_NAME)
                  .value();
              assertThat(context.getBean(MeterRegistry.class).getMeters()).isEmpty();
            });
  }

  @Test
  void realExecutorsShareBoundedMetersAndContextsDoNotShareState() {
    for (int run = 0; run < 2; run++) {
      runner
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withBean(
              "first", PyExecutor.class, PolyglotRuntimeMetricsAutoConfigurationTest::executor)
          .withBean(
              "second", PyExecutor.class, PolyglotRuntimeMetricsAutoConfigurationTest::executor)
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                PyExecutor first = context.getBean("first", PyExecutor.class);
                PyExecutor second = context.getBean("second", PyExecutor.class);
                first.bind(MetricsApi.class, Convention.BY_METHOD_NAME).value();
                second.bind(MetricsApi.class, Convention.BY_METHOD_NAME).value();
                MeterRegistry registry = context.getBean(MeterRegistry.class);
                assertThat(
                        registry
                            .get("polyglot.runtime.operations")
                            .tags("operation", "invocation_execution", "outcome", "success")
                            .timer()
                            .count())
                    .isEqualTo(2);
                assertThat(registry.find("polyglot.runtime.invocations.active").gauges())
                    .hasSize(1);
                assertThat(registry.get("polyglot.runtime.invocations.active").gauge().value())
                    .isZero();
                first.close();
                assertThat(registry.get("polyglot.runtime.executors.closed").counter().count())
                    .isEqualTo(1);
                assertThat(second.bind(MetricsApi.class, Convention.BY_METHOD_NAME).value())
                    .isEqualTo(42);
              });
    }
  }

  private static PyExecutor executor() {
    return new PyExecutor(
        Context.newBuilder(SupportedLanguage.PYTHON.id())
            .option("engine.WarnInterpreterOnly", "false")
            .build(),
        new InMemoryScriptSource(Map.of("python/metrics_api", "def value(): return 42\n")));
  }
}
