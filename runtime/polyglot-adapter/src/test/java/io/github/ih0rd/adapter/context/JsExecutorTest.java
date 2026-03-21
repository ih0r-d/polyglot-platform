package io.github.ih0rd.adapter.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import io.github.ih0rd.adapter.exceptions.BindingException;
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

  @Spy @InjectMocks private JsExecutor executor;

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

    assertSame(result, executor.evaluate("hello", JsApi.class));
    assertSame(result, executor.evaluate("hello", JsApi.class));

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

    assertSame(result, executor.evaluate("hello", JsApi.class, "world"));
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
  void metadataIncludesLoadedInterfaces() {
    doReturn(source).when(executor).loadScript(eq(SupportedLanguage.JS), any());
    when(context.getBindings("js")).thenReturn(bindings);
    when(bindings.getMember("hello")).thenReturn(function);
    when(function.canExecute()).thenReturn(true);
    when(function.execute()).thenReturn(result);

    executor.evaluate("hello", JsApi.class);

    assertEquals(1, ((java.util.List<?>) executor.metadata().get("loadedInterfaces")).size());
  }

  @Test
  void createUsesPolyglotHelperContext() {
    Context createdContext = mock(Context.class);
    ScriptSource createdScriptSource = mock(ScriptSource.class);
    Consumer<Context.Builder> customizer = mock(Consumer.class);

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
}
