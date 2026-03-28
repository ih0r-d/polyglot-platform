package io.github.ih0rd.adapter.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.DummyApi;
import io.github.ih0rd.adapter.exceptions.EvaluationException;

class CommonUtilsTest {

  static class DummyImpl implements DummyApi {

    @Override
    public int add(int a, int b) {
      return a + b;
    }

    @Override
    public int ping() {
      return 0;
    }
  }

  @Test
  void shouldInvokeMethodSuccessfully() {
    DummyImpl impl = new DummyImpl();

    Value val = CommonUtils.invokeMethod(DummyApi.class, impl, "add", 2, 3);

    assertEquals(5, val.asInt());
  }

  @Test
  void shouldInvokeVoidMethod() {
    DummyImpl impl = new DummyImpl();

    Value val = CommonUtils.invokeMethod(DummyApi.class, impl, "ping");

    assertFalse(val.isNull());
  }

  @Test
  void shouldThrowIfTargetNull() {
    EvaluationException exception =
        assertThrows(
            EvaluationException.class,
            () -> CommonUtils.invokeMethod(DummyApi.class, null, "add", 1, 2));

    assertTrue(exception.getMessage().contains("Could not invoke method 'add'"));
  }

  @Test
  void shouldThrowIfMethodNotFound() {
    DummyImpl impl = new DummyImpl();

    EvaluationException exception =
        assertThrows(
            EvaluationException.class,
            () -> CommonUtils.invokeMethod(DummyApi.class, impl, "missing"));

    assertTrue(exception.getMessage().contains("Could not invoke method 'missing'"));
    assertNotNull(exception.getCause());
    assertTrue(exception.getCause() instanceof EvaluationException);
    assertTrue(exception.getCause().getMessage().contains("Method 'missing' not found"));
  }

  @Test
  void shouldCoercePrimitiveFromValue() {
    Value mockVal = mock(Value.class);
    when(mockVal.as(int.class)).thenReturn(99);

    Object coerced = invokeCoerce(int.class, mockVal);

    assertEquals(99, coerced);
  }

  @Test
  void shouldThrowOnNullPrimitiveArg() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> invokeCoerce(int.class, null));

    assertTrue(exception.getMessage().contains("Null passed for primitive"));
  }

  @Test
  void shouldReturnSameForNonPrimitive() {
    Object in = "abc";

    Object out = invokeCoerce(String.class, in);

    assertSame(in, out);
  }

  @Test
  void shouldCheckIfMethodExists() {
    assertTrue(CommonUtils.checkIfMethodExists(DummyApi.class, "add"));
    assertFalse(CommonUtils.checkIfMethodExists(DummyApi.class, "nope"));
  }

  @Test
  void shouldThrowIfNotInterface() {
    assertThrows(
        EvaluationException.class, () -> CommonUtils.checkIfMethodExists(DummyImpl.class, "x"));
  }

  @Test
  void shouldGetFirstElementOrNull() {
    String firstElement = CommonUtils.getFirstElement(Set.of("x", "y"));

    assertNotNull(firstElement);
  }

  @Test
  void shouldReturnNullForEmptySet() {
    assertNull(CommonUtils.getFirstElement(Set.of()));
  }

  @Test
  void shouldCacheMethodHandles() throws Throwable {
    var field = CommonUtils.class.getDeclaredField("HANDLE_CACHE");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<Object, Object> cache = (Map<Object, Object>) field.get(null);
    cache.clear();

    CommonUtils.invokeMethod(DummyApi.class, new DummyImpl(), "add", 1, 2);

    assertEquals(1, cache.size());
  }

  @Test
  void shouldWrapExceptionFromHandle() {
    DummyApi bad =
        new DummyApi() {
          @Override
          public int add(int a, int b) {
            throw new RuntimeException("boom");
          }

          @Override
          public int ping() {
            return 0;
          }
        };

    EvaluationException exception =
        assertThrows(
            EvaluationException.class,
            () -> CommonUtils.invokeMethod(DummyApi.class, bad, "add", 1, 2));

    assertTrue(exception.getMessage().contains("Could not invoke method"));
  }

  @Test
  void shouldNotThrowWhenInvokingExistingMethod() {
    assertDoesNotThrow(() -> CommonUtils.invokeMethod(DummyApi.class, new DummyImpl(), "ping"));
  }

  private static Object invokeCoerce(Class<?> type, Object arg) {
    try {
      var method =
          CommonUtils.class.getDeclaredMethod("coercePrimitive", Class.class, Object.class);
      method.setAccessible(true);
      return method.invoke(null, type, arg);
    } catch (Exception e) {
      if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new RuntimeException(e);
    }
  }
}
