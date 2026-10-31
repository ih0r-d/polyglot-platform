package io.github.ih0rd.benchmarks;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.client.PolyglotClientFactoryBean;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotMetricsAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotRuntimeMetricsAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Measures warm calls through the actual Spring FactoryBean, toggling only Polyglot metrics. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms512m", "-Xmx2g"})
@Threads(1)
public class SpringObservabilityBenchmark {

  @Param({"false", "true"})
  public boolean metricsEnabled;

  @Param({"true"})
  public boolean runtimeMetrics;

  private AnnotationConfigApplicationContext application;
  private InvocationBenchmark.MethodNameBenchApi client;
  private int input;

  @Setup(Level.Trial)
  public void setup() {
    application = new AnnotationConfigApplicationContext();
    try {
      application
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "benchmark",
                  Map.of(
                      "polyglot.python.enabled", true,
                      "polyglot.core.log-metadata-on-startup", false,
                      "polyglot.metrics.enabled", metricsEnabled)));
      application.register(
          Fixture.class, PolyglotAutoConfiguration.class, PolyglotMetricsAutoConfiguration.class);
      if (runtimeMetrics) application.register(PolyglotRuntimeMetricsAutoConfiguration.class);
      application.refresh();
      client = application.getBean(InvocationBenchmark.MethodNameBenchApi.class);
      if (client.increment(41) != 42) {
        throw new AssertionError("Spring benchmark client returned an unexpected result");
      }
      MeterRegistry registry = application.getBean(MeterRegistry.class);
      int meters = registry.find("polyglot.client.invocations").timers().size();
      if (meters != (metricsEnabled ? 4 : 0)) {
        throw new AssertionError("Unexpected invocation meter count: " + meters);
      }
      int runtimeTimers = registry.find("polyglot.runtime.operations").timers().size();
      int expectedRuntimeTimers = metricsEnabled && runtimeMetrics ? 16 : 0;
      if (runtimeTimers != expectedRuntimeTimers) {
        throw new AssertionError("Unexpected runtime operation meter count: " + runtimeTimers);
      }
    } catch (RuntimeException | Error failure) {
      application.close();
      throw failure;
    }
  }

  @Benchmark
  public int invoke() {
    return client.increment(input++ & 1023);
  }

  @TearDown(Level.Trial)
  public void close() {
    if (application != null) {
      application.close();
    }
  }

  @Configuration(proxyBeanMethods = false)
  public static class Fixture {

    @Bean(destroyMethod = "close")
    PyExecutor executor() {
      return new PyExecutor(
          PolyglotHelper.newContext(SupportedLanguage.PYTHON),
          new InMemoryScriptSource(
              Map.of("python/method_name_bench_api", InvocationBenchmark.SCRIPT)));
    }

    @Bean(destroyMethod = "close")
    MeterRegistry registry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    PolyglotClientFactoryBean<InvocationBenchmark.MethodNameBenchApi> benchmarkClient() {
      return new PolyglotClientFactoryBean<>(
          InvocationBenchmark.MethodNameBenchApi.class.getName());
    }
  }
}
