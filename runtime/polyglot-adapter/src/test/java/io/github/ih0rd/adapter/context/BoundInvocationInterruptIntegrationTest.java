package io.github.ih0rd.adapter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.Convention;

class BoundInvocationInterruptIntegrationTest {
  interface InterruptApi {
    int run();
  }

  @Test
  void guestInterruptionSurvivesPythonInvocationWrapping() throws Exception {
    for (Convention convention : Convention.values()) {
      assertInterrupted(convention, "invocation", false);
    }
  }

  @Test
  void hostInterruptedExceptionRestoresStatusDuringModuleConstructorAndInvocation()
      throws Exception {
    for (Convention convention : Convention.values()) {
      for (String phase : new String[] {"module", "constructor", "invocation"}) {
        if (convention == Convention.BY_METHOD_NAME && phase.equals("constructor")) {
          continue;
        }
        assertInterrupted(convention, phase, true);
      }
    }
  }

  @Test
  void hostInterruptedExceptionDuringResultConversionRestoresStatus() throws Exception {
    for (Convention convention : Convention.values()) {
      assertInterrupted(convention, "conversion", true);
    }
  }

  @Test
  void ordinaryGuestFailureDoesNotSetInterruptStatus() {
    for (Convention convention : Convention.values()) {
      try (Context context = Context.newBuilder("python").build();
          PyExecutor executor =
              PyExecutor.createWithContext(
                  context,
                  new InMemoryScriptSource(
                      Map.of(
                          "python/interrupt_api",
                          "def run():\n    raise ValueError('ordinary failure')\n"
                              + "class InterruptApi:\n    def run(self):\n        return run()\n")))) {
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        assertThatThrownBy(executor.bind(InterruptApi.class, convention)::run)
            .isInstanceOf(InvocationException.class)
            .hasCauseInstanceOf(PolyglotException.class);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
      }
    }
  }

  private static void assertInterrupted(Convention convention, String phase, boolean host)
      throws Exception {
    InterruptGate gate = new InterruptGate();
    HostAccess access =
        HostAccess.newBuilder(HostAccess.ALL)
            .targetTypeMapping(
                Value.class,
                Integer.class,
                value -> phase.equals("conversion") && value.isNumber(),
                value -> {
                  value.getContext().asValue(gate).invokeMember("awaitInterrupt");
                  return value.asInt();
                },
                HostAccess.TargetMappingPrecedence.HIGHEST)
            .build();
    String block = host ? "gate.awaitInterrupt()" : "gate.started()\n    while True:\n        pass";
    String script =
        "def run():\n    "
            + (phase.equals("invocation") ? block : "pass")
            + "\n    return 42\n"
            + "class InterruptApi:\n    def __init__(self):\n        "
            + (phase.equals("constructor") ? "gate.awaitInterrupt()" : "pass")
            + "\n    def run(self):\n        return run()\n"
            + (phase.equals("module") ? "gate.awaitInterrupt()\n" : "");
    try (Context context =
            Context.newBuilder("python")
                .allowAllAccess(true)
                .allowHostAccess(access)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        PyExecutor executor =
            PyExecutor.createWithContext(
                context, new InMemoryScriptSource(Map.of("python/interrupt_api", script)))) {
      context.getBindings("python").putMember("gate", gate);
      InterruptApi client = executor.bind(InterruptApi.class, convention);
      AtomicBoolean interrupted = new AtomicBoolean();
      FutureTask<Throwable> invocation =
          new FutureTask<>(
              () -> {
                try {
                  client.run();
                  return null;
                } catch (InvocationException failure) {
                  interrupted.set(Thread.currentThread().isInterrupted());
                  return failure;
                } finally {
                  // Do not carry the worker's restored status into unrelated cleanup.
                  Thread.interrupted();
                }
              });
      Thread caller = new Thread(invocation, "interrupted-python-client");
      caller.start();
      try {
        assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();
        if (host) {
          caller.interrupt();
        } else {
          context.interrupt(Duration.ofSeconds(10));
        }
        Throwable failure = invocation.get(10, TimeUnit.SECONDS);
        assertThat(failure).isInstanceOf(InvocationException.class);
        Throwable cause = failure;
        while (cause instanceof InvocationException) {
          cause = cause.getCause();
        }
        assertThat(cause).isInstanceOf(PolyglotException.class);
        PolyglotException guest = (PolyglotException) cause;
        if (host) {
          assertThat(guest.isHostException()).isTrue();
          assertThat(guest.asHostException()).isInstanceOf(InterruptedException.class);
        } else {
          assertThat(guest.isInterrupted()).isTrue();
        }
        assertThat(interrupted.get()).as(convention + " / " + phase).isTrue();
        // A different thread can acquire the same monitor after interruption.
        assertThat(executor.evaluate("40 + 2").asInt()).isEqualTo(42);
        executor.clearAllCaches();
        executor.close();
        executor.close();
        assertThat(executor.isClosed()).isTrue();
        assertThatThrownBy(client::run).isInstanceOf(IllegalStateException.class);
      } finally {
        gate.release.countDown();
        caller.join(TimeUnit.SECONDS.toMillis(10));
        assertThat(caller.isAlive()).isFalse();
      }
    }
  }

  public static final class InterruptGate {
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    public void started() {
      entered.countDown();
    }

    public void awaitInterrupt() throws InterruptedException {
      entered.countDown();
      if (!release.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for host interruption");
      }
    }
  }
}
