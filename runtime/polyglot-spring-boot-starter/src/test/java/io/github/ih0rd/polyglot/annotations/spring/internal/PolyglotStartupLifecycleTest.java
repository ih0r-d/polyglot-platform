package io.github.ih0rd.polyglot.annotations.spring.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

class PolyglotStartupLifecycleTest {

  @Mock private PyExecutor pyExecutor;
  @Mock private JsExecutor jsExecutor;

  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  void startWarmsUpEnabledExecutorsAndMarksLifecycleRunning() {
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(enabledProperties(true, true, true), pyExecutor, jsExecutor);

    lifecycle.start();

    verify(pyExecutor).evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    verify(jsExecutor).evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    assertEquals(Integer.MAX_VALUE, lifecycle.getPhase());
    assertEquals(true, lifecycle.isRunning());
  }

  @Test
  void startSkipsWarmupWhenCoreIsDisabled() {
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(enabledProperties(false, true, false), pyExecutor, null);

    lifecycle.start();

    verify(pyExecutor, never()).evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    assertFalse(lifecycle.isRunning());
  }

  @Test
  void startWrapsWarmupFailureWhenFailFastIsEnabled() {
    org.mockito.Mockito.doThrow(new RuntimeException("boom"))
        .when(pyExecutor)
        .evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(enabledProperties(true, true, false), pyExecutor, null);

    assertThrows(IllegalStateException.class, lifecycle::start);
  }

  @Test
  void startSuppressesWarmupFailureWhenFailFastIsDisabled() {
    org.mockito.Mockito.doThrow(new RuntimeException("boom"))
        .when(pyExecutor)
        .evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(
            new PolyglotProperties(
                new PolyglotProperties.CoreProperties(true, false, true, "info"),
                new PolyglotProperties.PythonProperties(
                    true, "classpath:/python", true, true, java.util.List.of()),
                null,
                null,
                null),
            pyExecutor,
            null);

    assertDoesNotThrow(lifecycle::start);
    assertFalse(lifecycle.isRunning());
  }

  private static PolyglotProperties enabledProperties(
      boolean coreEnabled, boolean pyWarmup, boolean jsWarmup) {
    return new PolyglotProperties(
        new PolyglotProperties.CoreProperties(coreEnabled, true, true, "info"),
        new PolyglotProperties.PythonProperties(
            true, "classpath:/python", true, pyWarmup, java.util.List.of("demo")),
        new PolyglotProperties.JsProperties(true, "classpath:/js", jsWarmup, java.util.List.of()),
        null,
        null);
  }
}
