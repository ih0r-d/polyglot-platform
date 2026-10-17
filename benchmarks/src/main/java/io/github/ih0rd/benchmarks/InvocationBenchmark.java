package io.github.ih0rd.benchmarks;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
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

import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms512m", "-Xmx2g"})
@Threads(1)
public class InvocationBenchmark {
  static final String SCRIPT =
      """
      class BenchApi:
          def increment(self, value):
              return value + 1
          def transfer(self, values):
              return [value + 1 for value in values]
      """;

  @PolyglotClient(languages = SupportedLanguage.PYTHON)
  public interface BenchApi {
    int increment(int value);

    int[] transfer(int[] values);
  }

  @Param({"8", "1024"})
  public int size;

  private Context context;
  private PyExecutor executor;
  private BenchApi client;
  private Value target;
  private Value increment;
  private Value transfer;
  private int[] data;
  private int input;

  @Setup(Level.Trial)
  public void setup() {
    context = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
    executor =
        new PyExecutor(context, new InMemoryScriptSource(Map.of("python/bench_api", SCRIPT)));
    executor.validateBinding(BenchApi.class);
    client = executor.bind(BenchApi.class);
    target = context.eval("python", "BenchApi()");
    increment = target.getMember("increment");
    transfer = target.getMember("transfer");
    data = new int[size];
    for (int i = 0; i < size; i++) data[i] = i;
    if (client.increment(41) != 42
        || increment.execute(41).asInt() != 42
        || client.transfer(data)[size - 1] != size
        || transfer.execute((Object) data).as(int[].class)[size - 1] != size) {
      throw new IllegalStateException("Benchmark operations are not equivalent");
    }
  }

  @TearDown(Level.Trial)
  public void close() {
    executor.close();
  }

  @Benchmark
  public int javaCall() {
    return javaIncrement(input++ & 1023);
  }

  private static int javaIncrement(int value) {
    return value + 1;
  }

  @Benchmark
  public int directValue() {
    return increment.execute(input++ & 1023).asInt();
  }

  @Benchmark
  public int directGenericConversion() {
    return increment.execute(input++ & 1023).as(int.class);
  }

  @Benchmark
  public int directMemberLookup() {
    return target.getMember("increment").execute(input++ & 1023).as(int.class);
  }

  @Benchmark
  public int boundClient() {
    return client.increment(input++ & 1023);
  }

  @Benchmark
  public int[] javaTransfer() {
    int[] result = new int[data.length];
    for (int i = 0; i < data.length; i++) result[i] = data[i] + 1;
    return result;
  }

  @Benchmark
  public int[] directTransfer() {
    return transfer.execute((Object) data).as(int[].class);
  }

  @Benchmark
  public int[] boundTransfer() {
    return client.transfer(data);
  }
}
