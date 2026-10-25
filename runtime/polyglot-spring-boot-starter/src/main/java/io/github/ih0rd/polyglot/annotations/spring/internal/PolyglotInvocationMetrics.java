package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.NonNull;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Records duration and success/failure for Spring-managed client calls. */
public final class PolyglotInvocationMetrics implements PolyglotInvocationObserver {

  private static final String METER_NAME = "polyglot.client.invocations";
  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_FAILURE = "failure";

  private final Clock clock;
  private final TimerPair pythonTimers;
  private final TimerPair jsTimers;

  /** Creates metrics backed by the application's registry. */
  public PolyglotInvocationMetrics(@NonNull MeterRegistry registry) {
    this.clock = registry.config().clock();
    this.pythonTimers = timers(registry, SupportedLanguage.PYTHON);
    this.jsTimers = timers(registry, SupportedLanguage.JS);
  }

  @Override
  public long start() {
    return clock.monotonicTime();
  }

  private static TimerPair timers(MeterRegistry registry, SupportedLanguage language) {
    return new TimerPair(
        registry.timer(METER_NAME, "language", language.id(), "outcome", OUTCOME_SUCCESS),
        registry.timer(METER_NAME, "language", language.id(), "outcome", OUTCOME_FAILURE));
  }

  @Override
  public void recordInvocation(SupportedLanguage language, boolean failed, long startedAt) {
    TimerPair timers =
        switch (language) {
          case PYTHON -> pythonTimers;
          case JS -> jsTimers;
        };
    Timer timer = failed ? timers.failure() : timers.success();
    timer.record(clock.monotonicTime() - startedAt, TimeUnit.NANOSECONDS);
  }

  private record TimerPair(Timer success, Timer failure) {}
}
