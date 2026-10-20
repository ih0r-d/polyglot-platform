package io.github.ih0rd.adapter.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;

class PyExecutorRealContextIntegrationTest {

  interface GreetingApi {
    String hello(String name);
  }

  interface MethodNameApi {
    int value();
  }

  interface FirstMethodNameApi {
    int firstValue();
  }

  interface SecondMethodNameApi {
    int secondValue();
  }

  @Test
  void preloadScriptDoesNotHydrateContractCachesInRealContext() {
    Map<String, String> scripts =
        new ConcurrentHashMap<>(
            Map.of(
                "python/bootstrap",
                """
                preload_flag = "boot"
                """));

    try (PyExecutor executor = createExecutor(scripts)) {
      executor.preloadScript("bootstrap");

      Map<String, Object> metadata = executor.metadata();
      assertEquals(0, metadata.get("sourceCacheSize"));
      assertEquals(0, metadata.get("instanceCacheSize"));
      assertEquals(List.of(), metadata.get("cachedInterfaces"));
    }
  }

  @Test
  void clearSourceCacheKeepsLiveInstanceUntilContractInvalidationInRealContext() {
    Map<String, String> scripts = new ConcurrentHashMap<>();
    scripts.put("python/greeting_api", greetingScript("v1"));

    try (PyExecutor executor = createExecutor(scripts)) {
      GreetingApi client = executor.bind(GreetingApi.class);
      assertEquals("v1:Ada", client.hello("Ada"));

      scripts.put("python/greeting_api", greetingScript("v2"));

      executor.clearSourceCache();
      assertEquals("v1:Ada", client.hello("Ada"));

      executor.invalidateContractCache(GreetingApi.class);
      assertEquals("v2:Ada", client.hello("Ada"));
    }
  }

  @Test
  void preloadAndLaterBindingCanRepeatScriptSideEffectsInRealContext() {
    Map<String, String> scripts =
        new ConcurrentHashMap<>(
            Map.of(
                "python/greeting_api",
                """
                try:
                    eval_counter = eval_counter + 1
                except NameError:
                    eval_counter = 1

                class GreetingApi:
                    def hello(self, name):
                        return f"{eval_counter}:{name}"

                import polyglot
                polyglot.export_value('GreetingApi', GreetingApi)
                """));

    try (PyExecutor executor = createExecutor(scripts)) {
      executor.preloadScript("greeting_api");

      GreetingApi client = executor.bind(GreetingApi.class);

      assertEquals("2:Ada", client.hello("Ada"));
    }
  }

  @Test
  void clearAllCachesPicksUpChangedCodeInRealContext() {
    Map<String, String> scripts = new ConcurrentHashMap<>();
    scripts.put("python/greeting_api", greetingScript("v1"));

    try (PyExecutor executor = createExecutor(scripts)) {
      GreetingApi client = executor.bind(GreetingApi.class);
      assertEquals("v1:Ada", client.hello("Ada"));

      scripts.put("python/greeting_api", greetingScript("v2"));

      executor.clearAllCaches();

      assertEquals("v2:Ada", client.hello("Ada"));
    }
  }

  @Test
  void reloadContractPicksUpChangedCodeInRealContext() {
    Map<String, String> scripts = new ConcurrentHashMap<>();
    scripts.put("python/greeting_api", greetingScript("v1"));

    try (PyExecutor executor = createExecutor(scripts)) {
      GreetingApi client = executor.bind(GreetingApi.class);
      assertEquals("v1:Ada", client.hello("Ada"));

      scripts.put("python/greeting_api", greetingScript("v2"));

      executor.reloadContract(GreetingApi.class);

      assertEquals("v2:Ada", client.hello("Ada"));
    }
  }

  @Test
  void methodNameConventionEvaluatesModuleOnceUntilInvalidated() {
    Map<String, String> scripts = new ConcurrentHashMap<>();
    scripts.put("python/method_name_api", methodNameScript(1));

    try (PyExecutor executor = createExecutor(scripts)) {
      MethodNameApi client = executor.bind(MethodNameApi.class, Convention.BY_METHOD_NAME);
      assertEquals(102, client.value());
      assertEquals(103, client.value());

      scripts.put("python/method_name_api", methodNameScript(2));
      executor.clearSourceCache();
      assertEquals(203, client.value());

      scripts.put("python/method_name_api", methodNameScript(3));
      executor.invalidateContractCache(MethodNameApi.class);
      assertEquals(304, client.value());

      scripts.put("python/method_name_api", methodNameScript(4));
      executor.reloadContract(MethodNameApi.class, Convention.BY_METHOD_NAME);
      assertEquals(405, client.value());
    }
  }

  @Test
  void concurrentMethodNameClientsShareOneExecutorAndEvaluateEachModuleOnce() throws Exception {
    Map<String, String> scripts =
        new ConcurrentHashMap<>(
            Map.of(
                "python/first_method_name_api",
                "first_eval_count = globals().get('first_eval_count', 0) + 1\n"
                    + "def firstValue(): return first_eval_count\n",
                "python/second_method_name_api",
                "second_eval_count = globals().get('second_eval_count', 0) + 1\n"
                    + "def secondValue(): return second_eval_count\n"));

    try (PyExecutor executor = createExecutor(scripts);
        ExecutorService callers = Executors.newFixedThreadPool(2)) {
      FirstMethodNameApi first = executor.bind(FirstMethodNameApi.class, Convention.BY_METHOD_NAME);
      SecondMethodNameApi second =
          executor.bind(SecondMethodNameApi.class, Convention.BY_METHOD_NAME);
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      Future<?> firstCalls =
          callers.submit(() -> invokeRepeatedly(first::firstValue, ready, start));
      Future<?> secondCalls =
          callers.submit(() -> invokeRepeatedly(second::secondValue, ready, start));

      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      firstCalls.get(30, TimeUnit.SECONDS);
      secondCalls.get(30, TimeUnit.SECONDS);
    }
  }

  @Test
  void failedMethodNameModuleEvaluationIsRetried() {
    Context context =
        Context.newBuilder(SupportedLanguage.PYTHON.id()).allowAllAccess(true).build();
    context.initialize(SupportedLanguage.PYTHON.id());
    context.getBindings(SupportedLanguage.PYTHON.id()).putMember("allow_module", false);
    Map<String, String> scripts =
        new ConcurrentHashMap<>(
            Map.of(
                "python/method_name_api",
                "if not allow_module: raise RuntimeError('not ready')\n"
                    + "eval_count = globals().get('eval_count', 0) + 1\n"
                    + "def value(): return eval_count\n"));

    try (PyExecutor executor =
        PyExecutor.createWithContext(context, new InMemoryScriptSource(scripts))) {
      MethodNameApi client = executor.bind(MethodNameApi.class, Convention.BY_METHOD_NAME);
      org.assertj.core.api.Assertions.assertThatThrownBy(client::value)
          .isInstanceOf(io.github.ih0rd.adapter.exceptions.InvocationException.class)
          .hasCauseInstanceOf(org.graalvm.polyglot.PolyglotException.class);

      context.getBindings(SupportedLanguage.PYTHON.id()).putMember("allow_module", true);
      assertEquals(1, client.value());
      assertEquals(1, client.value());
    }
  }

  private static void invokeRepeatedly(
      java.util.function.IntSupplier call, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for concurrent invocation start");
      }
      for (int i = 0; i < 100; i++) {
        assertEquals(1, call.getAsInt());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted during concurrent invocation", e);
    }
  }

  private static String greetingScript(String prefix) {
    return """
        class GreetingApi:
            def hello(self, name):
                return "%s:" + name

        import polyglot
        polyglot.export_value('GreetingApi', GreetingApi)
        """
        .formatted(prefix);
  }

  private static String methodNameScript(int value) {
    return """
        try:
            eval_count = eval_count + 1
        except NameError:
            eval_count = 1

        call_count = 0
        def value():
            global call_count
            call_count += 1
            return eval_count * 100 + call_count + %d
        """
        .formatted(value);
  }

  private static PyExecutor createExecutor(Map<String, String> scripts) {
    Context context =
        Context.newBuilder(SupportedLanguage.PYTHON.id())
            .allowAllAccess(true)
            .allowExperimentalOptions(true)
            .build();
    context.initialize(SupportedLanguage.PYTHON.id());
    return PyExecutor.createWithContext(context, new InMemoryScriptSource(scripts));
  }
}
