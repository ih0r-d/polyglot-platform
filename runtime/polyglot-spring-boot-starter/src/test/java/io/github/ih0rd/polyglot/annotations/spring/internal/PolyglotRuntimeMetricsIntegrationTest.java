package io.github.ih0rd.polyglot.annotations.spring.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.context.ContextOwnership;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PolyglotRuntimeMetricsIntegrationTest {
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @AfterEach
  void closeRegistry() {
    registry.close();
  }

  interface RuntimeApi {
    int value();
  }

  @Test
  void actualCacheReuseReloadRollbackAndFailuresHaveBoundedMeters() {
    Map<String, String> scripts =
        new ConcurrentHashMap<>(Map.of("python/runtime_api", "def value(): return 42\n"));
    try (Context context = context();
        PyExecutor executor = executor(context, scripts, registry)) {
      RuntimeApi client = executor.bind(RuntimeApi.class, Convention.BY_METHOD_NAME);
      assertThat(client.value()).isEqualTo(42);
      assertThat(client.value()).isEqualTo(42);
      assertThat(accesses(registry, "module", "miss")).isEqualTo(1);
      assertThat(accesses(registry, "module", "hit")).isEqualTo(1);
      assertThat(accesses(registry, "source", "miss")).isEqualTo(1);
      assertThat(operations(registry, "source_load", "success")).isEqualTo(1);
      assertThat(operations(registry, "script_evaluation", "success")).isEqualTo(1);
      scripts.put("python/runtime_api", "def value(): return 43\n");
      executor.reloadContract(RuntimeApi.class, Convention.BY_METHOD_NAME);
      assertThat(client.value()).isEqualTo(43);
      scripts.put("python/runtime_api", "unrelated = 1\n");
      assertThatThrownBy(() -> executor.reloadContract(RuntimeApi.class, Convention.BY_METHOD_NAME))
          .isInstanceOf(BindingException.class);
      assertThat(client.value()).isEqualTo(43);
      assertThat(operations(registry, "reload", "success")).isEqualTo(1);
      assertThat(operations(registry, "reload", "failure")).isEqualTo(1);
      assertThat(
              registry
                  .get("polyglot.runtime.cache.invalidations")
                  .tags("language", "python", "cache", "module", "reason", "reload")
                  .counter()
                  .count())
          .isEqualTo(2);
      executor.clearAllCaches();
      assertThat(
              registry
                  .get("polyglot.runtime.cache.invalidations")
                  .tags("language", "python", "cache", "source", "reason", "clear")
                  .counter()
                  .count())
          .isEqualTo(1);
      assertThat(gauge(registry, "reloads.active")).isZero();
      assertThat(gauge(registry, "invocations.active")).isZero();
      assertThat(gauge(registry, "invocations.waiting")).isZero();
      assertThat(registry.getMeters())
          .allSatisfy(
              meter -> {
                assertThat(meter.getId().getTags())
                    .allSatisfy(
                        tag ->
                            assertThat(tag.getKey())
                                .isIn(
                                    "language",
                                    "operation",
                                    "outcome",
                                    "cache",
                                    "result",
                                    "reason"));
                assertThat(meter.getId().getTags())
                    .noneSatisfy(tag -> assertThat(tag.getValue()).contains("runtime_api"));
              });
    }
  }

  @Test
  void admissionGaugesBalanceDuringContentionAndInterruptedExecution() throws Exception {
    Gate gate = new Gate();
    Map<String, String> scripts =
        Map.of("python/runtime_api", "def value():\n    gate.block()\n    return 42\n");
    try (Context context = context();
        PyExecutor executor = executor(context, scripts, registry)) {
      context.getBindings("python").putMember("gate", gate);
      RuntimeApi client = executor.bind(RuntimeApi.class, Convention.BY_METHOD_NAME);
      AtomicBoolean interrupted = new AtomicBoolean();
      FutureTask<Void> first =
          new FutureTask<>(
              () -> {
                try {
                  assertThatThrownBy(client::value).isInstanceOf(InvocationException.class);
                  interrupted.set(Thread.currentThread().isInterrupted());
                  return null;
                } finally {
                  Thread.interrupted();
                }
              });
      FutureTask<Integer> second = new FutureTask<>(client::value);
      Thread owner = new Thread(first, "runtime-metrics-owner");
      Thread waiter = new Thread(second, "runtime-metrics-waiter");
      owner.start();
      try {
        assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();
        waiter.start();
        awaitBlocked(waiter, owner);
        assertThat(gauge(registry, "invocations.active")).isEqualTo(1);
        assertThat(gauge(registry, "invocations.waiting")).isEqualTo(1);
        owner.interrupt();
        first.get(10, TimeUnit.SECONDS);
        assertThat(interrupted).isTrue();
        gate.release.countDown();
        assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(42);
        assertThat(operations(registry, "invocation_wait", "success")).isEqualTo(2);
        assertThat(operations(registry, "invocation_execution", "success")).isEqualTo(1);
        assertThat(operations(registry, "invocation_execution", "failure")).isEqualTo(1);
        assertThat(gauge(registry, "invocations.active")).isZero();
        assertThat(gauge(registry, "invocations.waiting")).isZero();
      } finally {
        gate.release.countDown();
        owner.join(10000);
        waiter.join(10000);
        assertThat(owner.isAlive()).isFalse();
        assertThat(waiter.isAlive()).isFalse();
      }
    }
  }

  interface ConversionApi {
    int[] values();
  }

  @Test
  void conversionIsActiveAndShutdownWaitsForIt() throws Exception {
    Gate gate = new Gate();
    HostAccess access =
        HostAccess.newBuilder(HostAccess.ALL)
            .targetTypeMapping(
                Value.class,
                int[].class,
                Value::hasArrayElements,
                value -> {
                  try {
                    gate.block();
                  } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted during conversion", failure);
                  }
                  return new int[] {value.getArrayElement(0).asInt()};
                },
                HostAccess.TargetMappingPrecedence.HIGHEST)
            .build();
    try (Context context =
            Context.newBuilder(SupportedLanguage.PYTHON.id())
                .allowHostAccess(access)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        PyExecutor executor =
            executor(
                context,
                Map.of("python/conversion_api", "def values(): return [42]\n"),
                registry)) {
      var client = executor.bind(ConversionApi.class, Convention.BY_METHOD_NAME);
      FutureTask<int[]> invocation = new FutureTask<>(client::values);
      FutureTask<Void> close =
          new FutureTask<>(
              () -> {
                executor.close();
                return null;
              });
      Thread caller = new Thread(invocation, "metrics-conversion");
      Thread closer = new Thread(close, "metrics-close");
      caller.start();
      try {
        assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();
        closer.start();
        awaitBlocked(closer, caller);
        assertThat(gauge(registry, "invocations.active")).isEqualTo(1);
        gate.release.countDown();
        assertThat(invocation.get(10, TimeUnit.SECONDS)).containsExactly(42);
        close.get(10, TimeUnit.SECONDS);
        assertThat(gauge(registry, "invocations.active")).isZero();
        assertThat(registry.get("polyglot.runtime.executors.closed").counter().count())
            .isEqualTo(1);
      } finally {
        gate.release.countDown();
        caller.join(10000);
        closer.join(10000);
        assertThat(caller.isAlive()).isFalse();
        assertThat(closer.isAlive()).isFalse();
      }
    }
  }

  @Test
  void reloadGaugeTracksTheActualReloadUntilCompletion() throws Exception {
    Gate gate = new Gate();
    Map<String, String> scripts =
        new ConcurrentHashMap<>(Map.of("python/runtime_api", "def value(): return 42\n"));
    try (Context context = context();
        PyExecutor executor = executor(context, scripts, registry)) {
      RuntimeApi client = executor.bind(RuntimeApi.class, Convention.BY_METHOD_NAME);
      assertThat(client.value()).isEqualTo(42);
      context.getBindings("python").putMember("gate", gate);
      scripts.put("python/runtime_api", "gate.block()\ndef value(): return 43\n");
      FutureTask<Void> reload =
          new FutureTask<>(
              () -> {
                executor.reloadContract(RuntimeApi.class, Convention.BY_METHOD_NAME);
                return null;
              });
      Thread caller = new Thread(reload, "metrics-reload");
      caller.start();
      try {
        assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(gauge(registry, "reloads.active")).isEqualTo(1);
        assertThat(gauge(registry, "invocations.active")).isZero();
        gate.release.countDown();
        reload.get(10, TimeUnit.SECONDS);
        assertThat(gauge(registry, "reloads.active")).isZero();
        assertThat(client.value()).isEqualTo(43);
      } finally {
        gate.release.countDown();
        caller.join(10000);
        assertThat(caller.isAlive()).isFalse();
      }
    }
  }

  @Test
  void closeFromReloadCallbackDoesNotLeakReloadGaugeOrBorrowedContext() {
    Map<String, String> scripts =
        new ConcurrentHashMap<>(Map.of("python/runtime_api", "def value(): return 1\n"));
    try (Context context = context();
        PyExecutor executor = executor(context, scripts, registry)) {
      executor.bind(RuntimeApi.class, Convention.BY_METHOD_NAME).value();
      context.getBindings("python").putMember("executor", executor);
      scripts.put("python/runtime_api", "executor.close()\ndef value(): return 2\n");
      assertThatThrownBy(() -> executor.reloadContract(RuntimeApi.class, Convention.BY_METHOD_NAME))
          .isInstanceOf(BindingException.class);
      assertThat(gauge(registry, "reloads.active")).isZero();
      assertThat(operations(registry, "reload", "failure")).isEqualTo(1);
      assertThat(registry.get("polyglot.runtime.executors.closed").counter().count()).isEqualTo(1);
      assertThat(context.eval("python", "40 + 2").asInt()).isEqualTo(42);
      assertThatThrownBy(() -> executor.reloadContract(RuntimeApi.class))
          .isInstanceOf(IllegalStateException.class);
      assertThat(operations(registry, "reload", "failure")).isEqualTo(2);
      assertThat(gauge(registry, "reloads.active")).isZero();
    }
  }

  private static PyExecutor executor(
      Context context, Map<String, String> scripts, SimpleMeterRegistry registry) {
    PyExecutor executor =
        new PyExecutor(context, new InMemoryScriptSource(scripts), ContextOwnership.BORROWED);
    assertThat(
            executor.attachRuntimeObserver(
                () -> new PolyglotRuntimeMetrics(registry, SupportedLanguage.PYTHON)))
        .isTrue();
    return executor;
  }

  private static Context context() {
    return Context.newBuilder(SupportedLanguage.PYTHON.id())
        .allowAllAccess(true)
        .option("engine.WarnInterpreterOnly", "false")
        .build();
  }

  private static long operations(SimpleMeterRegistry registry, String operation, String outcome) {
    return registry
        .get("polyglot.runtime.operations")
        .tags("language", "python", "operation", operation, "outcome", outcome)
        .timer()
        .count();
  }

  private static double accesses(SimpleMeterRegistry registry, String cache, String result) {
    return registry
        .get("polyglot.runtime.cache.access")
        .tags("language", "python", "cache", cache, "result", result)
        .counter()
        .count();
  }

  private static double gauge(SimpleMeterRegistry registry, String suffix) {
    return registry.get("polyglot.runtime." + suffix).tag("language", "python").gauge().value();
  }

  private static void awaitBlocked(Thread waiter, Thread owner) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(waiter.threadId());
      if (info != null
          && info.getThreadState() == Thread.State.BLOCKED
          && info.getLockOwnerId() == owner.threadId()) return;
      assertThat(waiter.isAlive()).isTrue();
      Thread.yield();
    }
    throw new AssertionError("Caller did not wait on executor monitor");
  }

  public static final class Gate {
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    public void block() throws InterruptedException {
      entered.countDown();
      if (!release.await(10, TimeUnit.SECONDS))
        throw new AssertionError("Invocation gate timed out");
    }
  }
}
