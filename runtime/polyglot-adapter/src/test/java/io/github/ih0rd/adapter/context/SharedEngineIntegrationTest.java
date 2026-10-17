package io.github.ih0rd.adapter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.SupportedLanguage;

class SharedEngineIntegrationTest {

  interface GreetingApi {
    String hello(String name);
  }

  @Test
  void ownedContextsRemainIsolatedAndClosingOneExecutorKeepsEngineAndSiblingUsable() {
    try (Engine engine = newEngine()) {
      try (Context first = newContext(engine);
          Context second = newContext(engine);
          PyExecutor firstExecutor = executor(first, "first");
          PyExecutor secondExecutor = executor(second, "second")) {
        assertThat(first.getEngine()).isSameAs(engine);
        assertThat(second.getEngine()).isSameAs(engine);
        firstExecutor.evaluate("private_value = 99");
        assertThat(second.getBindings("python").hasMember("private_value")).isFalse();
        assertThat(firstExecutor.bind(GreetingApi.class).hello("Ada")).isEqualTo("first:Ada");
        assertThat(secondExecutor.bind(GreetingApi.class).hello("Ada")).isEqualTo("second:Ada");

        firstExecutor.close();
        assertThatThrownBy(() -> first.eval("python", "1"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(secondExecutor.bind(GreetingApi.class).hello("Ada")).isEqualTo("second:Ada");
      }
      try (Context later = newContext(engine)) {
        assertThat(later.eval("python", "40 + 2").asInt()).isEqualTo(42);
      }
    }
  }

  @Test
  void borrowedExecutorLeavesContextOpenAndOwnerClosesEngineLast() {
    Context context;
    try (Engine engine = newEngine()) {
      try (Context borrowed = newContext(engine)) {
        context = borrowed;
        try (PyExecutor executor =
            PyExecutor.createWithContext(
                borrowed, new InMemoryScriptSource(Map.of()), ContextOwnership.BORROWED)) {
          executor.evaluate("answer = 42");
        }
        assertThat(borrowed.eval("python", "answer").asInt()).isEqualTo(42);
      }
      assertThatThrownBy(() -> context.eval("python", "1"))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void defaultsContinueToUseIndependentEngines() {
    try (Context first = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
        Context second = PolyglotHelper.newContext(SupportedLanguage.PYTHON)) {
      assertThat(first.getEngine()).isNotSameAs(second.getEngine());
      first.close();
      assertThat(second.eval("python", "40 + 2").asInt()).isEqualTo(42);
    }
  }

  private static Engine newEngine() {
    return Engine.newBuilder("python").option("engine.WarnInterpreterOnly", "false").build();
  }

  private static Context newContext(Engine engine) {
    return PolyglotHelper.newContext(
        SupportedLanguage.PYTHON,
        false,
        builder ->
            builder
                .engine(engine)
                .allowAllAccess(true)
                .allowExperimentalOptions(true)
                .option("python.WarnExperimentalFeatures", "false"));
  }

  private static PyExecutor executor(Context context, String prefix) {
    String script =
        """
        class GreetingApi:
            def hello(self, name):
                return "%s:" + name
        """
            .formatted(prefix);
    return PyExecutor.createWithContext(
        context,
        new InMemoryScriptSource(Map.of("python/greeting_api", script)),
        ContextOwnership.OWNED_BY_EXECUTOR);
  }
}
