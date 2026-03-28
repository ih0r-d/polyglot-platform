package io.github.ih0rd.polyglot.annotations.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;

class PolyglotExecutorsTest {

  @Test
  void exposesConfiguredExecutorsAndMetadata() {
    PyExecutor pyExecutor = mock(PyExecutor.class);
    JsExecutor jsExecutor = mock(JsExecutor.class);
    when(pyExecutor.metadata()).thenReturn(Map.of("engine", "python"));
    when(jsExecutor.metadata()).thenReturn(Map.of("engine", "js"));

    PolyglotExecutors executors = new PolyglotExecutors(pyExecutor, jsExecutor);

    assertSame(pyExecutor, executors.python().orElseThrow());
    assertSame(jsExecutor, executors.js().orElseThrow());
    assertSame(pyExecutor, executors.requirePython());
    assertSame(jsExecutor, executors.requireJs());
    assertTrue(executors.isPythonEnabled());
    assertTrue(executors.isJsEnabled());
    assertEquals("python", ((Map<?, ?>) executors.metadata().get("python")).get("engine"));
    assertEquals("js", ((Map<?, ?>) executors.metadata().get("js")).get("engine"));
  }

  @Test
  void throwsWhenExecutorIsMissing() {
    PolyglotExecutors executors = new PolyglotExecutors(null, null);

    assertTrue(executors.python().isEmpty());
    assertTrue(executors.js().isEmpty());
    assertFalse(executors.isPythonEnabled());
    assertFalse(executors.isJsEnabled());
    assertTrue(executors.metadata().isEmpty());
    assertThrows(IllegalStateException.class, executors::requirePython);
    assertThrows(IllegalStateException.class, executors::requireJs);
  }
}
