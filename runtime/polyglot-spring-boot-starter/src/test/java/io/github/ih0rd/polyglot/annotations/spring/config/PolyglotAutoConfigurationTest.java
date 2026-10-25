package io.github.ih0rd.polyglot.annotations.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.context.SpringPolyglotContextFactory;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotInvocationObserver;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotRuntimeState;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotStartupLifecycle;
import io.github.ih0rd.polyglot.annotations.spring.metrics.PolyglotMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PolyglotAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PolyglotAutoConfiguration.class));

  @Test
  void coreDisabledCreatesNoExecutors() {
    contextRunner
        .withPropertyValues("polyglot.core.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(PyExecutor.class);
              assertThat(context).doesNotHaveBean(JsExecutor.class);
              assertThat(context).doesNotHaveBean(PolyglotExecutors.class);
            });
  }

  @Test
  void pythonEnabledRegistersExecutorsInfrastructure() {
    contextRunner
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotAutoConfiguration.class, PolyglotPythonAutoConfiguration.class))
        .withPropertyValues(
            "polyglot.core.enabled=true",
            "polyglot.python.enabled=true",
            "polyglot.python.resources-path=classpath:python")
        .withBean(PyExecutor.class, Mockito::mock)
        .run(context -> assertThat(context).hasSingleBean(PolyglotExecutors.class));
  }

  @Test
  void jsDisabledDoesNotCreateJsExecutor() {
    contextRunner
        .withPropertyValues("polyglot.core.enabled=true", "polyglot.js.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(JsExecutor.class));
  }

  @Test
  void metricsEnabledRegistersMetricsBinder() {
    contextRunner
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotAutoConfiguration.class, PolyglotMetricsAutoConfiguration.class))
        .withPropertyValues(
            "polyglot.core.enabled=true",
            "polyglot.python.enabled=true",
            "polyglot.python.resources-path=classpath:python",
            "polyglot.metrics.enabled=true")
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PolyglotMetricsBinder.class);
              assertThat(context).hasSingleBean(PolyglotInvocationObserver.class);
            });
  }

  @Test
  void metricsWithoutRegistryDoNotAddInvocationObserver() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(PolyglotMetricsAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(PolyglotMetricsBinder.class);
              assertThat(context).doesNotHaveBean(PolyglotInvocationObserver.class);
            });
  }

  @Test
  void metricsCanBeDisabled() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(PolyglotMetricsAutoConfiguration.class))
        .withPropertyValues("polyglot.metrics.enabled=false")
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(PolyglotMetricsBinder.class);
              assertThat(context).doesNotHaveBean(PolyglotInvocationObserver.class);
            });
  }

  @Test
  void invocationObserverIsScopedToEachApplicationContext() {
    AtomicReference<PolyglotInvocationObserver> first = new AtomicReference<>();
    AtomicReference<PolyglotInvocationObserver> second = new AtomicReference<>();
    ApplicationContextRunner metricsRunner =
        contextRunner
            .withConfiguration(AutoConfigurations.of(PolyglotMetricsAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    metricsRunner.run(context -> first.set(context.getBean(PolyglotInvocationObserver.class)));
    metricsRunner.run(context -> second.set(context.getBean(PolyglotInvocationObserver.class)));

    assertThat(first.get()).isNotSameAs(second.get());
  }

  @Test
  void optionalObservabilityConfigurationsBackOffWhenLibrariesAreAbsent() {
    new ApplicationContextRunner()
        .withClassLoader(
            new FilteredClassLoader(
                MeterRegistry.class,
                org.springframework.boot.health.contributor.HealthIndicator.class,
                InfoContributor.class))
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotAutoConfiguration.class,
                PolyglotActuatorAutoConfiguration.class,
                PolyglotMetricsAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(PolyglotMetricsBinder.class);
              assertThat(context).doesNotHaveBean(PolyglotInvocationObserver.class);
              assertThat(context).doesNotHaveBean(InfoContributor.class);
              assertThat(context)
                  .doesNotHaveBean(
                      org.springframework.boot.health.contributor.HealthIndicator.class);
            });
  }

  @Test
  void actuatorHealthIsUnknownWhenPolyglotCoreIsDisabled() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(PolyglotActuatorAutoConfiguration.class))
        .withPropertyValues("polyglot.core.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(PyExecutor.class);
              assertThat(context).hasSingleBean(HealthIndicator.class);
              assertThat(context.getBean(HealthIndicator.class).health().getStatus().getCode())
                  .isEqualTo("UNKNOWN");
            });
  }

  @Test
  void actuatorHealthIsUnknownUntilStartupLifecycleRecordsState() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PolyglotActuatorAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(PolyglotRuntimeState.class);
              assertThat(context.getBean(HealthIndicator.class).health().getStatus().getCode())
                  .isEqualTo("UNKNOWN");
            });
  }

  @Test
  void startupLifecycleRegistered() {
    contextRunner
        .withPropertyValues(
            "polyglot.core.enabled=true",
            "polyglot.python.enabled=true",
            "polyglot.python.resources-path=classpath:python")
        .run(context -> assertThat(context).hasSingleBean(PolyglotStartupLifecycle.class));
  }

  @Test
  void actuatorDisabledSkipsActuatorIntegration() {
    contextRunner
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotAutoConfiguration.class, PolyglotActuatorAutoConfiguration.class))
        .withPropertyValues("polyglot.core.enabled=true", "polyglot.actuator.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(InfoContributor.class));
  }

  @Test
  void safeDefaultsFalseChangesSpringPythonContextCreation() {
    AtomicBoolean recommendedDefaults = new AtomicBoolean(true);
    Context createdContext = Mockito.mock(Context.class);

    try (MockedStatic<PolyglotHelper> polyglotHelper = Mockito.mockStatic(PolyglotHelper.class)) {
      polyglotHelper
          .when(
              () ->
                  PolyglotHelper.newContext(
                      Mockito.eq(SupportedLanguage.PYTHON), Mockito.anyBoolean(), Mockito.any()))
          .thenAnswer(
              invocation -> {
                recommendedDefaults.set(invocation.getArgument(1, Boolean.class));
                return createdContext;
              });

      contextRunner
          .withPropertyValues("polyglot.python.safe-defaults=false")
          .run(
              context -> {
                SpringPolyglotContextFactory factory =
                    context.getBean(SpringPolyglotContextFactory.class);
                assertThat(factory.create(SupportedLanguage.PYTHON)).isSameAs(createdContext);
                assertThat(recommendedDefaults.get()).isFalse();
              });
    }
  }
}
