package io.github.ih0rd.adapter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.ScriptNotFoundException;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

class PythonReloadIntegrationTest {
  interface ReloadApi {
    int next();
  }

  interface IndependentApi {
    int independent();
  }

  @Test
  void existingProxiesObserveRepeatedReloadsAndInstanceResetWithoutGrowingCaches() {
    for (Convention convention : Convention.values()) {
      Map<String, String> scripts = scripts();
      try (PyExecutor executor = executor(scripts)) {
        ReloadApi first = executor.bind(ReloadApi.class, convention);
        ReloadApi second = executor.bind(ReloadApi.class, convention);
        assertThat(first.next()).isEqualTo(11);
        assertThat(second.next()).isEqualTo(12);
        for (int version = 2; version <= 12; version++) {
          scripts.put("python/reload_api", script(version * 10));
          executor.reloadContract(ReloadApi.class, convention);
          assertThat(first.next()).isEqualTo(version * 10 + 1);
          assertThat(second.next()).isEqualTo(version * 10 + 2);
          assertThat(executor.metadata().get("sourceCacheSize")).isEqualTo(1);
          assertThat(executor.metadata().get("instanceCacheSize"))
              .isEqualTo(convention == Convention.BY_METHOD_NAME ? 0 : 1);
        }
      }
    }
  }

  @Test
  void reloadPreservesIndependentContractInstanceAndMutableState() {
    Map<String, String> scripts = scripts();
    scripts.put(
        "python/independent_api",
        """
        class IndependentApi:
            def __init__(self): self.calls = 0
            def independent(self):
                self.calls += 1
                return self.calls
        """);
    try (PyExecutor executor = executor(scripts)) {
      ReloadApi client = executor.bind(ReloadApi.class, Convention.BY_METHOD_NAME);
      IndependentApi independent = executor.bind(IndependentApi.class);
      assertThat(client.next()).isEqualTo(11);
      assertThat(independent.independent()).isEqualTo(1);
      scripts.put("python/reload_api", script(20));
      executor.reloadContract(ReloadApi.class, Convention.BY_METHOD_NAME);
      assertThat(client.next()).isEqualTo(21);
      assertThat(independent.independent()).isEqualTo(2);
      assertThat(executor.metadata().get("sourceCacheSize")).isEqualTo(2);
    }
  }

  @Test
  void missingAndInvalidSourcesLeaveOldBindingsAndInstanceStateUntouched()
      throws ReflectiveOperationException {
    for (Convention convention : Convention.values()) {
      Map<String, String> scripts = scripts();
      try (PyExecutor executor = executor(scripts)) {
        ReloadApi client = executor.bind(ReloadApi.class, convention);
        assertThat(client.next()).isEqualTo(11);
        Object retained = retainInstance(executor);
        scripts.remove("python/reload_api");
        assertThatThrownBy(() -> executor.reloadContract(ReloadApi.class, convention))
            .isInstanceOf(ScriptNotFoundException.class)
            .hasMessageContaining("reload_api");
        assertThat(client.next()).isEqualTo(12);
        scripts.put("python/reload_api", "side_effect = 99\ndef broken(:\n");
        assertThatThrownBy(() -> executor.reloadContract(ReloadApi.class, convention))
            .isInstanceOf(PolyglotException.class);
        assertThat(executor.evaluate("'side_effect' in globals()").asBoolean()).isFalse();
        assertThat(client.next()).isEqualTo(13);
        Reference.reachabilityFence(retained);
      }
    }
  }

  @Test
  void omittedExportsCannotReuseStalePolyglotOrLanguageBindings() {
    for (Convention convention : Convention.values()) {
      Map<String, String> scripts = scripts();
      scripts.put(
          "python/reload_api",
          script(10)
              + "\nimport polyglot\npolyglot.export_value('ReloadApi', ReloadApi)\npolyglot.export_value('next', next)\n");
      try (PyExecutor executor = executor(scripts)) {
        ReloadApi client = executor.bind(ReloadApi.class, convention);
        assertThat(client.next()).isEqualTo(11);
        scripts.put("python/reload_api", "unrelated_marker = 99\n");
        assertThatThrownBy(() -> executor.reloadContract(ReloadApi.class, convention))
            .isInstanceOf(BindingException.class)
            .hasMessageContaining("reload_api", "cannot be rolled back")
            .hasCauseInstanceOf(BindingException.class);
        assertThat(client.next()).isEqualTo(12);
        // Only binding references are restored: arbitrary module effects intentionally remain.
        assertThat(executor.evaluate("unrelated_marker").asInt()).isEqualTo(99);
        scripts.put("python/reload_api", script(30));
        executor.reloadContract(ReloadApi.class, convention);
        assertThat(client.next()).isEqualTo(31);
      }
    }
  }

  @Test
  void incompatibleTargetAndFailedEvaluationRestoreKnownBindingsAndAllowRecovery() {
    for (Convention convention : Convention.values()) {
      Map<String, String> scripts = scripts();
      try (PyExecutor executor = executor(scripts)) {
        ReloadApi client = executor.bind(ReloadApi.class, convention);
        assertThat(client.next()).isEqualTo(11);
        scripts.put("python/reload_api", "class ReloadApi: pass\nnext = 1\n");
        assertThatThrownBy(() -> executor.reloadContract(ReloadApi.class, convention))
            .isInstanceOf(BindingException.class);
        assertThat(client.next()).isEqualTo(12);
        scripts.put("python/reload_api", "raise RuntimeError('module failed')\n");
        assertThatThrownBy(() -> executor.reloadContract(ReloadApi.class, convention))
            .isInstanceOf(BindingException.class)
            .hasCauseInstanceOf(PolyglotException.class);
        assertThat(client.next()).isEqualTo(13);
        scripts.put("python/reload_api", script(40));
        executor.reloadContract(ReloadApi.class, convention);
        assertThat(client.next()).isEqualTo(41);
      }
    }
  }

  @Test
  void failedInitialReloadDoesNotRetainPartialSourcesOrInstances() {
    Map<String, String> scripts = scripts();
    scripts.put("python/reload_api", "class ReloadApi: pass\n");
    try (PyExecutor executor = executor(scripts)) {
      assertThatThrownBy(() -> executor.reloadContract(ReloadApi.class))
          .isInstanceOf(BindingException.class);
      assertThat(executor.metadata().get("sourceCacheSize")).isEqualTo(0);
      assertThat(executor.metadata().get("instanceCacheSize")).isEqualTo(0);
      assertThat(executor.evaluate("'ReloadApi' in globals()").asBoolean()).isFalse();
    }
  }

  @Test
  void failedFirstMethodNameReloadDoesNotPublishAnAbsentBuiltinAsAGlobal() {
    Map<String, String> scripts = scripts();
    scripts.put("python/reload_api", "unrelated = 1\n");
    try (PyExecutor executor = executor(scripts)) {
      assertThat(executor.evaluate("'next' in globals()").asBoolean()).isFalse();
      assertThatThrownBy(() -> executor.reloadContract(ReloadApi.class, Convention.BY_METHOD_NAME))
          .isInstanceOf(BindingException.class);
      assertThat(executor.evaluate("'next' in globals()").asBoolean()).isFalse();
      assertThat(executor.metadata().get("sourceCacheSize")).isEqualTo(0);
    }
  }

  @Test
  void cachedCallsDoNotReopenSourceBeforeOrAfterReload() {
    AtomicInteger reads = new AtomicInteger();
    InMemoryScriptSource delegate = new InMemoryScriptSource(scripts());
    ScriptSource counting =
        new ScriptSource() {
          @Override
          public boolean exists(SupportedLanguage language, String name) {
            return delegate.exists(language, name);
          }

          @Override
          public Reader open(SupportedLanguage language, String name) {
            reads.incrementAndGet();
            return delegate.open(language, name);
          }
        };
    try (PyExecutor executor = new PyExecutor(context(), counting)) {
      ReloadApi client = executor.bind(ReloadApi.class, Convention.BY_METHOD_NAME);
      for (int i = 1; i <= 100; i++) assertThat(client.next()).isEqualTo(10 + i);
      assertThat(reads).hasValue(1);
      executor.reloadContract(ReloadApi.class, Convention.BY_METHOD_NAME);
      for (int i = 1; i <= 100; i++) assertThat(client.next()).isEqualTo(10 + i);
      assertThat(reads).hasValue(2);
      executor.clearSourceCache();
      assertThat(client.next()).isEqualTo(11);
      assertThat(reads).hasValue(3);
    }
  }

  @Test
  void reloadWaitsForInflightInvocationAndCloseWaitsForReload() throws Exception {
    for (boolean closeDuringReload : new boolean[] {false, true}) {
      Map<String, String> scripts = scripts();
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      try (PyExecutor executor = executor(scripts)) {
        executor.context.getBindings("python").putMember("gate", new Gate(entered, release));
        executor.evaluate("def pause(): gate.pause()\n");
        ReloadApi client = executor.bind(ReloadApi.class, Convention.BY_METHOD_NAME);
        assertThat(client.next()).isEqualTo(11);
        scripts.put("python/reload_api", (closeDuringReload ? "pause()\n" : "") + script(20));
        if (!closeDuringReload) executor.evaluate("def next():\n    pause()\n    return 12\n");
        FutureTask<Integer> ownerTask =
            new FutureTask<>(
                () -> {
                  if (closeDuringReload) {
                    executor.reloadContract(ReloadApi.class, Convention.BY_METHOD_NAME);
                    return 0;
                  }
                  return client.next();
                });
        FutureTask<Void> otherTask =
            new FutureTask<>(
                () -> {
                  if (closeDuringReload) executor.close();
                  else executor.reloadContract(ReloadApi.class, Convention.BY_METHOD_NAME);
                  return null;
                });
        Thread owner = new Thread(ownerTask, "guest-operation");
        Thread other = new Thread(otherTask, "reload-or-close");
        owner.start();
        try {
          assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
          other.start();
          awaitBlocked(other, owner);
          release.countDown();
          assertThat(ownerTask.get(10, TimeUnit.SECONDS)).isEqualTo(closeDuringReload ? 0 : 12);
          otherTask.get(10, TimeUnit.SECONDS);
          if (closeDuringReload) {
            assertThatThrownBy(
                    () -> executor.reloadContract(ReloadApi.class, Convention.BY_METHOD_NAME))
                .isInstanceOf(IllegalStateException.class);
          } else {
            assertThat(client.next()).isEqualTo(21);
          }
        } finally {
          release.countDown();
          owner.join(10000);
          other.join(10000);
          assertThat(owner.isAlive()).isFalse();
          assertThat(other.isAlive()).isFalse();
        }
      }
    }
  }

  @Test
  void reloadEvaluatesModuleExactlyOnceAndBatchFailureKeepsEarlierSuccess() {
    for (Convention convention : Convention.values()) {
      Map<String, String> scripts = scripts();
      scripts.put(
          "python/reload_api",
          script(10) + "\nevaluations = globals().get('evaluations', 0) + 1\n");
      try (PyExecutor executor = executor(scripts)) {
        ReloadApi client = executor.bind(ReloadApi.class, convention);
        assertThat(client.next()).isEqualTo(11);
        assertThat(executor.evaluate("evaluations").asInt()).isEqualTo(1);
        scripts.put("python/reload_api", script(20) + "\nevaluations += 1\n");
        assertThatThrownBy(
                () ->
                    executor.reloadContracts(
                        java.util.List.of(ReloadApi.class, IndependentApi.class), convention))
            .isInstanceOf(ScriptNotFoundException.class);
        assertThat(executor.evaluate("evaluations").asInt()).isEqualTo(2);
        assertThat(client.next()).isEqualTo(21);
      }
    }
  }

  @Test
  void shutdownFromReloadInitializationDoesNotRepublishCaches() {
    Map<String, String> scripts = scripts();
    try (Context context = context();
        PyExecutor executor =
            new PyExecutor(context, new InMemoryScriptSource(scripts), ContextOwnership.BORROWED)) {
      ReloadApi client = executor.bind(ReloadApi.class, Convention.BY_METHOD_NAME);
      assertThat(client.next()).isEqualTo(11);
      context
          .getBindings("python")
          .putMember(
              "shutdown",
              (ProxyExecutable)
                  args -> {
                    executor.close();
                    return null;
                  });
      scripts.put("python/reload_api", "shutdown()\n" + script(20));
      assertThatThrownBy(() -> executor.reloadContract(ReloadApi.class, Convention.BY_METHOD_NAME))
          .isInstanceOf(BindingException.class);
      assertThat(executor.isClosed()).isTrue();
      assertThat(executor.metadata().get("sourceCacheSize")).isEqualTo(0);
      assertThat(executor.metadata().get("instanceCacheSize")).isEqualTo(0);
      assertThatThrownBy(client::next).isInstanceOf(IllegalStateException.class);
      assertThat(context.eval("python", "40 + 2").asInt()).isEqualTo(42);
    }
  }

  @Test
  void reentrantReloadIsRejectedWithoutMutatingBindings() {
    Map<String, String> scripts = scripts();
    try (PyExecutor executor = executor(scripts)) {
      executor
          .context
          .getBindings("python")
          .putMember(
              "reload",
              (ProxyExecutable)
                  args -> {
                    assertThatThrownBy(() -> executor.reloadContract(ReloadApi.class))
                        .isInstanceOf(BindingException.class)
                        .hasMessageContaining("active executor operation");
                    return null;
                  });
      scripts.put("python/reload_api", "def next():\n    reload()\n    return 42\n");
      ReloadApi client = executor.bind(ReloadApi.class, Convention.BY_METHOD_NAME);
      assertThat(client.next()).isEqualTo(42);
      assertThat(client.next()).isEqualTo(42);
    }
  }

  @Test
  void interruptedReloadRestoresInterruptStatusAndReleasesMonitor() throws Exception {
    Map<String, String> scripts = scripts();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch blocker = new CountDownLatch(1);
    try (PyExecutor executor = executor(scripts)) {
      ReloadApi client = executor.bind(ReloadApi.class, Convention.BY_METHOD_NAME);
      assertThat(client.next()).isEqualTo(11);
      executor.context.getBindings("python").putMember("gate", new Gate(entered, blocker));
      executor.evaluate("def pause(): gate.pause()\n");
      scripts.put("python/reload_api", "pause()\n" + script(20));
      AtomicBoolean interrupted = new AtomicBoolean();
      FutureTask<Void> reload =
          new FutureTask<>(
              () -> {
                try {
                  assertThatThrownBy(
                          () -> executor.reloadContract(ReloadApi.class, Convention.BY_METHOD_NAME))
                      .isInstanceOf(BindingException.class);
                  interrupted.set(Thread.currentThread().isInterrupted());
                  return null;
                } finally {
                  Thread.interrupted();
                }
              });
      Thread thread = new Thread(reload, "interrupted-reload");
      thread.start();
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        thread.interrupt();
        reload.get(10, TimeUnit.SECONDS);
        assertThat(interrupted).isTrue();
        assertThat(client.next()).isEqualTo(12);
      } finally {
        blocker.countDown();
        thread.join(10000);
        assertThat(thread.isAlive()).isFalse();
      }
    }
  }

  public static final class Gate {
    private final CountDownLatch entered;
    private final CountDownLatch release;

    Gate(CountDownLatch entered, CountDownLatch release) {
      this.entered = entered;
      this.release = release;
    }

    public void pause() throws InterruptedException {
      entered.countDown();
      if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Gate timed out");
    }
  }

  private static void awaitBlocked(Thread contender, Thread owner) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(contender.threadId());
      if (info != null
          && info.getThreadState() == Thread.State.BLOCKED
          && info.getLockOwnerId() == owner.threadId()) return;
      assertThat(contender.isAlive()).as("contender must wait for executor monitor").isTrue();
      Thread.yield();
    }
    throw new AssertionError("Contender did not block on executor monitor");
  }

  // Pin the existing weak Value only while asserting identity/state across allocation-heavy
  // parsing.
  private static Object retainInstance(PyExecutor executor) throws ReflectiveOperationException {
    var field = PyExecutor.class.getDeclaredField("instanceCache");
    field.setAccessible(true);
    var reference = (WeakReference<?>) ((Map<?, ?>) field.get(executor)).get(ReloadApi.class);
    return reference == null ? null : reference.get();
  }

  private static Map<String, String> scripts() {
    return new ConcurrentHashMap<>(Map.of("python/reload_api", script(10)));
  }

  private static PyExecutor executor(Map<String, String> scripts) {
    return new PyExecutor(context(), new InMemoryScriptSource(scripts));
  }

  private static Context context() {
    return Context.newBuilder("python")
        .allowAllAccess(true)
        .option("engine.WarnInterpreterOnly", "false")
        .build();
  }

  private static String script(int value) {
    return """
        count = 0
        def next():
            global count
            count += 1
            return %d + count
        class ReloadApi:
            def __init__(self): self.count = 0
            def next(self):
                self.count += 1
                return %d + self.count
        """
        .formatted(value, value);
  }
}
