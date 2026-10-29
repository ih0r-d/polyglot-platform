package io.github.ih0rd.benchmarks;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.Convention;

/** Separates cached calls before/after reload from the explicit reload operation. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms512m", "-Xmx2g"})
@Threads(1)
public class ReloadBenchmark {
  private static final String SCRIPT = "def increment(value): return value + 1\n";

  public interface ReloadApi {
    int increment(int value);
  }

  @Param({"false", "true"})
  public boolean reloaded;

  private PyExecutor executor;
  private ReloadApi client;
  private int input;

  @Setup
  public void setup() {
    executor =
        new PyExecutor(
            Context.newBuilder("python").option("engine.WarnInterpreterOnly", "false").build(),
            new InMemoryScriptSource(Map.of("python/reload_api", SCRIPT)));
    client = executor.bind(ReloadApi.class, Convention.BY_METHOD_NAME);
    if (client.increment(41) != 42) throw new IllegalStateException("Invalid initial result");
    if (reloaded) executor.reloadContract(ReloadApi.class, Convention.BY_METHOD_NAME);
    if (client.increment(41) != 42) throw new IllegalStateException("Invalid reloaded result");
  }

  @TearDown
  public void close() {
    executor.close();
  }

  @Benchmark
  public int cachedInvocation() {
    return client.increment(input++ & 1023);
  }

  @Benchmark
  public void explicitReload() {
    executor.reloadContract(ReloadApi.class, Convention.BY_METHOD_NAME);
  }
}
