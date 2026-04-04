package io.github.ih0rd.adapter.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

@SuppressWarnings({"unchecked", "resource"})
class PyExecutorTest {

  interface Api {
    String hello(String arg);
  }

  static final class NamespaceOne {
    interface SharedApi {
      String hello(String arg);
    }
  }

  static final class NamespaceTwo {
    interface SharedApi {
      String hello(String arg);
    }
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

  private Value callResolveClass(PyExecutor exec) {
    try {
      Method m = PyExecutor.class.getDeclaredMethod("resolveClass", Class.class);
      m.setAccessible(true);
      return (Value) m.invoke(exec, (Class<?>) Api.class);
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

  private Source callResolveSource(PyExecutor exec, Class<?> iface) {
    try {
      Method method = PyExecutor.class.getDeclaredMethod("resolveSource", Class.class);
      method.setAccessible(true);
      return (Source) method.invoke(exec, iface);
    } catch (Exception e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) throw runtimeException;
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
  void validateBindingByMethodNameUsesLanguageBindingsFallback() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);
    Value polyBindings = mock(Value.class);
    Value languageBindings = mock(Value.class);
    Value fn = mock(Value.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "api");
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(polyBindings);
    when(polyBindings.getMember("hello")).thenReturn(null);
    when(ctx.getBindings("python")).thenReturn(languageBindings);
    when(languageBindings.getMember("hello")).thenReturn(fn);
    when(fn.canExecute()).thenReturn(true);

    assertDoesNotThrow(() -> exec.validateBinding(Api.class, Convention.BY_METHOD_NAME));
  }

  @Test
  void evaluateByMethodNameWrapsInvocationFailure() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);
    Value polyBindings = mock(Value.class);
    Value fn = mock(Value.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "api");
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(polyBindings);
    when(polyBindings.getMember("hello")).thenReturn(fn);
    when(fn.canExecute()).thenReturn(true);
    when(fn.execute("x")).thenThrow(new RuntimeException("boom"));

    assertThrows(
        InvocationException.class,
        () -> exec.evaluate(Convention.BY_METHOD_NAME, "hello", Api.class, "x"));
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

    assertSame(cls, callResolveClass(exec));
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

    assertThrows(BindingException.class, () -> callResolveClass(exec));
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

    assertSame(type, callResolveClass(exec));
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

    assertSame(exported, callResolveClass(exec));
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
  void validateBindingDefaultSupportsHashEntryExports() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);
    Value exported = mock(Value.class);
    Value polyBindings = mock(Value.class);
    Value method = mock(Value.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "api");
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(polyBindings);
    when(polyBindings.getMember("Api")).thenReturn(exported);
    when(exported.canExecute()).thenReturn(false);
    when(exported.hasMember("hello")).thenReturn(false);
    when(exported.hasHashEntries()).thenReturn(true);
    when(exported.getHashValue("hello")).thenReturn(method);
    when(method.canExecute()).thenReturn(true);

    assertDoesNotThrow(() -> exec.validateBinding(Api.class, Convention.DEFAULT));
  }

  @Test
  void validateBindingDefaultRejectsMissingResolvedMember() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);
    Value exported = mock(Value.class);
    Value polyBindings = mock(Value.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "api");
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(polyBindings);
    when(polyBindings.getMember("Api")).thenReturn(exported);
    when(exported.canExecute()).thenReturn(false);
    when(exported.hasMember("hello")).thenReturn(false);
    when(exported.hasHashEntries()).thenReturn(true);
    when(exported.getHashValue("hello")).thenReturn(null);

    assertThrows(BindingException.class, () -> exec.validateBinding(Api.class, Convention.DEFAULT));
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

    assertThrows(
        BindingException.class, () -> exec.validateBinding(Api.class, Convention.BY_METHOD_NAME));
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
    Map<Class<?>, WeakReference<Value>> cache = (Map<Class<?>, WeakReference<Value>>) f.get(exec);
    cache.put(Api.class, new WeakReference<>(mock(Value.class)));

    assertEquals(1, exec.metadata().get("instanceCacheSize"));
    assertEquals(1, ((java.util.List<?>) exec.metadata().get("cachedInterfaces")).size());
  }

  @Test
  void createUsesPolyglotHelperContext() {
    Context createdContext = mock(Context.class);
    ScriptSource createdScriptSource = mock(ScriptSource.class);
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

  @Test
  void preloadScriptDoesNotPopulateInterfaceSourceCache() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "bootstrap");

    exec.preloadScript("bootstrap");

    assertEquals(0, exec.sourceCache.size());
    verify(ctx).eval(source);
  }

  @Test
  void preloadScriptDoesNotPopulateInstanceCache() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "bootstrap");

    exec.preloadScript("bootstrap");

    Field instanceField = PyExecutor.class.getDeclaredField("instanceCache");
    instanceField.setAccessible(true);
    Map<Class<?>, WeakReference<Value>> instanceCache =
        (Map<Class<?>, WeakReference<Value>>) instanceField.get(exec);
    assertEquals(0, instanceCache.size());
  }

  @Test
  void clearSourceCacheDoesNotInvalidateLiveInstanceCache() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = newExec(ctx);
    Value instance = mock(Value.class);
    Value member = mock(Value.class);
    Value result = mock(Value.class);

    when(instance.isNull()).thenReturn(false);
    when(instance.hasMember("hello")).thenReturn(true);
    when(instance.getMember("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(true);
    when(member.execute("x")).thenReturn(result);

    Field instanceField = PyExecutor.class.getDeclaredField("instanceCache");
    instanceField.setAccessible(true);
    Map<Class<?>, WeakReference<Value>> instanceCache =
        (Map<Class<?>, WeakReference<Value>>) instanceField.get(exec);
    instanceCache.put(Api.class, new WeakReference<>(instance));

    exec.clearSourceCache();

    assertSame(result, exec.evaluate(Convention.DEFAULT, "hello", Api.class, "x"));
    verifyNoInteractions(ctx);
  }

  @Test
  void invalidateContractCacheEvictsSourceAndInstanceForInterface() throws Exception {
    PyExecutor exec = newExec(mock(Context.class));

    exec.sourceCache.put(Api.class, mock(Source.class));
    exec.sourceCache.put(NamespaceOne.SharedApi.class, mock(Source.class));

    Field instanceField = PyExecutor.class.getDeclaredField("instanceCache");
    instanceField.setAccessible(true);
    Map<Class<?>, WeakReference<Value>> instanceCache =
        (Map<Class<?>, WeakReference<Value>>) instanceField.get(exec);
    instanceCache.put(Api.class, new WeakReference<>(mock(Value.class)));
    instanceCache.put(NamespaceOne.SharedApi.class, new WeakReference<>(mock(Value.class)));

    exec.invalidateContractCache(Api.class);

    assertFalse(exec.sourceCache.containsKey(Api.class));
    assertTrue(exec.sourceCache.containsKey(NamespaceOne.SharedApi.class));
    assertFalse(instanceCache.containsKey(Api.class));
    assertTrue(instanceCache.containsKey(NamespaceOne.SharedApi.class));
  }

  @Test
  void reloadContractClearsContractCachesAndRevalidates() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);
    Value exported = mock(Value.class);
    Value bindings = mock(Value.class);
    Value member = mock(Value.class);

    exec.sourceCache.put(Api.class, mock(Source.class));
    Field instanceField = PyExecutor.class.getDeclaredField("instanceCache");
    instanceField.setAccessible(true);
    Map<Class<?>, WeakReference<Value>> instanceCache =
        (Map<Class<?>, WeakReference<Value>>) instanceField.get(exec);
    instanceCache.put(Api.class, new WeakReference<>(mock(Value.class)));

    doReturn(source).when(exec).loadScript(SupportedLanguage.PYTHON, "api");
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(bindings);
    when(bindings.getMember("Api")).thenReturn(exported);
    when(exported.canExecute()).thenReturn(false);
    when(exported.hasMember("hello")).thenReturn(true);
    when(exported.getMember("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(true);

    exec.reloadContract(Api.class);

    assertTrue(exec.sourceCache.containsKey(Api.class));
    assertTrue(instanceCache.containsKey(Api.class));
    verify(ctx).eval(source);
  }

  @Test
  void reloadContractRejectsNullInterface() {
    PyExecutor exec = new PyExecutor(mock(Context.class), mock(ScriptSource.class));

    assertThrows(IllegalArgumentException.class, () -> exec.reloadContract(null));
  }

  @Test
  void reloadContractsRevalidatesEachRequestedContract() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source source = mock(Source.class);
    Value bindings = mock(Value.class);
    Value apiExport = mock(Value.class);
    Value sharedExport = mock(Value.class);
    Value apiMember = mock(Value.class);
    Value sharedMember = mock(Value.class);

    doReturn(source).when(exec).loadScript(eq(SupportedLanguage.PYTHON), any());
    when(ctx.eval(source)).thenReturn(mock(Value.class));
    when(ctx.getPolyglotBindings()).thenReturn(bindings);
    when(bindings.getMember("Api")).thenReturn(apiExport);
    when(bindings.getMember("SharedApi")).thenReturn(sharedExport);
    when(apiExport.canExecute()).thenReturn(false);
    when(sharedExport.canExecute()).thenReturn(false);
    when(apiExport.hasMember("hello")).thenReturn(true);
    when(sharedExport.hasMember("hello")).thenReturn(true);
    when(apiExport.getMember("hello")).thenReturn(apiMember);
    when(sharedExport.getMember("hello")).thenReturn(sharedMember);
    when(apiMember.canExecute()).thenReturn(true);
    when(sharedMember.canExecute()).thenReturn(true);

    exec.reloadContracts(java.util.List.of(Api.class, NamespaceOne.SharedApi.class));

    verify(exec).loadScript(SupportedLanguage.PYTHON, "api");
    verify(exec).loadScript(SupportedLanguage.PYTHON, "shared_api");
    assertTrue(exec.sourceCache.containsKey(Api.class));
    assertTrue(exec.sourceCache.containsKey(NamespaceOne.SharedApi.class));
  }

  @Test
  void reloadContractsRejectsNullCollection() throws Exception {
    PyExecutor exec = newExec(mock(Context.class));

    assertThrows(IllegalArgumentException.class, () -> exec.reloadContracts(null));
  }

  @Test
  void reloadContractsRejectsNullCollectionItem() throws Exception {
    PyExecutor exec = newExec(mock(Context.class));
    List<Class<?>> targets = Arrays.asList(Api.class, null);

    assertThrows(IllegalArgumentException.class, () -> exec.reloadContracts(targets));
  }

  @Test
  void sourceCacheIsKeyedByInterfaceClassEvenWhenSimpleNamesMatch() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = spy(newExec(ctx));
    Source sourceOne = mock(Source.class);
    Source sourceTwo = mock(Source.class);

    doReturn(sourceOne, sourceTwo).when(exec).loadScript(SupportedLanguage.PYTHON, "shared_api");

    Source loadedOne = callResolveSource(exec, NamespaceOne.SharedApi.class);
    Source loadedTwo = callResolveSource(exec, NamespaceTwo.SharedApi.class);

    assertSame(sourceOne, loadedOne);
    assertSame(sourceTwo, loadedTwo);
    assertEquals(2, exec.sourceCache.size());
    verify(exec, times(2)).loadScript(SupportedLanguage.PYTHON, "shared_api");
  }

  @Test
  void concurrentInvocationIsSerializedBySharedExecutorLock() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = newExec(ctx);

    Value instance = mock(Value.class);
    Value member = mock(Value.class);

    when(instance.isNull()).thenReturn(false);
    when(instance.hasMember("hello")).thenReturn(true);
    when(instance.getMember("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(true);

    Field field = PyExecutor.class.getDeclaredField("instanceCache");
    field.setAccessible(true);
    Map<Class<?>, WeakReference<Value>> cache =
        (Map<Class<?>, WeakReference<Value>>) field.get(exec);
    cache.put(Api.class, new WeakReference<>(instance));

    AtomicInteger active = new AtomicInteger();
    AtomicInteger maxActive = new AtomicInteger();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(member.execute("x"))
        .thenAnswer(
            invocation -> {
              int current = active.incrementAndGet();
              maxActive.updateAndGet(previous -> Math.max(previous, current));
              try {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
              } finally {
                active.decrementAndGet();
              }
              return mock(Value.class);
            });

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Value> first =
          pool.submit(() -> exec.evaluate(Convention.DEFAULT, "hello", Api.class, "x"));
      assertTrue(started.await(1, TimeUnit.SECONDS));
      Future<Value> second =
          pool.submit(() -> exec.evaluate(Convention.DEFAULT, "hello", Api.class, "x"));

      release.countDown();
      assertNotNull(first.get(2, TimeUnit.SECONDS));
      assertNotNull(second.get(2, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }

    assertEquals(1, maxActive.get());
  }

  @Test
  void closeWaitsForInFlightInvocationToFinish() throws Exception {
    Context ctx = mock(Context.class);
    PyExecutor exec = newExec(ctx);

    Value instance = mock(Value.class);
    Value member = mock(Value.class);
    Value result = mock(Value.class);

    when(instance.isNull()).thenReturn(false);
    when(instance.hasMember("hello")).thenReturn(true);
    when(instance.getMember("hello")).thenReturn(member);
    when(member.canExecute()).thenReturn(true);

    Field field = PyExecutor.class.getDeclaredField("instanceCache");
    field.setAccessible(true);
    Map<Class<?>, WeakReference<Value>> cache =
        (Map<Class<?>, WeakReference<Value>>) field.get(exec);
    cache.put(Api.class, new WeakReference<>(instance));

    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch closeAttempted = new CountDownLatch(1);
    AtomicBoolean closeCalled = new AtomicBoolean(false);
    doAnswer(
            invocation -> {
              closeCalled.set(true);
              return null;
            })
        .when(ctx)
        .close();
    when(member.execute("x"))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              release.await(2, TimeUnit.SECONDS);
              return result;
            });

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Value> invokeFuture =
          pool.submit(() -> exec.evaluate(Convention.DEFAULT, "hello", Api.class, "x"));
      assertTrue(entered.await(1, TimeUnit.SECONDS));

      Future<?> closeFuture =
          pool.submit(
              () -> {
                closeAttempted.countDown();
                exec.close();
                return null;
              });

      assertTrue(closeAttempted.await(1, TimeUnit.SECONDS));
      assertFalse(closeCalled.get());

      release.countDown();
      assertSame(result, invokeFuture.get(2, TimeUnit.SECONDS));
      closeFuture.get(2, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    assertTrue(closeCalled.get());
  }
}
