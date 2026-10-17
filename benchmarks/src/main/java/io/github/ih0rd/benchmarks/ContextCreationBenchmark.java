package io.github.ih0rd.benchmarks;

import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
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
import io.github.ih0rd.polyglot.SupportedLanguage;

@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Fork(
    value = 5,
    jvmArgsAppend = {"-Xms512m", "-Xmx2g"})
@Threads(1)
public class ContextCreationBenchmark {
  @Param({"independent", "shared"})
  public String strategy;

  private Engine engine;
  private Context context;

  @Setup(Level.Trial)
  public void setup() {
    if (strategy.equals("shared")) {
      engine = Engine.newBuilder("python").option("engine.WarnInterpreterOnly", "false").build();
    }
  }

  @Benchmark
  public Context create() {
    context =
        PolyglotHelper.newContext(
            SupportedLanguage.PYTHON,
            false,
            b -> {
              b.allowAllAccess(true)
                  .allowExperimentalOptions(true)
                  .option("python.WarnExperimentalFeatures", "false");
              if (engine == null) b.option("engine.WarnInterpreterOnly", "false");
              else b.engine(engine);
            });
    return context;
  }

  @TearDown(Level.Invocation)
  public void closeContext() {
    if (context != null) {
      context.close();
      context = null;
    }
  }

  @TearDown(Level.Trial)
  public void closeEngine() {
    if (engine != null) engine.close();
  }
}
