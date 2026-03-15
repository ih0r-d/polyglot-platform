package io.github.ih0rd.adapter.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
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
    assertThat(val.asInt()).isEqualTo(5);
  }

  @Test
  void shouldInvokeVoidMethod() {
    DummyImpl impl = new DummyImpl();
    Value val = CommonUtils.invokeMethod(DummyApi.class, impl, "ping");
    assertThat(val.isNull()).isFalse();
  }

  @Test
  void shouldThrowIfTargetNull() {
    assertThatThrownBy(() -> CommonUtils.invokeMethod(DummyApi.class, null, "add", 1, 2))
        .isInstanceOf(EvaluationException.class)
        .hasMessageContaining("Could not invoke method 'add'");
  }

  @Test
  void shouldThrowIfMethodNotFound() {
    DummyImpl impl = new DummyImpl();

    assertThatThrownBy(() -> CommonUtils.invokeMethod(DummyApi.class, impl, "missing"))
        .isInstanceOf(EvaluationException.class)
        .hasMessageContaining("Could not invoke method 'missing'")
        .hasCauseInstanceOf(EvaluationException.class)
        .cause()
        .hasMessageContaining("Method 'missing' not found");
  }

  @Test
  void shouldCoercePrimitiveFromValue() {
    Value mockVal = mock(Value.class);
    when(mockVal.as(int.class)).thenReturn(99);
    Object coerced = invokeCoerce(int.class, mockVal);
    assertThat(coerced).isEqualTo(99);
  }

  @Test
  void shouldThrowOnNullPrimitiveArg() {
    assertThatThrownBy(() -> invokeCoerce(int.class, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Null passed for primitive");
  }

  @Test
  void shouldReturnSameForNonPrimitive() {
    Object in = "abc";
    Object out = invokeCoerce(String.class, in);
    assertThat(out).isSameAs(in);
  }

  @Test
  void shouldCheckIfMethodExists() {
    assertThat(CommonUtils.checkIfMethodExists(DummyApi.class, "add")).isTrue();
    assertThat(CommonUtils.checkIfMethodExists(DummyApi.class, "nope")).isFalse();
  }

  @Test
  void shouldThrowIfNotInterface() {
    assertThatThrownBy(() -> CommonUtils.checkIfMethodExists(DummyImpl.class, "x"))
        .isInstanceOf(EvaluationException.class);
  }

  @Test
  void shouldGetFirstElementOrNull() {
    String firstElement = CommonUtils.getFirstElement(Set.of("x", "y"));
    assertThat(firstElement).isNotNull();
  }

  @Test
  void shouldCacheMethodHandles() throws Throwable {
    Method m = DummyApi.class.getMethod("add", int.class, int.class);
    var field = CommonUtils.class.getDeclaredField("HANDLE_CACHE");
    field.setAccessible(true);
    var cache = (java.util.Map<?, ?>) field.get(null);
    cache.clear();
    CommonUtils.invokeMethod(DummyApi.class, new DummyImpl(), "add", 1, 2);
    assertThat(cache.keySet()).anyMatch(k -> k.equals(m));
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
    assertThatThrownBy(() -> CommonUtils.invokeMethod(DummyApi.class, bad, "add", 1, 2))
        .isInstanceOf(EvaluationException.class)
        .hasMessageContaining("Could not invoke method");
  }

  private static Object invokeCoerce(Class<?> type, Object arg) {
    try {
      var m = CommonUtils.class.getDeclaredMethod("coercePrimitive", Class.class, Object.class);
      m.setAccessible(true);
      return m.invoke(null, type, arg);
    } catch (Exception e) {
      if (e.getCause() != null) throw (RuntimeException) e.getCause();
      throw new RuntimeException(e);
    }
  }
}
