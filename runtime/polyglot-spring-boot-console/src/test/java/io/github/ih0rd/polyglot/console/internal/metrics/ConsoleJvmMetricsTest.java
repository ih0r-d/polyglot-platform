package io.github.ih0rd.polyglot.console.internal.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ConsoleJvmMetricsTest {
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @AfterEach
  void closeRegistry() {
    registry.close();
  }

  @Test
  void readsRealStandardBindersWithoutRegisteringAdditionalMeters() {
    new JvmInfoMetrics().bindTo(registry);
    new JvmMemoryMetrics().bindTo(registry);
    new JvmThreadMetrics().bindTo(registry);
    new UptimeMetrics().bindTo(registry);
    int meters = registry.getMeters().size();

    var snapshot = ConsoleJvmMetrics.snapshot(registry);

    assertThat(snapshot.javaVersion()).isEqualTo(System.getProperty("java.runtime.version"));
    assertThat(snapshot.vendor()).isEqualTo(System.getProperty("java.vm.vendor"));
    assertThat(snapshot.runtime()).isEqualTo(System.getProperty("java.runtime.name"));
    assertThat(snapshot.uptimeSeconds()).isPositive();
    assertThat(snapshot.heapUsedBytes()).isNotNegative();
    assertThat(snapshot.heapCommittedBytes()).isPositive();
    assertThat(snapshot.liveThreads()).isPositive();
    assertThat(registry.getMeters()).hasSize(meters);
  }

  @Test
  void sumsOnlyHeapPoolsAndReadsOnlyAllowlistedInfoTags() {
    Gauge.builder("jvm.info", () -> 1)
        .tags(
            "version",
            "25-test",
            "vendor",
            "Test vendor",
            "runtime",
            "Test runtime",
            "secret",
            "sensitive-value")
        .register(registry);
    memory(registry, "jvm.memory.used", "first", 32);
    memory(registry, "jvm.memory.used", "second", 64);
    memory(registry, "jvm.memory.committed", "first", 128);
    memory(registry, "jvm.memory.committed", "second", 128);
    Gauge.builder("jvm.memory.used", () -> 999)
        .tags("area", "nonheap", "id", "metaspace")
        .register(registry);
    registry.gauge("process.uptime", 0);
    registry.gauge("jvm.threads.live", 42);

    var snapshot = ConsoleJvmMetrics.snapshot(registry);

    assertThat(snapshot.javaVersion()).isEqualTo("25-test");
    assertThat(snapshot.vendor()).isEqualTo("Test vendor");
    assertThat(snapshot.runtime()).isEqualTo("Test runtime");
    assertThat(snapshot.heapUsedBytes()).isEqualTo(96);
    assertThat(snapshot.heapCommittedBytes()).isEqualTo(256);
    assertThat(snapshot.uptimeSeconds()).isZero();
    assertThat(snapshot.liveThreads()).isEqualTo(42);
    assertThat(snapshot.toString()).doesNotContain("secret", "sensitive-value", "metaspace");
  }

  @Test
  void missingMetersRemainUnavailableRatherThanZero() {
    var missing = ConsoleJvmMetrics.snapshot(null);
    assertThat(ConsoleJvmMetrics.snapshot(registry)).isEqualTo(missing);
    assertThat(missing.javaVersion()).isNull();
    assertThat(missing.vendor()).isNull();
    assertThat(missing.runtime()).isNull();
    assertThat(missing.uptimeSeconds()).isNull();
    assertThat(missing.heapUsedBytes()).isNull();
    assertThat(missing.heapCommittedBytes()).isNull();
    assertThat(missing.liveThreads()).isNull();
    registry.gauge("jvm.info", 1);
    assertThat(ConsoleJvmMetrics.snapshot(registry)).isEqualTo(missing);
  }

  @Test
  void invalidValuesDoNotProducePartialHeapTotals() {
    for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
      registry.clear();
      memory(registry, "jvm.memory.used", "valid", 64);
      memory(registry, "jvm.memory.used", "invalid", invalid);
      registry.gauge("process.uptime", invalid);
      registry.gauge("jvm.threads.live", invalid);
      var snapshot = ConsoleJvmMetrics.snapshot(registry);
      assertThat(snapshot.heapUsedBytes()).isNull();
      assertThat(snapshot.uptimeSeconds()).isNull();
      assertThat(snapshot.liveThreads()).isNull();
    }
  }

  @Test
  void ambiguousSingleValueMetersAreNotSelectedArbitrarily() {
    for (String name : new String[] {"jvm.info", "process.uptime", "jvm.threads.live"}) {
      Gauge.builder(name, () -> 1).tag("instance", "first").register(registry);
      Gauge.builder(name, () -> 2).tag("instance", "second").register(registry);
    }
    var snapshot = ConsoleJvmMetrics.snapshot(registry);
    assertThat(snapshot.javaVersion()).isNull();
    assertThat(snapshot.uptimeSeconds()).isNull();
    assertThat(snapshot.liveThreads()).isNull();
  }

  @Test
  void duplicateOrMissingPoolIdsAreNotSummed() {
    Gauge.builder("jvm.memory.used", () -> 1).tag("area", "heap").register(registry);
    assertThat(ConsoleJvmMetrics.snapshot(registry).heapUsedBytes()).isNull();
    for (String instance : new String[] {"first", "second"}) {
      Gauge.builder("jvm.memory.committed", () -> 1)
          .tags("area", "heap", "id", "duplicate", "instance", instance)
          .register(registry);
    }
    assertThat(ConsoleJvmMetrics.snapshot(registry).heapCommittedBytes()).isNull();
  }

  @Test
  void overflowingHeapTotalsRemainUnavailable() {
    memory(registry, "jvm.memory.used", "first", Double.MAX_VALUE);
    memory(registry, "jvm.memory.used", "second", Double.MAX_VALUE);
    assertThat(ConsoleJvmMetrics.snapshot(registry).heapUsedBytes()).isNull();
  }

  private static void memory(SimpleMeterRegistry registry, String name, String pool, double bytes) {
    Gauge.builder(name, () -> bytes).tags("area", "heap", "id", pool).register(registry);
  }
}
