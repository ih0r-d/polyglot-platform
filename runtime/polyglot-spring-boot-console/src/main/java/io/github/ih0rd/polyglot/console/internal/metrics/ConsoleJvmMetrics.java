package io.github.ih0rd.polyglot.console.internal.metrics;

import java.util.HashSet;

import org.jspecify.annotations.Nullable;

import io.github.ih0rd.polyglot.console.internal.dto.JvmSnapshot;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Reads standard JVM meters without registering binders or exposing arbitrary metadata. */
final class ConsoleJvmMetrics {
  private ConsoleJvmMetrics() {}

  static JvmSnapshot snapshot(@Nullable MeterRegistry registry) {
    Gauge info = uniqueGauge(registry, "jvm.info");
    return new JvmSnapshot(
        tag(info, "version"),
        tag(info, "vendor"),
        tag(info, "runtime"),
        reading(uniqueGauge(registry, "process.uptime")),
        heap(registry, "jvm.memory.used"),
        heap(registry, "jvm.memory.committed"),
        reading(uniqueGauge(registry, "jvm.threads.live")));
  }

  private static @Nullable Gauge uniqueGauge(@Nullable MeterRegistry registry, String name) {
    if (registry == null) return null;
    var gauges = registry.find(name).gauges();
    return gauges.size() == 1 ? gauges.iterator().next() : null;
  }

  private static @Nullable String tag(@Nullable Gauge gauge, String name) {
    return gauge == null ? null : gauge.getId().getTag(name);
  }

  private static @Nullable Double reading(@Nullable Gauge gauge) {
    if (gauge == null) return null;
    double value = gauge.value();
    return Double.isFinite(value) && value >= 0 ? value : null;
  }

  private static @Nullable Double heap(@Nullable MeterRegistry registry, String name) {
    if (registry == null) return null;
    var gauges = registry.find(name).tag("area", "heap").gauges();
    if (gauges.isEmpty()) return null;
    var pools = new HashSet<String>();
    double total = 0;
    for (Gauge gauge : gauges) {
      String pool = gauge.getId().getTag("id");
      Double value = reading(gauge);
      if (pool == null || !pools.add(pool) || value == null) return null;
      total += value;
    }
    return Double.isFinite(total) ? total : null;
  }
}
