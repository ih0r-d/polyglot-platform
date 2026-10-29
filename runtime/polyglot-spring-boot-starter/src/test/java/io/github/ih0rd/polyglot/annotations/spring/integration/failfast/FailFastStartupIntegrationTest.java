package io.github.ih0rd.polyglot.annotations.spring.integration.failfast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.InvocationException;
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
  void existingSpringClientObservesReloadWithoutBeanRecreation() throws Exception {
    writeClient("return 'before'");
    for (boolean failFast : new boolean[] {true, false}) {
      withPython(failFast)
          .run(
              application -> {
                assertThat(application).hasNotFailed();
                FailingPythonClient client = application.getBean(FailingPythonClient.class);
                assertThat(client.hello()).isEqualTo("before");
                try {
                  writeClient("return 'after'");
                  application.getBean(PyExecutor.class).reloadContract(FailingPythonClient.class);
                  assertThat(application.getBean(FailingPythonClient.class)).isSameAs(client);
                  assertThat(client.hello()).isEqualTo("after");
                  writeClient("return 'before'");
                } catch (IOException failure) {
                  throw new AssertionError("Could not update reload test module", failure);
                }
              });
    }
  }

  @Test
  void interruptedClientRestoresStatusAndAllowsSpringShutdown() throws Exception {
    writeClient("started.countDown()\n        getattr(blocker, 'await')()\n        return 'hello'");
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch blocker = new CountDownLatch(1);
    withPython(true)
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              python.getBindings("python").putMember("started", started);
              python.getBindings("python").putMember("blocker", blocker);
              FailingPythonClient client = application.getBean(FailingPythonClient.class);
              PyExecutor executor = application.getBean(PyExecutor.class);
              AtomicBoolean interrupted = new AtomicBoolean();
              FutureTask<Void> invocation =
                  new FutureTask<>(
                      () -> {
                        try {
                          assertThatThrownBy(client::hello)
                              .isInstanceOf(InvocationException.class)
                              .hasCauseInstanceOf(PolyglotException.class);
                          interrupted.set(Thread.currentThread().isInterrupted());
                          return null;
                        } finally {
                          Thread.interrupted();
                        }
                      });
              Thread caller = new Thread(invocation, "interrupted-spring-client");
              caller.start();
              try {
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                caller.interrupt();
                invocation.get(10, TimeUnit.SECONDS);
                assertThat(interrupted.get()).isTrue();
                assertThat(executor.evaluate("40 + 2").asInt()).isEqualTo(42);
                application.getSourceApplicationContext().close();
                assertThat(executor.isClosed()).isTrue();
              } finally {
                blocker.countDown();
                caller.join(TimeUnit.SECONDS.toMillis(10));
                assertThat(caller.isAlive()).isFalse();
              }
            });
  }

  @Test
  void springShutdownWaitsForClientResultConversion() throws Exception {
    writeClient("return 'hello'");
    CountDownLatch converting = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    HostAccess access =
        HostAccess.newBuilder(HostAccess.ALL)
            .targetTypeMapping(
                Value.class,
                String.class,
                Value::isString,
                value -> {
                  converting.countDown();
                  try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                      throw new AssertionError("Timed out waiting to finish conversion");
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted during conversion", e);
                  }
                  return value.asString();
                },
                HostAccess.TargetMappingPrecedence.HIGHEST)
            .build();
    withPython(true, access)
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              FailingPythonClient client = application.getBean(FailingPythonClient.class);
              PyExecutor executor = application.getBean(PyExecutor.class);
              FutureTask<String> invocation = new FutureTask<>(client::hello);
              CountDownLatch closing = new CountDownLatch(1);
              FutureTask<Void> shutdown =
                  new FutureTask<>(
                      () -> {
                        closing.countDown();
                        application.getSourceApplicationContext().close();
                        return null;
                      });
              Thread caller = new Thread(invocation, "spring-python-client");
              Thread closer = new Thread(shutdown, "spring-context-close");
              caller.start();
              try {
                assertThat(converting.await(10, TimeUnit.SECONDS)).isTrue();
                closer.start();
                assertThat(closing.await(10, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                ThreadInfo info;
                do {
                  info = ManagementFactory.getThreadMXBean().getThreadInfo(closer.threadId());
                  if (info != null
                      && info.getThreadState() == Thread.State.BLOCKED
                      && info.getLockOwnerId() == caller.threadId()) {
                    break;
                  }
                  assertThat(closer.isAlive()).as("shutdown must wait for conversion").isTrue();
                  Thread.yield();
                } while (System.nanoTime() < deadline);
                assertThat(info).isNotNull();
                assertThat(info.getThreadState()).isEqualTo(Thread.State.BLOCKED);
                assertThat(info.getLockOwnerId()).isEqualTo(caller.threadId());
                assertThat(executor.isClosed()).isFalse();
                release.countDown();
                assertThat(invocation.get(10, TimeUnit.SECONDS)).isEqualTo("hello");
                shutdown.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(client::hello).isInstanceOf(IllegalStateException.class);
              } finally {
                release.countDown();
                caller.join(TimeUnit.SECONDS.toMillis(10));
                closer.join(TimeUnit.SECONDS.toMillis(10));
                assertThat(caller.isAlive()).isFalse();
                assertThat(closer.isAlive()).isFalse();
              }
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

  private void writeClient(String body) throws IOException {
    Files.writeString(
        scripts.resolve("failing_python_client.py"),
        "class FailingPythonClient:\n    def hello(self):\n        " + body + "\n");
  }

  private ApplicationContextRunner withPython(boolean failFast) {
    return withPython(failFast, HostAccess.ALL);
  }

  private ApplicationContextRunner withPython(boolean failFast, HostAccess access) {
    SpringPolyglotContextFactory factory = mock(SpringPolyglotContextFactory.class);
    // The auto-configured executor owns this real context and must close it even on startup
    // failure.
    python = Context.newBuilder("python").allowAllAccess(true).allowHostAccess(access).build();
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
