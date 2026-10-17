package io.github.ih0rd.polyglot.annotations.spring.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import io.github.ih0rd.adapter.context.ContextOwnership;
import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotPythonAutoConfiguration;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

class SharedEngineSpringIntegrationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  PolyglotAutoConfiguration.class, PolyglotPythonAutoConfiguration.class))
          .withPropertyValues(
              "polyglot.python.enabled=true",
              "polyglot.python.safe-defaults=false",
              "polyglot.python.resources-path=classpath:python",
              "polyglot.core.log-metadata-on-startup=false");

  @Test
  void springManagedEngineOutlivesExecutorsAndIsClosedOnShutdown() {
    Engine[] engine = new Engine[1];
    PyExecutor[] executor = new PyExecutor[1];
    runner
        .withUserConfiguration(SharedEngineConfiguration.class)
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              engine[0] = application.getBean(Engine.class);
              executor[0] = application.getBean(PyExecutor.class);
              assertThat(application.getBeanFactory().getDependentBeans("pythonEngine"))
                  .contains("pyExecutor");
              executor[0].evaluate("private_value = 99");
              try (Context second =
                  application
                      .getBean(SpringPolyglotContextFactory.class)
                      .create(SupportedLanguage.PYTHON)) {
                assertThat(second.getEngine()).isSameAs(engine[0]);
                assertThat(second.getBindings("python").hasMember("private_value")).isFalse();
                assertThat(second.eval("python", "40 + 2").asInt()).isEqualTo(42);
              }
              assertThat(executor[0].evaluate("private_value").asInt()).isEqualTo(99);
            });
    assertThat(executor[0].isClosed()).isTrue();
    assertThatThrownBy(() -> Context.newBuilder("python").engine(engine[0]).build())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void twoSpringApplicationsCanBorrowOneExternallyOwnedEngine() {
    try (Engine engine =
        Engine.newBuilder("python").option("engine.WarnInterpreterOnly", "false").build()) {
      ApplicationContextRunner sharedRunner =
          runner.withBean(PolyglotContextCustomizer.class, () -> customizer(engine));
      sharedRunner.run(
          first -> {
            assertThat(first).hasNotFailed();
            PyExecutor firstExecutor = first.getBean(PyExecutor.class);
            firstExecutor.evaluate("private_value = 99");
            PyExecutor[] secondExecutor = new PyExecutor[1];
            sharedRunner.run(
                second -> {
                  assertThat(second).hasNotFailed();
                  secondExecutor[0] = second.getBean(PyExecutor.class);
                  assertThat(secondExecutor[0].evaluate("'private_value' in globals()").asBoolean())
                      .isFalse();
                });
            assertThat(secondExecutor[0].isClosed()).isTrue();
            assertThat(firstExecutor.evaluate("private_value").asInt()).isEqualTo(99);
          });
      try (Context later =
          PolyglotHelper.newContext(
              SupportedLanguage.PYTHON,
              false,
              builder -> customizer(engine).customize(SupportedLanguage.PYTHON, builder))) {
        assertThat(later.eval("python", "40 + 2").asInt()).isEqualTo(42);
      }
    }
  }

  private static PolyglotContextCustomizer customizer(Engine engine) {
    return (language, builder) -> {
      if (language == SupportedLanguage.PYTHON) {
        builder
            .engine(engine)
            .allowAllAccess(true)
            .allowExperimentalOptions(true)
            .option("python.WarnExperimentalFeatures", "false");
      }
    };
  }

  @Configuration(proxyBeanMethods = false)
  static class SharedEngineConfiguration {
    @Bean(destroyMethod = "close")
    Engine pythonEngine() {
      return Engine.newBuilder("python").option("engine.WarnInterpreterOnly", "false").build();
    }

    @Bean
    PolyglotContextCustomizer sharedPythonEngine(Engine pythonEngine) {
      return customizer(pythonEngine);
    }

    @Bean(destroyMethod = "close")
    @DependsOn("pythonEngine")
    PyExecutor pyExecutor(
        @Qualifier("pyScriptSource") ScriptSource scriptSource,
        ObjectProvider<PolyglotContextCustomizer> customizers) {
      Context context =
          PolyglotHelper.newContext(
              SupportedLanguage.PYTHON,
              false,
              builder ->
                  customizers
                      .orderedStream()
                      .forEach(
                          customizer -> customizer.customize(SupportedLanguage.PYTHON, builder)));
      return PyExecutor.createWithContext(
          context, scriptSource, ContextOwnership.OWNED_BY_EXECUTOR);
    }
  }
}
