package io.github.ih0rd.benchmarks;

import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.polyglot.SupportedLanguage;

@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, batchSize = 1)
@Fork(
    value = 5,
    jvmArgsAppend = {"-Xms512m", "-Xmx2g"})
@Threads(1)
public class StartupBenchmark {
  private Context context;

  @Benchmark
  public Context initializedContext() {
    context = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
    return context;
  }

  @TearDown(Level.Invocation)
  public void close() {
    if (context != null) context.close();
  }

  @State(Scope.Thread)
  public static class Initialized {
    Context context;

    @Setup(Level.Trial)
    public void setup() {
      context = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
    }

    @TearDown(Level.Trial)
    public void close() {
      context.close();
    }
  }

  @Benchmark
  public Object firstStdlibImport(Initialized state) {
    return state.context.eval("python", "import json");
  }

  @Benchmark
  public Object firstModuleEvaluation(Initialized state) {
    return state.context.eval("python", InvocationBenchmark.SCRIPT);
  }
}
