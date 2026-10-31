package io.github.ih0rd.polyglot.console.internal.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;
import io.github.ih0rd.polyglot.console.internal.dto.ConsoleSettingSnapshot;
import io.github.ih0rd.polyglot.console.internal.dto.ConsoleSnapshot;
import io.github.ih0rd.polyglot.console.internal.dto.InvocationSnapshot;
import io.github.ih0rd.polyglot.console.internal.dto.RuntimeMetricSnapshot;
import io.github.ih0rd.polyglot.console.properties.ConsoleProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

/** Reads existing lifecycle and meter snapshots without creating contexts or registering meters. */
public final class ConsoleSnapshotService {
  private final String instanceId = UUID.randomUUID().toString();
  private final ConsoleProperties settings;
  private final ObjectProvider<MeterRegistry> registries;
  private final ObjectProvider<HealthIndicator> health;
  private final ObjectProvider<PolyglotProperties> properties;

  /**
   * Creates an in-process collector without resolving optional runtime providers.
   *
   * @param settings console settings
   * @param registries application registries
   * @param health qualified polyglot lifecycle indicator
   * @param properties optional effective polyglot configuration
   */
  public ConsoleSnapshotService(
      ConsoleProperties settings,
      ObjectProvider<MeterRegistry> registries,
      ObjectProvider<HealthIndicator> health,
      ObjectProvider<PolyglotProperties> properties) {
    this.settings = settings;
    this.registries = registries;
    this.health = health;
    this.properties = properties;
  }

  /**
   * Reads current lifecycle and meter measurements without invoking guest functions.
   *
   * @return detached, allowlisted browser snapshot
   */
  public ConsoleSnapshot snapshot() {
    MeterRegistry registry = registries.getIfUnique();
    HealthIndicator indicator = health.getIfAvailable();
    Health state = indicator == null ? Health.unknown().build() : indicator.health();
    if (state == null) {
      state = Health.unknown().build();
    }
    var available = state.getDetails().get("pythonEnabled");
    PolyglotProperties configured = properties.getIfAvailable();
    List<SupportedLanguage> languages = enabledLanguages(configured);
    return new ConsoleSnapshot(
        instanceId,
        settings.refreshInterval().toMillis(),
        state.getStatus().getCode(),
        available instanceof Boolean value ? value : null,
        invocations(registry, languages),
        runtime(registry, languages),
        configuration(configured),
        ConsoleJvmMetrics.snapshot(registry),
        ConsoleRuntimeMetrics.operations(registry, languages),
        ConsoleRuntimeMetrics.caches(registry, languages));
  }

  private static List<SupportedLanguage> enabledLanguages(@Nullable PolyglotProperties configured) {
    if (configured == null || !configured.core().enabled()) {
      return List.of();
    }
    List<SupportedLanguage> languages = new ArrayList<>();
    if (configured.python().enabled()) {
      languages.add(SupportedLanguage.PYTHON);
    }
    if (configured.js().enabled()) {
      languages.add(SupportedLanguage.JS);
    }
    return languages;
  }

  private static List<InvocationSnapshot> invocations(
      @Nullable MeterRegistry registry, List<SupportedLanguage> languages) {
    if (registry == null) return List.of();
    List<InvocationSnapshot> results = new ArrayList<>();
    for (SupportedLanguage language : languages) {
      for (String outcome : List.of("success", "failure")) {
        var timers =
            registry
                .find("polyglot.client.invocations")
                .tags("language", language.id(), "outcome", outcome)
                .timers();
        if (timers.isEmpty()) continue;
        long count = 0;
        double total = 0;
        double maximum = 0;
        for (var timer : timers) {
          HistogramSnapshot snapshot = timer.takeSnapshot();
          count += snapshot.count();
          total += snapshot.total(TimeUnit.SECONDS);
          maximum = Math.max(maximum, snapshot.max(TimeUnit.SECONDS));
        }
        results.add(new InvocationSnapshot(language.id(), outcome, count, total, maximum));
      }
    }
    return results;
  }

  private static List<RuntimeMetricSnapshot> runtime(
      @Nullable MeterRegistry registry, List<SupportedLanguage> languages) {
    List<RuntimeMetricSnapshot> values = new ArrayList<>();
    values.add(value(registry, "polyglot.executor.configured.count", "", "count"));
    values.add(value(registry, "polyglot.executor.available.count", "", "count"));
    values.add(value(registry, "polyglot.startup.duration", "", "milliseconds"));
    for (SupportedLanguage language : languages) {
      values.add(value(registry, "polyglot.executor.enabled", language.id(), "boolean"));
      values.add(value(registry, "polyglot.executor.source.cache.size", language.id(), "count"));
      values.add(value(registry, "polyglot.runtime.invocations.active", language.id(), "count"));
      values.add(value(registry, "polyglot.runtime.invocations.waiting", language.id(), "count"));
      values.add(
          new RuntimeMetricSnapshot(
              "polyglot.runtime.executors.closed",
              language.id(),
              "count",
              registry == null
                  ? null
                  : ConsoleRuntimeMetrics.counter(
                      registry, "polyglot.runtime.executors.closed", language.id())));
      if (language == SupportedLanguage.PYTHON) {
        values.add(value(registry, "polyglot.runtime.reloads.active", language.id(), "count"));
      }
    }
    if (languages.contains(SupportedLanguage.PYTHON)) {
      values.add(
          value(
              registry,
              "polyglot.python.bound.interfaces.count",
              SupportedLanguage.PYTHON.id(),
              "count"));
      values.add(
          value(
              registry,
              "polyglot.python.instance.cache.size",
              SupportedLanguage.PYTHON.id(),
              "count"));
    }
    if (languages.contains(SupportedLanguage.JS)) {
      values.add(
          value(
              registry, "polyglot.js.loaded.interfaces.count", SupportedLanguage.JS.id(), "count"));
    }
    return values;
  }

  private static RuntimeMetricSnapshot value(
      @Nullable MeterRegistry registry, String name, String language, String unit) {
    Double result = null;
    if (registry != null) {
      var search = registry.find(name);
      if (!language.isEmpty()) search.tag("language", language);
      var gauges = search.gauges();
      if (gauges.size() == 1) {
        double observed = gauges.iterator().next().value();
        if (Double.isFinite(observed) && observed >= 0) result = observed;
      }
    }
    return new RuntimeMetricSnapshot(name, language, unit, result);
  }

  private static List<ConsoleSettingSnapshot> configuration(
      @Nullable PolyglotProperties properties) {
    if (properties == null) return List.of();
    return List.of(
        new ConsoleSettingSnapshot("Core enabled", properties.core().enabled()),
        new ConsoleSettingSnapshot("Fail-fast", properties.core().failFast()),
        new ConsoleSettingSnapshot("Python enabled", properties.python().enabled()),
        new ConsoleSettingSnapshot("Safe defaults", properties.python().safeDefaults()),
        new ConsoleSettingSnapshot("Warmup on startup", properties.python().warmupOnStartup()),
        new ConsoleSettingSnapshot("JavaScript enabled", properties.js().enabled()),
        new ConsoleSettingSnapshot("Invocation metrics enabled", properties.metrics().enabled()));
  }
}
