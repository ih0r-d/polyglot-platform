package io.github.ih0rd.polyglot.annotations.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

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

  @Test
  void providerBasedFacadeDoesNotResolveExecutorsUntilUsed() {
    AtomicBoolean accessed = new AtomicBoolean(false);
    PolyglotExecutors executors =
        PolyglotExecutors.fromProviders(
            trackingProvider(mock(PyExecutor.class), accessed),
            trackingProvider((JsExecutor) null, new AtomicBoolean(false)));

    assertFalse(accessed.get());

    executors.python();

    assertTrue(accessed.get());
  }

  private static <T> ObjectProvider<T> trackingProvider(T instance, AtomicBoolean accessed) {
    return new ObjectProvider<>() {
      @Override
      public T getObject(Object... args) {
        accessed.set(true);
        return instance;
      }

      @Override
      public T getIfAvailable() {
        accessed.set(true);
        return instance;
      }

      @Override
      public T getIfUnique() {
        accessed.set(true);
        return instance;
      }

      @Override
      public T getObject() {
        accessed.set(true);
        return instance;
      }
    };
  }
}
