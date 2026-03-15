package io.github.ih0rd.polyglot.annotations.spring.actuator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

class PolyglotHealthIndicatorTest {

  @Mock private PyExecutor pyExecutor;

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
  void healthIsUnknownWhenCoreIsDisabled() {
    PolyglotHealthIndicator indicator =
        new PolyglotHealthIndicator(new PolyglotExecutors(null, null), disabledCore());

    assertEquals("UNKNOWN", indicator.health().getStatus().getCode());
  }

  @Test
  void healthIsUpWhenAnyExecutorIsAvailable() {
    PolyglotHealthIndicator indicator =
        new PolyglotHealthIndicator(new PolyglotExecutors(pyExecutor, null), defaults());

    assertEquals("UP", indicator.health().getStatus().getCode());
  }

  @Test
  void healthIsDownWhenCoreEnabledAndNoExecutorsExist() {
    PolyglotHealthIndicator indicator =
        new PolyglotHealthIndicator(new PolyglotExecutors(null, null), defaults());

    assertEquals("DOWN", indicator.health().getStatus().getCode());
    assertEquals("No polyglot executors available", indicator.health().getDetails().get("reason"));
  }

  private static PolyglotProperties defaults() {
    return new PolyglotProperties(null, null, null, null, null);
  }

  private static PolyglotProperties disabledCore() {
    return new PolyglotProperties(
        new PolyglotProperties.CoreProperties(false, true, true, "info"), null, null, null, null);
  }
}
