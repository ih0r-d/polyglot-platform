package io.github.ih0rd.polyglot.console.internal.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

import io.github.ih0rd.adapter.observability.InvalidationReason;
import io.github.ih0rd.adapter.observability.RuntimeCache;
import io.github.ih0rd.adapter.observability.RuntimeOperation;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.console.internal.dto.CacheSnapshot;
import io.github.ih0rd.polyglot.console.internal.dto.OperationSnapshot;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Reads only the fixed runtime meter catalog; never resolves an executor or evaluates guest code.
 */
final class ConsoleRuntimeMetrics {
  private ConsoleRuntimeMetrics() {}

  static List<OperationSnapshot> operations(
      @Nullable MeterRegistry registry, List<SupportedLanguage> languages) {
    if (registry == null) return List.of();
    List<OperationSnapshot> values = new ArrayList<>();
    for (SupportedLanguage language : languages) {
      for (RuntimeOperation operation : RuntimeOperation.values()) {
        for (String outcome : List.of("success", "failure")) {
          var timers =
              registry
                  .find("polyglot.runtime.operations")
                  .tags("language", language.id(), "operation", id(operation), "outcome", outcome)
                  .timers();
          if (timers.isEmpty()) continue;
          long count = 0;
          double seconds = 0;
          double maximum = 0;
          for (var timer : timers) {
            var observed = timer.takeSnapshot();
            count += observed.count();
            seconds += observed.total(TimeUnit.SECONDS);
            maximum = Math.max(maximum, observed.max(TimeUnit.SECONDS));
          }
          if (Double.isFinite(seconds) && Double.isFinite(maximum)) {
            values.add(
                new OperationSnapshot(
                    language.id(), id(operation), outcome, count, seconds, maximum));
          }
        }
      }
    }
    return values;
  }

  static List<CacheSnapshot> caches(
      @Nullable MeterRegistry registry, List<SupportedLanguage> languages) {
    if (registry == null) return List.of();
    List<CacheSnapshot> values = new ArrayList<>();
    for (SupportedLanguage language : languages) {
      for (RuntimeCache cache : RuntimeCache.values()) {
        Double hits =
            counter(
                registry,
                "polyglot.runtime.cache.access",
                language.id(),
                "cache",
                id(cache),
                "result",
                "hit");
        Double misses =
            counter(
                registry,
                "polyglot.runtime.cache.access",
                language.id(),
                "cache",
                id(cache),
                "result",
                "miss");
        if (hits == null && misses == null) continue;
        values.add(
            new CacheSnapshot(
                language.id(),
                id(cache),
                hits,
                misses,
                invalidations(registry, language, cache),
                counter(
                    registry,
                    "polyglot.runtime.cache.invalidations",
                    language.id(),
                    "cache",
                    id(cache),
                    "reason",
                    "reload"),
                size(registry, language, cache)));
      }
    }
    return values;
  }

  private static @Nullable Double invalidations(
      MeterRegistry registry, SupportedLanguage language, RuntimeCache cache) {
    double total = 0;
    for (InvalidationReason reason : InvalidationReason.values()) {
      Double value =
          counter(
              registry,
              "polyglot.runtime.cache.invalidations",
              language.id(),
              "cache",
              id(cache),
              "reason",
              id(reason));
      if (value == null) return null;
      total += value;
    }
    return Double.isFinite(total) ? total : null;
  }

  private static @Nullable Double size(
      MeterRegistry registry, SupportedLanguage language, RuntimeCache cache) {
    String name =
        switch (cache) {
          case SOURCE -> "polyglot.executor.source.cache.size";
          case INSTANCE -> "polyglot.python.instance.cache.size";
          case MODULE -> null;
        };
    if (name == null) return null;
    var gauges = registry.find(name).tag("language", language.id()).gauges();
    if (gauges.size() != 1) return null;
    double value = gauges.iterator().next().value();
    return Double.isFinite(value) && value >= 0 ? value : null;
  }

  static @Nullable Double counter(
      MeterRegistry registry, String name, String language, String... tags) {
    var counters = registry.find(name).tag("language", language).tags(tags).counters();
    if (counters.size() != 1) return null;
    double value = counters.iterator().next().count();
    return Double.isFinite(value) && value >= 0 ? value : null;
  }

  private static String id(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }
}
