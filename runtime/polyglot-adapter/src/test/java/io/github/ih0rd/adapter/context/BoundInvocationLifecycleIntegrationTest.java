package io.github.ih0rd.adapter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.Convention;

class BoundInvocationLifecycleIntegrationTest {
  interface LifecycleApi {
    int[] values();

    int ping();

    String nullable();

    void touch(int amount);
  }

  private static final String SCRIPT =
      """
      def values():
          gate.invocation()
          return [1, 2, 3]
      def ping():
          return 42
      def nullable():
          return None
      def touch(amount):
          gate.record(amount)
      class LifecycleApi:
          def values(self):
              return values()
          def ping(self):
              return ping()
          def nullable(self):
              return nullable()
          def touch(self, amount):
              touch(amount)
      """;

  @Test
  void closeWaitsForGuestExecutionForEveryPythonConventionAndOwnership() throws Exception {
    for (Convention convention : Convention.values()) {
      for (ContextOwnership ownership : ContextOwnership.values()) {
        assertCloseWaits(Pause.INVOCATION, convention, ownership);
      }
    }
  }

  @Test
  void closeWaitsForResultConversionForEveryPythonConventionAndOwnership() throws Exception {
    for (Convention convention : Convention.values()) {
      for (ContextOwnership ownership : ContextOwnership.values()) {
        assertCloseWaits(Pause.CONVERSION, convention, ownership);
      }
    }
  }

  @Test
  void customMappingCanReenterTheExecutorAndRepeatedCallsKeepNullAndVoidSemantics() {
    Gate gate = new Gate(null);
    AtomicReference<Runnable> duringConversion = new AtomicReference<>(() -> {});
    try (Context context = newContext(gate, duringConversion);
        PyExecutor executor = newExecutor(context, gate, ContextOwnership.OWNED_BY_EXECUTOR)) {
      for (Convention convention : Convention.values()) {
        LifecycleApi client = executor.bind(LifecycleApi.class, convention);
        duringConversion.set(() -> assertThat(client.ping()).isEqualTo(42));
        for (int i = 0; i < 20; i++) {
          assertThat(client.values()).containsExactly(1, 2, 3);
          assertThat(client.nullable()).isNull();
          client.touch(2);
        }
      }
      assertThat(gate.recorded.get()).isEqualTo(120);
    }
  }

  @Test
  void failedConversionReleasesTheMonitorAndPreservesItsCause() throws Exception {
    Gate gate = new Gate(null);
    IllegalArgumentException failure = new IllegalArgumentException("conversion failed");
    AtomicReference<Runnable> duringConversion =
        new AtomicReference<>(
            () -> {
              throw failure;
            });
    try (Context context = newContext(gate, duringConversion);
        PyExecutor executor = newExecutor(context, gate, ContextOwnership.OWNED_BY_EXECUTOR)) {
      LifecycleApi client = executor.bind(LifecycleApi.class);
      assertThatThrownBy(client::values)
          .isInstanceOf(InvocationException.class)
          .cause()
          .isInstanceOfSatisfying(
              PolyglotException.class,
              guest -> {
                assertThat(guest.isHostException()).isTrue();
                assertThat(guest.asHostException()).isSameAs(failure);
              });
      duringConversion.set(() -> {});
      FutureTask<int[]> next = new FutureTask<>(client::values);
      Thread thread = new Thread(next, "after-conversion-failure");
      thread.start();
      try {
        assertThat(next.get(10, TimeUnit.SECONDS)).containsExactly(1, 2, 3);
      } finally {
        join(thread);
      }
    }
  }

  @Test
  void anotherBoundCallWaitsForCustomConversion() throws Exception {
    Gate gate = new Gate(Pause.CONVERSION);
    try (Context context = newContext(gate, new AtomicReference<>(() -> {}));
        PyExecutor executor = newExecutor(context, gate, ContextOwnership.OWNED_BY_EXECUTOR)) {
      LifecycleApi client = executor.bind(LifecycleApi.class);
      FutureTask<int[]> conversion = new FutureTask<>(client::values);
      FutureTask<Integer> next = new FutureTask<>(client::ping);
      Thread caller = new Thread(conversion, "converting-client");
      Thread contender = new Thread(next, "other-client-call");
      caller.start();
      try {
        assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();
        contender.start();
        awaitBlocked(contender, caller);
        gate.release.countDown();
        assertThat(conversion.get(10, TimeUnit.SECONDS)).containsExactly(1, 2, 3);
        assertThat(next.get(10, TimeUnit.SECONDS)).isEqualTo(42);
      } finally {
        gate.release.countDown();
        join(caller);
        join(contender);
      }
    }
  }

  private static void assertCloseWaits(
      Pause pause, Convention convention, ContextOwnership ownership) throws Exception {
    Gate gate = new Gate(pause);
    AtomicReference<Runnable> duringConversion = new AtomicReference<>(() -> {});
    try (Context context = newContext(gate, duringConversion);
        PyExecutor executor = newExecutor(context, gate, ownership)) {
      LifecycleApi client = executor.bind(LifecycleApi.class, convention);
      FutureTask<int[]> invocation = new FutureTask<>(client::values);
      CountDownLatch closing = new CountDownLatch(1);
      FutureTask<Void> close =
          new FutureTask<>(
              () -> {
                closing.countDown();
                executor.close();
                return null;
              });
      Thread caller = new Thread(invocation, "bound-python-call");
      Thread closer = new Thread(close, "executor-close");
      caller.start();
      try {
        assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();
        closer.start();
        assertThat(closing.await(10, TimeUnit.SECONDS)).isTrue();
        awaitBlocked(closer, caller);
        assertThat(close.isDone()).isFalse();
        assertThat(executor.isClosed()).isFalse();
        gate.release.countDown();
        assertThat(invocation.get(10, TimeUnit.SECONDS)).containsExactly(1, 2, 3);
        close.get(10, TimeUnit.SECONDS);
        assertThat(executor.isClosed()).isTrue();
        assertThatThrownBy(client::values).isInstanceOf(IllegalStateException.class);
        if (ownership == ContextOwnership.BORROWED) {
          assertThat(context.eval("python", "40 + 2").asInt()).isEqualTo(42);
        } else {
          assertThatThrownBy(() -> context.eval("python", "1"))
              .isInstanceOf(IllegalStateException.class);
        }
      } finally {
        gate.release.countDown();
        join(caller);
        join(closer);
      }
    }
  }

  private static Context newContext(Gate gate, AtomicReference<Runnable> duringConversion) {
    HostAccess access =
        HostAccess.newBuilder(HostAccess.ALL)
            .targetTypeMapping(
                Value.class,
                int[].class,
                Value::hasArrayElements,
                value -> {
                  gate.pause(Pause.CONVERSION);
                  duringConversion.get().run();
                  return new int[] {
                    value.getArrayElement(0).asInt(),
                    value.getArrayElement(1).asInt(),
                    value.getArrayElement(2).asInt()
                  };
                },
                HostAccess.TargetMappingPrecedence.HIGHEST)
            .build();
    return Context.newBuilder("python")
        .allowAllAccess(true)
        .allowHostAccess(access)
        .option("engine.WarnInterpreterOnly", "false")
        .build();
  }

  private static PyExecutor newExecutor(Context context, Gate gate, ContextOwnership ownership) {
    context.getBindings("python").putMember("gate", gate);
    return PyExecutor.createWithContext(
        context, new InMemoryScriptSource(Map.of("python/lifecycle_api", SCRIPT)), ownership);
  }

  private static void awaitBlocked(Thread contender, Thread owner) {
    ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      ThreadInfo info = threads.getThreadInfo(contender.threadId());
      if (info != null
          && info.getThreadState() == Thread.State.BLOCKED
          && info.getLockOwnerId() == owner.threadId()) {
        return;
      }
      if (!contender.isAlive()) {
        fail("close completed while the bound invocation was still active");
      }
      Thread.yield();
    }
    fail("close did not wait for the bound invocation monitor");
  }

  private static void join(Thread thread) throws InterruptedException {
    thread.join(TimeUnit.SECONDS.toMillis(10));
    assertThat(thread.isAlive()).as(thread.getName() + " must terminate").isFalse();
  }

  private enum Pause {
    INVOCATION,
    CONVERSION
  }

  public static final class Gate {
    private final Pause pause;
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger recorded = new AtomicInteger();

    private Gate(Pause pause) {
      this.pause = pause;
    }

    public void invocation() {
      pause(Pause.INVOCATION);
    }

    public void record(int amount) {
      recorded.addAndGet(amount);
    }

    private void pause(Pause phase) {
      if (pause == phase) {
        entered.countDown();
        try {
          if (!release.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting to release " + phase);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError("Interrupted while waiting to release " + phase, e);
        }
      }
    }
  }
}
