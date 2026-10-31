package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.ih0rd.adapter.observability.InvalidationReason;
import io.github.ih0rd.adapter.observability.RuntimeCache;
import io.github.ih0rd.adapter.observability.RuntimeObserver;
import io.github.ih0rd.adapter.observability.RuntimeOperation;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Cached, bounded meters. No executor, Context or contract is retained by registry gauges. */
final class PolyglotRuntimeMetrics implements RuntimeObserver {
  private final Timer[][] operations = new Timer[RuntimeOperation.values().length][2];
  private final Counter[][] accesses = new Counter[RuntimeCache.values().length][2];
  private final Counter[][] invalidations =
      new Counter[RuntimeCache.values().length][InvalidationReason.values().length];
  private final AtomicInteger active = new AtomicInteger();
  private final AtomicInteger waiting = new AtomicInteger();
  private final AtomicInteger reloads = new AtomicInteger();
  private final Counter closed;

  /** Registers bounded meter IDs once, before an observer is attached to an executor. */
  PolyglotRuntimeMetrics(MeterRegistry registry, SupportedLanguage language) {
    registerOperations(registry, language);
    registerCaches(registry, language);
    gauge(registry, language, "polyglot.runtime.invocations.active", active);
    gauge(registry, language, "polyglot.runtime.invocations.waiting", waiting);
    if (language == SupportedLanguage.PYTHON) {
      gauge(registry, language, "polyglot.runtime.reloads.active", reloads);
    }
    closed =
        Counter.builder("polyglot.runtime.executors.closed")
            .baseUnit("executors")
            .tag("language", language.id())
            .register(registry);
  }

  @Override
  public void started(RuntimeOperation operation) {
    AtomicInteger inProgress = inProgress(operation);
    if (inProgress != null) inProgress.incrementAndGet();
  }

  @Override
  public void completed(RuntimeOperation operation, long elapsedNanos, boolean failed) {
    AtomicInteger inProgress = inProgress(operation);
    if (inProgress != null) inProgress.decrementAndGet();
    Timer timer = operations[operation.ordinal()][failed ? 1 : 0];
    if (timer != null) timer.record(elapsedNanos, TimeUnit.NANOSECONDS);
  }

  @Override
  public void cacheAccess(RuntimeCache cache, boolean hit) {
    Counter counter = accesses[cache.ordinal()][hit ? 0 : 1];
    if (counter != null) counter.increment();
  }

  @Override
  public void cacheInvalidated(RuntimeCache cache, InvalidationReason reason, int entries) {
    Counter counter = invalidations[cache.ordinal()][reason.ordinal()];
    if (counter != null) counter.increment(entries);
  }

  @Override
  public void closed() {
    closed.increment();
  }

  private AtomicInteger inProgress(RuntimeOperation operation) {
    return switch (operation) {
      case INVOCATION_WAIT -> waiting;
      case INVOCATION_EXECUTION -> active;
      case RELOAD -> reloads;
      default -> null;
    };
  }

  private void registerOperations(MeterRegistry registry, SupportedLanguage language) {
    for (RuntimeOperation operation : RuntimeOperation.values()) {
      if (language == SupportedLanguage.JS
          && (operation == RuntimeOperation.RELOAD
              || operation == RuntimeOperation.INSTANCE_CREATION
              || operation == RuntimeOperation.CONTRACT_VALIDATION)) continue;
      for (int outcome = 0; outcome < 2; outcome++) {
        operations[operation.ordinal()][outcome] =
            Timer.builder("polyglot.runtime.operations")
                .description(
                    "Executor operation duration; scopes may nest and are not guest CPU time")
                .tags(
                    "language",
                    language.id(),
                    "operation",
                    id(operation),
                    "outcome",
                    outcome == 0 ? "success" : "failure")
                .register(registry);
      }
    }
  }

  private void registerCaches(MeterRegistry registry, SupportedLanguage language) {
    for (RuntimeCache cache : RuntimeCache.values()) {
      if (language == SupportedLanguage.JS && cache != RuntimeCache.SOURCE) continue;
      for (int result = 0; result < 2; result++) {
        accesses[cache.ordinal()][result] =
            Counter.builder("polyglot.runtime.cache.access")
                .baseUnit("lookups")
                .tags(
                    "language",
                    language.id(),
                    "cache",
                    id(cache),
                    "result",
                    result == 0 ? "hit" : "miss")
                .register(registry);
      }
      for (InvalidationReason reason : InvalidationReason.values()) {
        invalidations[cache.ordinal()][reason.ordinal()] =
            Counter.builder("polyglot.runtime.cache.invalidations")
                .baseUnit("entries")
                .tags("language", language.id(), "cache", id(cache), "reason", id(reason))
                .register(registry);
      }
    }
  }

  private static void gauge(
      MeterRegistry registry, SupportedLanguage language, String name, AtomicInteger value) {
    Gauge.builder(name, value, AtomicInteger::get)
        .tag("language", language.id())
        .register(registry);
  }

  private static String id(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }
}
