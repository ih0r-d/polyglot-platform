package io.github.ih0rd.polyglot.annotations.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsEndpointAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.EvaluationException;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotInvocationObserver;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotRuntimeState;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotWarmupConstants;
import io.micrometer.core.instrument.MeterRegistry;

class PolyglotObservabilityAutoConfigurationTest {

  @Test
  void healthDoesNotResolveBeansAfterApplicationContextCloses() {
    var creations = new AtomicInteger();
    var indicator = new AtomicReference<HealthIndicator>();
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotAutoConfiguration.class, PolyglotActuatorAutoConfiguration.class))
        .withBean(
            PolyglotRuntimeState.class,
            () -> {
              creations.incrementAndGet();
              return new PolyglotRuntimeState();
            })
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              indicator.set(context.getBean(HealthIndicator.class));
              assertThat(indicator.get().health().getStatus().getCode()).isEqualTo("DOWN");
            });

    assertThat(indicator.get().health().getStatus().getCode()).isEqualTo("UNKNOWN");
    assertThat(creations).hasValue(1);
  }

  @Test
  void healthReadsOneLifecycleSnapshotWithoutResolvingLazyExecutors() {
    var state = new PolyglotRuntimeState();
    var creations = new AtomicInteger();
    PyExecutor python = mock(PyExecutor.class);
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PolyglotActuatorAutoConfiguration.class))
        .withBean(PolyglotRuntimeState.class, () -> state)
        .withBean(
            PyExecutor.class,
            () -> {
              creations.incrementAndGet();
              return python;
            },
            definition -> definition.setLazyInit(true))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              HealthIndicator health = context.getBean(HealthIndicator.class);
              assertThat(health.health().getStatus().getCode()).isEqualTo("UNKNOWN");
              state.recordStartup(python, null, 1, false);
              assertThat(health.health().getStatus().getCode()).isEqualTo("UNKNOWN");
              state.recordStartup(python, null, 2, true);
              assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
              assertThat(health.health().getDetails()).containsEntry("pythonEnabled", true);
              state.recordStartupFailure();
              assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");
              state.clearStartup();
              assertThat(health.health().getStatus().getCode()).isEqualTo("UNKNOWN");
              assertThat(creations).hasValue(0);
              verifyNoInteractions(python);
            });
  }

  @Test
  void disabledWarmupAndFailFastLeaveHealthUnknownWithoutGuestExecution() {
    PyExecutor python = mock(PyExecutor.class);
    runtimeRunner(python)
        .withPropertyValues("polyglot.core.fail-fast=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(HealthIndicator.class).health().getStatus().getCode())
                  .isEqualTo("UNKNOWN");
              verifyNoInteractions(python);
            });
  }

  @Test
  void nonFatalWarmupFailureIsDownAndShutdownResetsTheSnapshot() {
    PyExecutor python = mock(PyExecutor.class);
    doThrow(new EvaluationException("expected warmup failure"))
        .when(python)
        .evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    var state = new AtomicReference<PolyglotRuntimeState>();
    runtimeRunner(python)
        .withPropertyValues(
            "polyglot.core.fail-fast=false", "polyglot.python.warmup-on-startup=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              state.set(context.getBean(PolyglotRuntimeState.class));
              assertThat(context.getBean(HealthIndicator.class).health().getStatus().getCode())
                  .isEqualTo("DOWN");
              verify(python).evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
            });
    assertThat(state.get().healthState()).isEqualTo(PolyglotRuntimeState.HealthState.UNKNOWN);
  }

  @Test
  void bootCreatesTheRegistryAndExposesInvocationTimersThroughItsMetricsEndpoint() {
    metricsRunner()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var observer = context.getBean(PolyglotInvocationObserver.class);
              observer.recordInvocation(SupportedLanguage.PYTHON, false, observer.start());
              MetricsEndpoint endpoint = context.getBean(MetricsEndpoint.class);
              assertThat(endpoint.listNames().getNames()).contains("polyglot.client.invocations");
              assertThat(endpoint.metric("polyglot.client.invocations", null)).isNotNull();
              assertThat(
                      context
                          .getBean(MeterRegistry.class)
                          .get("polyglot.client.invocations")
                          .tags("language", "python", "outcome", "success")
                          .timer()
                          .count())
                  .isEqualTo(1);
            });
  }

  @Test
  void bootMeterFiltersCanDisableInvocationMeters() {
    metricsRunner()
        .withPropertyValues("management.metrics.enable.polyglot.client.invocations=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var observer = context.getBean(PolyglotInvocationObserver.class);
              observer.recordInvocation(SupportedLanguage.PYTHON, false, observer.start());
              assertThat(
                      context
                          .getBean(MeterRegistry.class)
                          .find("polyglot.client.invocations")
                          .timers())
                  .isEmpty();
            });
  }

  private ApplicationContextRunner runtimeRunner(PyExecutor python) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotAutoConfiguration.class, PolyglotActuatorAutoConfiguration.class))
        .withBean(PyExecutor.class, () -> python)
        .withPropertyValues(
            "polyglot.python.enabled=true", "polyglot.core.log-metadata-on-startup=false");
  }

  private ApplicationContextRunner metricsRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotAutoConfiguration.class, PolyglotMetricsAutoConfiguration.class,
                MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class,
                CompositeMeterRegistryAutoConfiguration.class,
                    MetricsEndpointAutoConfiguration.class))
        .withPropertyValues("management.endpoints.web.exposure.include=metrics");
  }
}
