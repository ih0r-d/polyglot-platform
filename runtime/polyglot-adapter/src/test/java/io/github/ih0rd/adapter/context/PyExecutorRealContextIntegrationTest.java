package io.github.ih0rd.adapter.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.graalvm.polyglot.Context;

import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.SupportedLanguage;

class PyExecutorRealContextIntegrationTest {

  interface GreetingApi {
    String hello(String name);
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
