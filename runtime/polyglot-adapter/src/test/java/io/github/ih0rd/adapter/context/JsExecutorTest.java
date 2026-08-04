package io.github.ih0rd.adapter.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

class JsExecutorTest {

  interface JsApi {
    String hello();
  }

  @Mock private Context context;
  @Mock private ScriptSource scriptSource;
  @Mock private Source source;
  @Mock private Value bindings;
  @Mock private Value function;
  @Mock private Value result;

  private JsExecutor executor;

  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    executor = spy(new JsExecutor(context, scriptSource));
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  void languageIdReturnsJs() {
    assertEquals("js", executor.languageId());
  }

  @Test
  void evaluateLoadsModuleOnceAndCallsFunction() {
    doReturn(source).when(executor).loadScript(SupportedLanguage.JS, "js_api");
    when(context.getBindings("js")).thenReturn(bindings);
    when(bindings.getMember("hello")).thenReturn(function);
    when(function.canExecute()).thenReturn(true);
    when(function.execute()).thenReturn(result);

    assertSame(result, executor.evaluate(Convention.DEFAULT, "hello", JsApi.class));
    assertSame(result, executor.evaluate(Convention.DEFAULT, "hello", JsApi.class));

    verify(context).eval(source);
    verify(executor).loadScript(SupportedLanguage.JS, "js_api");
  }

  @Test
  void evaluateWithArgsLoadsModuleAndCallsFunction() {
    doReturn(source).when(executor).loadScript(SupportedLanguage.JS, "js_api");
    when(context.getBindings("js")).thenReturn(bindings);
    when(bindings.getMember("hello")).thenReturn(function);
    when(function.canExecute()).thenReturn(true);
    when(function.execute("world")).thenReturn(result);

    assertSame(result, executor.evaluate(Convention.DEFAULT, "hello", JsApi.class, "world"));
  }

  @Test
  void validateBindingFailsWhenFunctionIsMissing() {
    doReturn(source).when(executor).loadScript(SupportedLanguage.JS, "js_api");
    when(context.getBindings("js")).thenReturn(bindings);
    when(bindings.getMember("hello")).thenReturn(null);

    assertThrows(BindingException.class, () -> executor.validateBinding(JsApi.class));
  }

  @Test
  void validateBindingSkipsObjectMethods() {
    doReturn(source).when(executor).loadScript(SupportedLanguage.JS, "js_api");
    when(context.getBindings("js")).thenReturn(bindings);
    when(bindings.getMember("hello")).thenReturn(function);
    when(function.canExecute()).thenReturn(true);

    executor.validateBinding(JsApi.class);

    verify(bindings, never()).getMember("toString");
  }

  @Test
  void validateBindingRejectsNullInterface() {
    assertThrows(IllegalArgumentException.class, () -> executor.validateBinding(null));
  }

  @Test
  void byInterfaceExportConventionIsRejected() {
    assertThrows(
        BindingException.class,
        () -> executor.validateBinding(JsApi.class, Convention.BY_INTERFACE_EXPORT));
    assertThrows(
        BindingException.class,
        () -> executor.evaluate(Convention.BY_INTERFACE_EXPORT, "hello", JsApi.class));
  }

  @Test
  void metadataIncludesLoadedInterfaces() {
    doReturn(source).when(executor).loadScript(eq(SupportedLanguage.JS), any());
    when(context.getBindings("js")).thenReturn(bindings);
    when(bindings.getMember("hello")).thenReturn(function);
    when(function.canExecute()).thenReturn(true);
    when(function.execute()).thenReturn(result);

    executor.evaluate(Convention.DEFAULT, "hello", JsApi.class);

    assertEquals(1, ((java.util.List<?>) executor.metadata().get("loadedInterfaces")).size());
  }

  @Test
  void createUsesPolyglotHelperContext() {
    Context createdContext = mock(Context.class);
    ScriptSource createdScriptSource = mock(ScriptSource.class);
    @SuppressWarnings("unchecked")
    Consumer<Context.Builder> customizer = (Consumer<Context.Builder>) mock(Consumer.class);

    try (MockedStatic<PolyglotHelper> polyglotHelper = mockStatic(PolyglotHelper.class)) {
      polyglotHelper
          .when(() -> PolyglotHelper.newContext(SupportedLanguage.JS, customizer))
          .thenReturn(createdContext);

      JsExecutor created = JsExecutor.create(createdScriptSource, customizer);

      assertSame(createdContext, created.context);
      assertSame(createdScriptSource, created.scriptSource);
    }
  }

  @Test
  void createWithContextUsesProvidedContext() {
    Context createdContext = mock(Context.class);
    ScriptSource createdScriptSource = mock(ScriptSource.class);

    JsExecutor created = JsExecutor.createWithContext(createdContext, createdScriptSource);

    assertSame(createdContext, created.context);
    assertSame(createdScriptSource, created.scriptSource);
  }

  @Test
  void internallyCreatedContextIsClosedWithExecutor() {
    Context createdContext = mock(Context.class);
    ScriptSource createdScriptSource = mock(ScriptSource.class);
    @SuppressWarnings("unchecked")
    Consumer<Context.Builder> customizer = (Consumer<Context.Builder>) mock(Consumer.class);

    try (MockedStatic<PolyglotHelper> polyglotHelper = mockStatic(PolyglotHelper.class)) {
      polyglotHelper
          .when(() -> PolyglotHelper.newContext(SupportedLanguage.JS, customizer))
          .thenReturn(createdContext);

      JsExecutor created = JsExecutor.create(createdScriptSource, customizer);

      created.close();

      verify(createdContext).close();
    }
  }

  @Test
  void createWithContextKeepsPreviousOwnedContextCloseSemantics() {
    Context createdContext = mock(Context.class);
    ScriptSource createdScriptSource = mock(ScriptSource.class);

    JsExecutor created = JsExecutor.createWithContext(createdContext, createdScriptSource);

    created.close();

    verify(createdContext).close();
  }

  @Test
  void callerProvidedOwnedContextIsClosedWithExecutor() {
    Context createdContext = mock(Context.class);
    ScriptSource createdScriptSource = mock(ScriptSource.class);

    JsExecutor created =
        JsExecutor.createWithContext(
            createdContext, createdScriptSource, ContextOwnership.OWNED_BY_EXECUTOR);

    created.close();

    verify(createdContext).close();
  }

  @Test
  void callerProvidedBorrowedContextIsNotClosedWithExecutor() {
    Context createdContext = mock(Context.class);
    ScriptSource createdScriptSource = mock(ScriptSource.class);

    JsExecutor created =
        JsExecutor.createWithContext(
            createdContext, createdScriptSource, ContextOwnership.BORROWED);

    created.close();

    verify(createdContext, never()).close();
  }

  @Test
  void borrowedContextRemainsAvailableToAnotherExecutorAfterClose() {
    Context sharedContext = mock(Context.class);
    Value value = mock(Value.class);
    when(sharedContext.eval(any(Source.class))).thenReturn(value);
    ScriptSource createdScriptSource = mock(ScriptSource.class);
    JsExecutor first =
        JsExecutor.createWithContext(sharedContext, createdScriptSource, ContextOwnership.BORROWED);
    JsExecutor second =
        JsExecutor.createWithContext(sharedContext, createdScriptSource, ContextOwnership.BORROWED);

    first.close();

    assertFalse(second.isClosed());
    assertSame(value, second.evaluate("1 + 1"));
    verify(sharedContext, never()).close();
  }

  @Test
  void closeIsIdempotent() {
    Context createdContext = mock(Context.class);
    JsExecutor created = new JsExecutor(createdContext, mock(ScriptSource.class));

    created.close();
    created.close();

    verify(createdContext).close();
  }

  @Test
  void operationsFailAfterClose() {
    JsExecutor created = new JsExecutor(mock(Context.class), mock(ScriptSource.class));

    created.close();

    assertThrows(IllegalStateException.class, () -> created.evaluate("1 + 1"));
  }

  @Test
  void closeClearsJavaScriptExecutorCaches() {
    JsExecutor created = new JsExecutor(mock(Context.class), mock(ScriptSource.class));
    created.sourceCache.put(JsApi.class, mock(Source.class));

    created.close();

    assertTrue(created.sourceCache.isEmpty());
  }

  @Test
  void preloadScriptDoesNotPopulateInterfaceSourceCache() {
    doReturn(source).when(executor).loadScript(SupportedLanguage.JS, "bootstrap");

    executor.preloadScript("bootstrap");

    assertEquals(0, executor.sourceCache.size());
    verify(context).eval(source);
  }

  @Test
  void preloadScriptWrapsPolyglotExceptionAsInvocationException() throws Exception {
    Context ctx = mock(Context.class);
    ScriptSource ss = mock(ScriptSource.class);
    when(ss.exists(eq(SupportedLanguage.JS), eq("failing"))).thenReturn(true);
    when(ss.open(eq(SupportedLanguage.JS), eq("failing")))
        .thenReturn(new StringReader("var x = 1;"));
    JsExecutor exec = new JsExecutor(ctx, ss);

    PolyglotException polyglotEx = mock(PolyglotException.class);
    when(polyglotEx.isInterrupted()).thenReturn(false);
    when(ctx.eval(any(Source.class))).thenThrow(polyglotEx);

    InvocationException thrown =
        assertThrows(InvocationException.class, () -> exec.preloadScript("failing"));
    assertSame(polyglotEx, thrown.getCause());
  }

  @Test
  void preloadScriptRestoresInterruptFlagWhenPolyglotExceptionIsInterrupted() throws Exception {
    Context ctx = mock(Context.class);
    ScriptSource ss = mock(ScriptSource.class);
    when(ss.exists(eq(SupportedLanguage.JS), eq("failing"))).thenReturn(true);
    when(ss.open(eq(SupportedLanguage.JS), eq("failing")))
        .thenReturn(new StringReader("var x = 1;"));
    JsExecutor exec = new JsExecutor(ctx, ss);

    PolyglotException polyglotEx = mock(PolyglotException.class);
    when(polyglotEx.isInterrupted()).thenReturn(true);
    when(ctx.eval(any(Source.class))).thenThrow(polyglotEx);

    assertThrows(InvocationException.class, () -> exec.preloadScript("failing"));
    assertTrue(Thread.interrupted()); // clears the flag after asserting
  }
}
