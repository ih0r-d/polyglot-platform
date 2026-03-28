package io.github.ih0rd.adapter.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

@SuppressWarnings({"unchecked"})
class PyExecutorTest {

  interface Api {
    String hello(String arg);
  }

  private ScriptSource mockScriptSource() throws Exception {
    ScriptSource ss = mock(ScriptSource.class);
    when(ss.exists(eq(SupportedLanguage.PYTHON), any())).thenReturn(true);

    when(ss.open(eq(SupportedLanguage.PYTHON), any())).thenReturn(mock(java.io.Reader.class));

    return ss;
  }

  private PyExecutor newExec(Context ctx) throws Exception {
    return new PyExecutor(ctx, mockScriptSource());
  }

  private void callResolveInstance(PyExecutor exec) {
    try {
      Method m = PyExecutor.class.getDeclaredMethod("resolveInstance", Class.class);
      m.setAccessible(true);
      m.invoke(exec, (Class<?>) Api.class);
    } catch (Exception e) {
      Throwable c = e.getCause();
      if (c instanceof RuntimeException r) throw r;
      throw new RuntimeException(e);
    }
  }

  private Value callResolveClass(PyExecutor exec, Class<?> iface) {
    try {
      Method m = PyExecutor.class.getDeclaredMethod("resolveClass", Class.class);
      m.setAccessible(true);
      return (Value) m.invoke(exec, iface);
    } catch (Exception e) {
      Throwable c = e.getCause();
      if (c instanceof RuntimeException r) throw r;
      throw new RuntimeException(e);
    }
  }

  private Value callInvokeMember(PyExecutor exec, Value target, String name, Object... args) {
    try {
      Method m =
          PyExecutor.class.getDeclaredMethod(
              "invokeMember", Value.class, String.class, Object[].class);
      m.setAccessible(true);
      return (Value) m.invoke(exec, target, name, args);
    } catch (Exception e) {
      Throwable c = e.getCause();
      if (c instanceof RuntimeException r) throw r;
      throw new RuntimeException(e);
    }
  }

  @Test
  void languageId_returnsPython() throws Exception {
    PyExecutor exec = newExec(mock(Context.class));
    assertEquals("python", exec.languageId());
  }

  @Test
  void evaluateWithArgs_usesCachedInstance() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = newExec(ctx);

    Value instance = mock(Value.class);
    Value member = mock(Value.class);
    Value result = mock(Value.class);

    when(instance.isNull()).thenReturn(false);
    when(instance.getMember("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(true);
    when(member.execute("x")).thenReturn(result);
    when(instance.hasMember("hello")).thenReturn(true);

    Field f = PyExecutor.class.getDeclaredField("instanceCache");
    f.setAccessible(true);
    Map<Class<?>, WeakReference<Value>> cache = (Map<Class<?>, WeakReference<Value>>) f.get(exec);
    cache.put(Api.class, new WeakReference<>(instance));

    Value out = exec.evaluate(Convention.DEFAULT, "hello", Api.class, "x");
    assertSame(result, out);
  }

  @Test
  void evaluateByMethodNameUsesBindingsDirectly() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);
    Value polyBindings = mock(Value.class);
    Value fn = mock(Value.class);
    Value result = mock(Value.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "api");
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(polyBindings);
    when(polyBindings.getMember("hello")).thenReturn(fn);
    when(fn.canExecute()).thenReturn(true);
    when(fn.execute("x")).thenReturn(result);

    assertSame(result, exec.evaluate(Convention.BY_METHOD_NAME, "hello", Api.class, "x"));
  }

  @Test
  void resolveInstance_executeThrows_wrapped() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));

    doReturn(mock(Source.class)).when(exec).loadScript(eq(SupportedLanguage.PYTHON), any());

    when(ctx.eval(any(Source.class))).thenReturn(mock(Value.class));

    Value poly = mock(Value.class);
    Value pyClass = mock(Value.class);
    when(ctx.getPolyglotBindings()).thenReturn(poly);
    when(poly.getMember("Api")).thenReturn(pyClass);
    when(pyClass.canExecute()).thenReturn(true);
    when(pyClass.execute()).thenThrow(new RuntimeException("boom"));

    assertThrows(InvocationException.class, () -> callResolveInstance(exec));
  }

  @Test
  void resolveClass_fromPolyglotBindings() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = newExec(ctx);

    Value poly = mock(Value.class);
    Value cls = mock(Value.class);

    when(ctx.getPolyglotBindings()).thenReturn(poly);
    when(poly.getMember("Api")).thenReturn(cls);

    assertSame(cls, callResolveClass(exec, Api.class));
  }

  @Test
  void resolveClass_notFound_throws() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = newExec(ctx);

    Value poly = mock(Value.class);
    when(ctx.getPolyglotBindings()).thenReturn(poly);
    when(poly.getMember("Api")).thenReturn(null);

    Value lang = mock(Value.class);
    when(ctx.getBindings("python")).thenReturn(lang);
    when(lang.getMember("Api")).thenReturn(null);

    assertThrows(BindingException.class, () -> callResolveClass(exec, Api.class));
  }

  @Test
  void invokeMember_executeThrows_wrapped() throws Exception {
    PyExecutor exec = newExec(mock(Context.class));
    Value target = mock(Value.class);
    Value member = mock(Value.class);

    when(target.isNull()).thenReturn(false);
    when(target.getMember("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(true);
    when(member.execute(any())).thenThrow(new RuntimeException("err"));

    assertThrows(
        io.github.ih0rd.adapter.exceptions.BindingException.class,
        () -> callInvokeMember(exec, target, "hello", "x"));
  }

  @Test
  void resolveClass_fallsBackToLanguageBindings() {
    Context ctx = mock(Context.class);
    PyExecutor exec = new PyExecutor(ctx, mock(ScriptSource.class));

    Value poly = mock(Value.class);
    Value bindings = mock(Value.class);
    Value type = mock(Value.class);
    when(ctx.getPolyglotBindings()).thenReturn(poly);
    when(poly.getMember("Api")).thenReturn(null);
    when(ctx.getBindings("python")).thenReturn(bindings);
    when(bindings.getMember("Api")).thenReturn(type);

    assertSame(type, callResolveClass(exec, Api.class));
  }

  @Test
  void resolveInstance_usesObjectStyleExportWithoutInstantiation() {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(new PyExecutor(ctx, mock(ScriptSource.class)));
    Source source = mock(Source.class);
    Value exported = mock(Value.class);
    Value poly = mock(Value.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "api");
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(poly);
    when(poly.getMember("Api")).thenReturn(exported);
    when(exported.canExecute()).thenReturn(false);
    when(exported.isNull()).thenReturn(false);

    assertSame(exported, callResolveClass(exec, Api.class));
    callResolveInstance(exec);
    verify(exported, never()).execute();
  }

  @Test
  void invokeMember_usesHashEntriesForObjectStyleExports() throws Exception {
    PyExecutor exec = newExec(mock(Context.class));
    Value target = mock(Value.class);
    Value member = mock(Value.class);
    Value result = mock(Value.class);

    when(target.isNull()).thenReturn(false);
    when(target.hasMember("hello")).thenReturn(false);
    when(target.hasHashEntries()).thenReturn(true);
    when(target.getHashValue("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(true);
    when(member.execute("x")).thenReturn(result);

    assertSame(result, callInvokeMember(exec, target, "hello", "x"));
  }

  @Test
  void invokeMember_rejectsNullTargets() {
    PyExecutor exec = new PyExecutor(mock(Context.class), mock(ScriptSource.class));
    Value target = mock(Value.class);
    when(target.isNull()).thenReturn(true);

    assertThrows(BindingException.class, () -> callInvokeMember(exec, target, "hello"));
  }

  @Test
  void invokeMember_rejectsMissingTargetInstance() {
    PyExecutor exec = new PyExecutor(mock(Context.class), mock(ScriptSource.class));

    assertThrows(BindingException.class, () -> callInvokeMember(exec, null, "hello"));
  }

  @Test
  void invokeMember_rejectsNonExecutableMembers() throws Exception {
    PyExecutor exec = newExec(mock(Context.class));
    Value target = mock(Value.class);
    Value member = mock(Value.class);

    when(target.isNull()).thenReturn(false);
    when(target.hasMember("hello")).thenReturn(true);
    when(target.getMember("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(false);

    assertThrows(BindingException.class, () -> callInvokeMember(exec, target, "hello"));
  }

  @Test
  void clearAllCachesClearsInstanceAndSourceCaches() throws Exception {
    PyExecutor exec = newExec(mock(Context.class));

    Field instanceField = PyExecutor.class.getDeclaredField("instanceCache");
    instanceField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<Class<?>, WeakReference<Value>> instanceCache =
        (Map<Class<?>, WeakReference<Value>>) instanceField.get(exec);
    instanceCache.put(Api.class, new WeakReference<>(mock(Value.class)));
    exec.sourceCache.put(Api.class, mock(Source.class));

    exec.clearAllCaches();

    assertEquals(0, instanceCache.size());
    assertEquals(0, exec.sourceCache.size());
  }

  @Test
  void evaluateWithoutArgsUsesResolvedInstance() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = newExec(ctx);

    Value instance = mock(Value.class);
    Value member = mock(Value.class);
    Value result = mock(Value.class);

    when(instance.isNull()).thenReturn(false);
    when(instance.hasMember("hello")).thenReturn(true);
    when(instance.getMember("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(true);
    when(member.execute(new Object[0])).thenReturn(result);

    Field f = PyExecutor.class.getDeclaredField("instanceCache");
    f.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<Class<?>, WeakReference<Value>> cache = (Map<Class<?>, WeakReference<Value>>) f.get(exec);
    cache.put(Api.class, new WeakReference<>(instance));

    assertSame(result, exec.evaluate(Convention.DEFAULT, "hello", Api.class));
  }

  @Test
  void resolveInstance_executesCallableExports() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);
    Value exported = mock(Value.class);
    Value instance = mock(Value.class);
    Value bindings = mock(Value.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "api");
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(bindings);
    when(bindings.getMember("Api")).thenReturn(exported);
    when(exported.canExecute()).thenReturn(true);
    when(exported.execute()).thenReturn(instance);
    when(instance.isNull()).thenReturn(false);

    callResolveInstance(exec);

    verify(exported).execute();
  }

  @Test
  void invokeMember_wrapsExecutionFailures() throws Exception {
    PyExecutor exec = newExec(mock(Context.class));
    Value target = mock(Value.class);
    Value member = mock(Value.class);

    when(target.isNull()).thenReturn(false);
    when(target.hasMember("hello")).thenReturn(true);
    when(target.getMember("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(true);
    when(member.execute("x")).thenThrow(new RuntimeException("err"));

    assertThrows(InvocationException.class, () -> callInvokeMember(exec, target, "hello", "x"));
  }

  @Test
  void validateBindingRejectsNullInterface() {
    PyExecutor exec = new PyExecutor(mock(Context.class), mock(ScriptSource.class));

    assertThrows(IllegalArgumentException.class, () -> exec.validateBinding(null));
  }

  @Test
  void validateBindingByMethodNameRejectsMissingBindingFunction() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);
    Value polyBindings = mock(Value.class);
    Value pyBindings = mock(Value.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "api");
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(polyBindings);
    when(polyBindings.getMember("hello")).thenReturn(null);
    when(ctx.getBindings("python")).thenReturn(pyBindings);
    when(pyBindings.getMember("hello")).thenReturn(null);

    assertThrows(BindingException.class, () -> exec.validateBinding(Api.class, Convention.BY_METHOD_NAME));
  }

  @Test
  void validateBindingResolvesInterface() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);
    Value exported = mock(Value.class);
    Value bindings = mock(Value.class);
    Value member = mock(Value.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "api");
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(bindings);
    when(bindings.getMember("Api")).thenReturn(exported);
    when(exported.canExecute()).thenReturn(false);
    when(exported.isNull()).thenReturn(false);
    when(exported.hasMember("hello")).thenReturn(true);
    when(exported.getMember("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(true);

    exec.validateBinding(Api.class);

    verify(ctx).eval(source);
  }

  @Test
  void metadataIncludesCachedInterfaces() throws Exception {
    PyExecutor exec = newExec(mock(Context.class));

    Field f = PyExecutor.class.getDeclaredField("instanceCache");
    f.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<Class<?>, WeakReference<Value>> cache = (Map<Class<?>, WeakReference<Value>>) f.get(exec);
    cache.put(Api.class, new WeakReference<>(mock(Value.class)));

    assertEquals(1, exec.metadata().get("instanceCacheSize"));
    assertEquals(1, ((java.util.List<?>) exec.metadata().get("cachedInterfaces")).size());
  }

  @Test
  void createUsesPolyglotHelperContext() {
    Context createdContext = mock(Context.class);
    ScriptSource createdScriptSource = mock(ScriptSource.class);
    @SuppressWarnings("unchecked")
    Consumer<Builder> customizer = mock(Consumer.class);

    try (MockedStatic<PolyglotHelper> polyglotHelper = mockStatic(PolyglotHelper.class)) {
      polyglotHelper
          .when(() -> PolyglotHelper.newContext(SupportedLanguage.PYTHON, customizer))
          .thenReturn(createdContext);

      PyExecutor created = PyExecutor.create(createdScriptSource, customizer);

      assertSame(createdContext, created.context);
      assertSame(createdScriptSource, created.scriptSource);
    }
  }

  @Test
  void createWithContextUsesProvidedContext() {
    Context createdContext = mock(Context.class);
    ScriptSource createdScriptSource = mock(ScriptSource.class);

    PyExecutor created = PyExecutor.createWithContext(createdContext, createdScriptSource);

    assertSame(createdContext, created.context);
    assertSame(createdScriptSource, created.scriptSource);
  }
}
