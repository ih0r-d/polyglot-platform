package io.github.ih0rd.polyglot.console.internal.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.observability.InvalidationReason;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ConsoleRuntimeMetricsTest {
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final List<SupportedLanguage> python = List.of(SupportedLanguage.PYTHON);

  @AfterEach
  void close() {
    registry.close();
  }

  @Test
  void readsOnlyKnownTimerScopesAndDoesNotRegisterAnything() {
    registry
        .timer(
            "polyglot.runtime.operations",
            "language",
            "python",
            "operation",
            "reload",
            "outcome",
            "success")
        .record(20, TimeUnit.MILLISECONDS);
    registry.timer(
        "polyglot.runtime.operations",
        "language",
        "python",
        "operation",
        "/secret.py",
        "outcome",
        "success");
    registry.timer(
        "polyglot.runtime.operations",
        "language",
        "python",
        "operation",
        "reload",
        "outcome",
        "private exception");
    registry.timer("unrelated.secret.metric");
    int before = registry.getMeters().size();
    var snapshot = ConsoleRuntimeMetrics.operations(registry, python);
    assertThat(snapshot).hasSize(1);
    assertThat(snapshot.getFirst().operation()).isEqualTo("reload");
    assertThat(snapshot.getFirst().count()).isEqualTo(1);
    assertThat(snapshot.getFirst().totalSeconds()).isEqualTo(0.02);
    assertThat(registry.getMeters()).hasSize(before);
    assertThat(snapshot.toString()).doesNotContain("secret", "private exception");
  }

  @Test
  void missingCacheCountersAndUnsupportedSizesStayUnknown() {
    assertThat(ConsoleRuntimeMetrics.caches(registry, python)).isEmpty();
    assertThat(ConsoleRuntimeMetrics.operations(null, python)).isEmpty();
    registry
        .counter(
            "polyglot.runtime.cache.access",
            "language",
            "python",
            "cache",
            "module",
            "result",
            "hit")
        .increment(3);
    var partial = ConsoleRuntimeMetrics.caches(registry, python).getFirst();
    assertThat(partial.hits()).isEqualTo(3);
    assertThat(partial.misses()).isNull();
    assertThat(partial.invalidatedEntries()).isNull();
    assertThat(partial.size()).isNull();
  }

  @Test
  void countersAndExistingSizeGaugeUseOneMatchingCacheScope() {
    registry
        .counter(
            "polyglot.runtime.cache.access",
            "language",
            "python",
            "cache",
            "source",
            "result",
            "hit")
        .increment(4);
    registry
        .counter(
            "polyglot.runtime.cache.access",
            "language",
            "python",
            "cache",
            "source",
            "result",
            "miss")
        .increment(2);
    for (InvalidationReason reason : InvalidationReason.values()) {
      registry
          .counter(
              "polyglot.runtime.cache.invalidations",
              "language",
              "python",
              "cache",
              "source",
              "reason",
              reason.name().toLowerCase(java.util.Locale.ROOT))
          .increment();
    }
    Gauge.builder("polyglot.executor.source.cache.size", () -> 2)
        .tag("language", "python")
        .register(registry);
    var snapshot = ConsoleRuntimeMetrics.caches(registry, python);
    assertThat(snapshot).hasSize(1);
    assertThat(snapshot.getFirst().hits()).isEqualTo(4);
    assertThat(snapshot.getFirst().misses()).isEqualTo(2);
    assertThat(snapshot.getFirst().invalidatedEntries()).isEqualTo(4);
    assertThat(snapshot.getFirst().reloadInvalidatedEntries()).isEqualTo(1);
    assertThat(snapshot.getFirst().size()).isEqualTo(2);
    assertThat(ConsoleRuntimeMetrics.caches(registry, List.of(SupportedLanguage.JS))).isEmpty();
  }
}
