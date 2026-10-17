package io.github.ih0rd.polyglot.annotations.spring.integration.failfast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.ScriptNotFoundException;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.client.EnablePolyglotClients;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotPythonAutoConfiguration;
import io.github.ih0rd.polyglot.annotations.spring.context.SpringPolyglotContextFactory;

class FailFastStartupIntegrationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PolyglotAutoConfiguration.class))
          .withUserConfiguration(FailFastClientConfig.class)
          .withPropertyValues(
              "polyglot.core.enabled=true",
              "polyglot.core.fail-fast=true",
              "polyglot.core.log-metadata-on-startup=false");

  @TempDir Path scripts;
  private Context python;

  @AfterEach
  void contextIsClosedBySpring() {
    if (python != null) {
      try {
        assertThatThrownBy(() -> python.eval("python", "1"))
            .isInstanceOf(IllegalStateException.class);
      } finally {
        python.close();
      }
    }
  }

  @Test
  void validBindingStartsWithoutCallingBusinessMethod() throws Exception {
    writeClient("raise AssertionError('business method must not run during startup')");
    withPython(true).run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void validatedClientRemainsCallableAtRuntime() throws Exception {
    writeClient("return 'hello'");
    withPython(true)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(FailingPythonClient.class).hello()).isEqualTo("hello");
            });
  }

  @Test
  void missingModuleFailsStartupWithClientAndScriptDetails() {
    withPython(true)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasStackTraceContaining(FailingPythonClient.class.getName())
                  .hasStackTraceContaining("failing_python_client")
                  .hasRootCauseInstanceOf(ScriptNotFoundException.class);
            });
  }

  @Test
  void missingMethodFailsStartupWithClientAndMethodDetails() throws Exception {
    Files.writeString(
        scripts.resolve("failing_python_client.py"), "class FailingPythonClient:\n    pass\n");
    withPython(true)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasStackTraceContaining(FailingPythonClient.class.getName())
                  .hasRootCauseInstanceOf(BindingException.class)
                  .hasStackTraceContaining("Python method 'hello' not found or not executable");
            });
  }

  @Test
  void disabledValidationDefersMissingModuleFailureUntilClientLookup() {
    withPython(false)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThatThrownBy(() -> context.getBean(FailingPythonClient.class))
                  .hasRootCauseInstanceOf(ScriptNotFoundException.class);
            });
  }

  @Test
  void disabledValidationDefersMissingMethodFailureUntilClientLookup() throws Exception {
    Files.writeString(
        scripts.resolve("failing_python_client.py"), "class FailingPythonClient:\n    pass\n");
    withPython(false)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThatThrownBy(() -> context.getBean(FailingPythonClient.class))
                  .hasRootCauseInstanceOf(BindingException.class)
                  .hasStackTraceContaining("Python method 'hello' not found or not executable");
            });
  }

  private void writeClient(String body) throws Exception {
    Files.writeString(
        scripts.resolve("failing_python_client.py"),
        "class FailingPythonClient:\n    def hello(self):\n        " + body + "\n");
  }

  private ApplicationContextRunner withPython(boolean failFast) {
    SpringPolyglotContextFactory factory = mock(SpringPolyglotContextFactory.class);
    // The auto-configured executor owns this real context and must close it even on startup
    // failure.
    python = Context.newBuilder("python").allowAllAccess(true).build();
    when(factory.create(SupportedLanguage.PYTHON)).thenReturn(python);
    return contextRunner
        .withConfiguration(AutoConfigurations.of(PolyglotPythonAutoConfiguration.class))
        .withBean(SpringPolyglotContextFactory.class, () -> factory)
        .withPropertyValues(
            "polyglot.core.fail-fast=" + failFast,
            "polyglot.python.enabled=true",
            "polyglot.python.resources-path=" + scripts.toUri());
  }

  @Test
  void invalidDiscoveredClientFailsContextStartupWhenFailFastIsEnabled() {
    contextRunner
        .withBean(
            PyExecutor.class,
            () -> {
              PyExecutor executor = mock(PyExecutor.class);
              doThrow(new BindingException("broken contract"))
                  .when(executor)
                  .validateBinding(FailingPythonClient.class, Convention.DEFAULT);
              return executor;
            })
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(BindingException.class);
            });
  }
}

@Configuration(proxyBeanMethods = false)
@EnablePolyglotClients
class FailFastClientConfig {}

@PolyglotClient(languages = SupportedLanguage.PYTHON)
interface FailingPythonClient {
  String hello();
}
