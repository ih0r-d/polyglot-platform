package io.github.ih0rd.polyglot.annotations.spring.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PolyglotInvocationMetricsTest {

  @Test
  void usesRegistryClockAndSeparatesOutcomesWithoutDuplicateMeters() {
    MockClock clock = new MockClock();
    var registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    try {
      var first = new PolyglotInvocationMetrics(registry);
      var second = new PolyglotInvocationMetrics(registry);
      long start = first.start();
      clock.add(Duration.ofMillis(7));
      first.recordInvocation(SupportedLanguage.PYTHON, false, start);
      start = second.start();
      clock.add(Duration.ofMillis(3));
      second.recordInvocation(SupportedLanguage.PYTHON, true, start);

      assertThat(timer(registry, "success").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(7);
      assertThat(timer(registry, "failure").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(3);
      assertThat(registry.getMeters()).hasSize(4);
      assertThat(registry.getMeters())
          .allSatisfy(meter -> assertThat(meter.getId().getTags()).hasSize(2));
    } finally {
      registry.close();
    }
  }

  @Test
  void concurrentCallsShareThreadSafeTimersWithBoundedCardinality() throws Exception {
    int threads = 8;
    int callsPerThread = 100;
    var registry = new SimpleMeterRegistry();
    try (var workers = Executors.newFixedThreadPool(threads)) {
      var metrics = new PolyglotInvocationMetrics(registry);
      var start = new CyclicBarrier(threads);
      var futures = new ArrayList<Future<?>>();
      for (int thread = 0; thread < threads; thread++) {
        futures.add(
            workers.submit(
                () -> {
                  start.await(10, TimeUnit.SECONDS);
                  for (int call = 0; call < callsPerThread; call++) {
                    metrics.recordInvocation(
                        SupportedLanguage.PYTHON, call % 2 == 0, metrics.start());
                  }
                  return null;
                }));
      }
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
      assertThat(timer(registry, "success").count()).isEqualTo(threads * callsPerThread / 2);
      assertThat(timer(registry, "failure").count()).isEqualTo(threads * callsPerThread / 2);
      assertThat(registry.getMeters()).hasSize(4);
    } finally {
      registry.close();
    }
  }

  private static Timer timer(SimpleMeterRegistry registry, String outcome) {
    return registry
        .get("polyglot.client.invocations")
        .tags("language", "python", "outcome", outcome)
        .timer();
  }
}
