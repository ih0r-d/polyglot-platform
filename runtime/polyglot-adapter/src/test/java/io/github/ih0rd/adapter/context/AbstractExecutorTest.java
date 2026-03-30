package io.github.ih0rd.adapter.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.EvaluationException;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.adapter.exceptions.ScriptNotFoundException;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

@SuppressWarnings({"unchecked"})
class AbstractExecutorTest {

  static class TestExecutor extends AbstractPolyglotExecutor {

    TestExecutor(Context ctx) {
      super(ctx, mock(ScriptSource.class));
    }

    @Override
    public String languageId() {
      return "python";
    }

    // critical: avoid Graal init
    @Override
    protected Source loadScript(SupportedLanguage language, String name) {
      return mock(Source.class);
    }

    @Override
    protected <T> Value evaluate(
        Convention convention, String methodName, Class<T> target, Object... args) {
      Value v = mock(Value.class);
      when(v.isNull()).thenReturn(false);
      when(v.as(any(Class.class))).thenAnswer(inv -> "ok");
      return v;
    }
  }

  @Test
  void bindCallsEvaluate() {
    Context ctx = mock(Context.class);
    TestExecutor exec = spy(new TestExecutor(ctx));

    interface Api {
      String hello();
    }

    Api api = exec.bind(Api.class);
    assertEquals("ok", api.hello());

    verify(exec).evaluate(eq(Convention.DEFAULT), eq("hello"), eq(Api.class), any(Object[].class));
  }

  @Test
  void bindReturnsNullWhenValueIsNull() {
    Context ctx = mock(Context.class);
    TestExecutor exec = spy(new TestExecutor(ctx));

    Value nullValue = mock(Value.class);
    when(nullValue.isNull()).thenReturn(true);

    doReturn(nullValue)
        .when(exec)
        .evaluate(eq(Convention.DEFAULT), eq("hello"), any(), any(Object[].class));

    interface Api {
      Object hello();
    }

    Api api = exec.bind(Api.class);
    assertNull(api.hello());
  }

  @Test
  void bindUsesEmptyArgumentsForNoArgMethods() {
    Context ctx = mock(Context.class);
    TestExecutor exec = spy(new TestExecutor(ctx));

    interface Api {
      String hello();
    }

    Api api = exec.bind(Api.class);

    assertEquals("ok", api.hello());
  }

  @Test
  void bindUsesProvidedConvention() {
    Context ctx = mock(Context.class);
    TestExecutor exec = spy(new TestExecutor(ctx));

    interface Api {
      String hello();
    }

    Api api = exec.bind(Api.class, Convention.BY_METHOD_NAME);

    assertEquals("ok", api.hello());
    verify(exec)
        .evaluate(eq(Convention.BY_METHOD_NAME), eq("hello"), eq(Api.class), any(Object[].class));
  }

  @Test
  void bindDelegatesObjectMethodsToExecutorInstance() {
    Context ctx = mock(Context.class);
    TestExecutor exec = new TestExecutor(ctx);

    interface Api {
      String hello();
    }

    Api api = exec.bind(Api.class);

    assertTrue(api.toString().contains(TestExecutor.class.getSimpleName()));
  }

  @Test
  void callFunctionExecutes() {
    Context ctx = mock(Context.class);
    Value bindings = mock(Value.class);
    Value fn = mock(Value.class);

    when(ctx.getBindings("python")).thenReturn(bindings);
    when(bindings.getMember("foo")).thenReturn(fn);
    when(fn.canExecute()).thenReturn(true);

    TestExecutor exec = new TestExecutor(ctx);
    exec.callFunction("foo", 1, 2);

    verify(fn).execute(1, 2);
  }

  @Test
  void callFunctionMissingThrows() {
    Context ctx = mock(Context.class);
    Value bindings = mock(Value.class);

    when(ctx.getBindings("python")).thenReturn(bindings);
    when(bindings.getMember("foo")).thenReturn(null);

    TestExecutor exec = new TestExecutor(ctx);

    assertThrows(BindingException.class, () -> exec.callFunction("foo"));
  }

  @Test
  void evaluateInlineWrappedException() {
    Context ctx = mock(Context.class);
    TestExecutor exec = new TestExecutor(ctx);

    // real evaluate(String) runs, but Context.eval explodes
    when(ctx.eval(any(Source.class))).thenThrow(new RuntimeException("boom"));

    assertThrows(InvocationException.class, () -> exec.evaluate("x=1"));
  }

  @Test
  void evaluateInlineReturnsContextResult() {
    Context ctx = mock(Context.class);
    TestExecutor exec = new TestExecutor(ctx);
    Value value = mock(Value.class);

    when(ctx.eval(any(Source.class))).thenReturn(value);

    assertSame(value, exec.evaluate("x=1"));
  }

  @Test
  void closeClosesContext() {
    Context ctx = mock(Context.class);
    TestExecutor exec = new TestExecutor(ctx);

    exec.close();

    verify(ctx).close();
  }

  @Test
  void constructorRejectsNullArguments() {
    assertThrows(IllegalArgumentException.class, () -> new TestExecutor(null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AbstractPolyglotExecutor(mock(Context.class), null) {
              @Override
              protected String languageId() {
                return "python";
              }

              @Override
              protected <T> Value evaluate(
                  Convention convention,
                  String methodName,
                  Class<T> memberTargetType,
                  Object... args) {
                return mock(Value.class);
              }
            });
  }

  @Test
  void bindRejectsNullInterface() {
    TestExecutor exec = new TestExecutor(mock(Context.class));

    assertThrows(IllegalArgumentException.class, () -> exec.bind(null));
  }

  @Test
  void validateBindingRejectsNullInterface() {
    TestExecutor exec = new TestExecutor(mock(Context.class));

    assertThrows(IllegalArgumentException.class, () -> exec.validateBinding(null));
    assertThrows(NullPointerException.class, () -> exec.validateBinding(Runnable.class, null));
  }

  @Test
  void bindRejectsNullConvention() {
    TestExecutor exec = new TestExecutor(mock(Context.class));

    assertThrows(NullPointerException.class, () -> exec.bind(Runnable.class, null));
  }

  @Test
  void validateBindingThrowsUnsupportedOperationForBaseImplementation() {
    TestExecutor exec = new TestExecutor(mock(Context.class));

    assertThrows(UnsupportedOperationException.class, () -> exec.validateBinding(Runnable.class));
    assertThrows(
        UnsupportedOperationException.class,
        () -> exec.validateBinding(Runnable.class, Convention.BY_METHOD_NAME));
  }

  @Test
  void callFunctionWrapsExecutionErrors() {
    Context ctx = mock(Context.class);
    Value bindings = mock(Value.class);
    Value fn = mock(Value.class);

    when(ctx.getBindings("python")).thenReturn(bindings);
    when(bindings.getMember("foo")).thenReturn(fn);
    when(fn.canExecute()).thenReturn(true);
    when(fn.execute()).thenThrow(new IllegalStateException("boom"));

    TestExecutor exec = new TestExecutor(ctx);

    assertThrows(InvocationException.class, () -> exec.callFunction("foo"));
  }

  @Test
  void loadScriptBuildsSourceFromScriptSource() throws Exception {
    Context ctx = mock(Context.class);
    ScriptSource scriptSource = mock(ScriptSource.class);
    when(scriptSource.exists(SupportedLanguage.PYTHON, "demo")).thenReturn(true);
    when(scriptSource.open(SupportedLanguage.PYTHON, "demo")).thenReturn(new StringReader("x = 1"));

    AbstractPolyglotExecutor exec =
        new AbstractPolyglotExecutor(ctx, scriptSource) {
          @Override
          protected String languageId() {
            return "python";
          }

          @Override
          protected <T> Value evaluate(
              Convention convention, String methodName, Class<T> memberTargetType, Object... args) {
            return mock(Value.class);
          }
        };

    Source source = exec.loadScript(SupportedLanguage.PYTHON, "demo");

    assertEquals("demo", source.getName());
    assertEquals("python", source.getLanguage());
  }

  @Test
  void loadScriptThrowsWhenScriptDoesNotExist() {
    Context ctx = mock(Context.class);
    ScriptSource scriptSource = mock(ScriptSource.class);
    when(scriptSource.exists(SupportedLanguage.PYTHON, "missing")).thenReturn(false);

    AbstractPolyglotExecutor exec =
        new AbstractPolyglotExecutor(ctx, scriptSource) {
          @Override
          protected String languageId() {
            return "python";
          }

          @Override
          protected <T> Value evaluate(
              Convention convention, String methodName, Class<T> memberTargetType, Object... args) {
            return mock(Value.class);
          }
        };

    assertThrows(
        ScriptNotFoundException.class, () -> exec.loadScript(SupportedLanguage.PYTHON, "missing"));
  }

  @Test
  void loadScriptWrapsIoExceptions() throws Exception {
    Context ctx = mock(Context.class);
    ScriptSource scriptSource = mock(ScriptSource.class);
    when(scriptSource.exists(SupportedLanguage.PYTHON, "broken")).thenReturn(true);
    when(scriptSource.open(SupportedLanguage.PYTHON, "broken"))
        .thenThrow(new IOException("disk error"));

    AbstractPolyglotExecutor exec =
        new AbstractPolyglotExecutor(ctx, scriptSource) {
          @Override
          protected String languageId() {
            return "python";
          }

          @Override
          protected <T> Value evaluate(
              Convention convention, String methodName, Class<T> memberTargetType, Object... args) {
            return mock(Value.class);
          }
        };

    assertThrows(
        EvaluationException.class, () -> exec.loadScript(SupportedLanguage.PYTHON, "broken"));
  }

  @Test
  void clearAllCachesClearsSourceCacheAndMetadataReflectsIt() {
    TestExecutor exec = new TestExecutor(mock(Context.class));
    exec.sourceCache.put(Runnable.class, mock(Source.class));

    assertEquals(1, exec.metadata().get("sourceCacheSize"));

    exec.clearAllCaches();

    assertEquals(
        Map.of(
            "executorType",
            exec.getClass().getName(),
            "languageId",
            "python",
            "sourceCacheSize",
            0),
        exec.metadata());
  }
}
