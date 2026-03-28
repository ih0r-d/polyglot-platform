package io.github.ih0rd.adapter.context;

import static io.github.ih0rd.adapter.utils.StringCaseConverter.camelToSnake;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/**
 * Python-specific executor backed by GraalPy.
 *
 * <p>The executor follows the repository's default convention:
 *
 * <ul>
 *   <li>script name derived from the Java interface simple name in snake case
 *   <li>exported Python value name equal to the Java interface simple name
 * </ul>
 *
 * <p>Sources are cached per Java interface. Resolved Python targets are cached per interface using
 * weak references, so a reclaimed target may be recreated on a later invocation.
 */
public final class PyExecutor extends AbstractPolyglotExecutor {

  /** Cache of resolved Python targets keyed by Java interface type. */
  private final Map<Class<?>, WeakReference<Value>> instanceCache = new ConcurrentHashMap<>();

  /**
   * Creates a Python executor.
   *
   * @param context GraalPy context
   * @param scriptSource script source abstraction
   */
  public PyExecutor(Context context, ScriptSource scriptSource) {
    super(context, scriptSource);
  }

  @Override
  public String languageId() {
    return SupportedLanguage.PYTHON.id();
  }

  @Override
  protected <T> Value evaluate(
      Convention convention, String methodName, Class<T> memberTargetType, Object... args) {
    return withContextLock(
        () ->
            switch (requireConvention(convention)) {
              case DEFAULT, BY_INTERFACE_EXPORT -> {
                Value instance = resolveInstance(memberTargetType);
                yield invokeMember(instance, methodName, args);
              }
              case BY_METHOD_NAME -> invokeByMethodName(memberTargetType, methodName, args);
            });
  }

  /** Eagerly resolves the Python export to verify that the contract can be bound. */
  @Override
  public <T> void validateBinding(Class<T> iface, Convention convention) {
    if (iface == null) {
      throw new IllegalArgumentException("Interface type must not be null");
    }

    withContextLock(
        () -> {
          switch (requireConvention(convention)) {
            case DEFAULT, BY_INTERFACE_EXPORT -> validateInterfaceExportBinding(iface);
            case BY_METHOD_NAME -> validateMethodNameBinding(iface);
          }
        });
  }

  /** Adds Python-specific cache details to the executor metadata snapshot. */
  @Override
  public Map<String, Object> metadata() {
    Map<String, Object> info = new LinkedHashMap<>(super.metadata());
    info.put("cachedInterfaces", instanceCache.keySet().stream().map(Class::getName).toList());
    info.put("instanceCacheSize", instanceCache.size());
    return info;
  }

  /**
   * Creates a Python executor with a new internally managed context.
   *
   * @param scriptSource script source implementation
   * @param customizer optional context-builder customizer
   * @return configured executor
   */
  public static PyExecutor create(ScriptSource scriptSource, Consumer<Context.Builder> customizer) {

    Context context = PolyglotHelper.newContext(SupportedLanguage.PYTHON, customizer);
    return new PyExecutor(context, scriptSource);
  }

  /**
   * Creates a Python executor with a caller-provided context.
   *
   * <p>The caller remains responsible for the context lifecycle.
   */
  public static PyExecutor createWithContext(Context context, ScriptSource scriptSource) {

    return new PyExecutor(context, scriptSource);
  }

  /**
   * Resolves and evaluates a Python script by logical name without binding it to a Java contract.
   *
   * @param scriptName logical script name resolved through {@link ScriptSource}
   */
  public void preloadScript(String scriptName) {
    Source source = loadScript(SupportedLanguage.PYTHON, scriptName);
    withContextLock(() -> context.eval(source));
  }

  /**
   * Resolves or creates the Python target object for a Java contract.
   *
   * <p>Supported exports are:
   *
   * <ul>
   *   <li>class-style exports, where the exported value is executable and instantiated
   *   <li>dictionary-style exports, where methods are resolved from a function map
   * </ul>
   */
  private <T> Value resolveInstance(Class<T> iface) {

    WeakReference<Value> ref = instanceCache.get(iface);
    Value cached = (ref != null ? ref.get() : null);
    if (cached != null && !cached.isNull()) {
      return cached;
    }

    Source source = resolveSource(iface);
    context.eval(source);

    Value exported = resolveClass(iface);

    Value instance;

    if (exported.canExecute()) {
      // class-style export
      try {
        instance = exported.execute();
      } catch (Exception e) {
        throw new InvocationException(
            "Failed to instantiate Python class '%s'".formatted(iface.getSimpleName()), e);
      }
    } else {
      // object-style export (map of functions)
      instance = exported;
    }

    instanceCache.put(iface, new WeakReference<>(instance));
    return instance;
  }

  /** Resolves the exported Python contract by interface simple name. */
  private <T> Value resolveClass(Class<T> iface) {
    String className = iface.getSimpleName();

    Value polyglotBindings = context.getPolyglotBindings();
    Value exported = polyglotBindings.getMember(className);
    if (exported != null) {
      return exported;
    }

    Value pyBindings = context.getBindings(languageId());
    Value fromBindings = pyBindings.getMember(className);
    if (fromBindings != null) {
      return fromBindings;
    }

    throw new BindingException(
        "Python class '%s' not found in polyglot or language bindings".formatted(className));
  }

  /**
   * Invokes a method on a resolved Python target, handling both object-member and hash-entry access
   * patterns.
   */
  private Value invokeMember(Value target, String methodName, Object... args) {

    if (target == null || target.isNull()) {
      throw new BindingException(
          "Cannot invoke method '%s' on null Python target".formatted(methodName));
    }

    Value member = null;

    // Class-style: method exposed as member
    if (target.hasMember(methodName)) {
      member = target.getMember(methodName);
    }
    // Object-style: exported dict of functions
    else if (target.hasHashEntries()) {
      member = target.getHashValue(methodName);
    }

    if (member == null || !member.canExecute()) {
      throw new BindingException(
          "Python method '%s' not found or not executable".formatted(methodName));
    }

    try {
      return member.execute(args);
    } catch (Exception e) {
      throw new InvocationException("Error executing Python method '%s'".formatted(methodName), e);
    }
  }

  /** Resolves and caches the Python source for the given Java contract. */
  private <T> Source resolveSource(Class<T> iface) {
    return sourceCache.computeIfAbsent(
        iface,
        cls -> {
          String interfaceName = cls.getSimpleName();
          String moduleName = camelToSnake(interfaceName);
          return loadScript(SupportedLanguage.PYTHON, moduleName);
        });
  }

  /** Clears cached Python instances resolved for bound contract types. */
  public void clearInstanceCache() {
    instanceCache.clear();
  }

  private <T> void validateInterfaceExportBinding(Class<T> iface) {
    Value instance = resolveInstance(iface);

    for (Method method : contractMethods(iface)) {
      resolveMember(instance, method.getName());
    }
  }

  private <T> void validateMethodNameBinding(Class<T> iface) {
    ensureModuleLoaded(iface);

    for (Method method : contractMethods(iface)) {
      resolveBindingFunction(method.getName());
    }
  }

  private <T> void ensureModuleLoaded(Class<T> iface) {
    Source source = resolveSource(iface);
    context.eval(source);
  }

  private <T> Value invokeByMethodName(Class<T> iface, String methodName, Object... args) {
    ensureModuleLoaded(iface);
    try {
      return resolveBindingFunction(methodName).execute(args);
    } catch (Exception e) {
      throw new InvocationException(
          "Error executing Python method '%s'".formatted(methodName), e);
    }
  }

  private Value resolveBindingFunction(String methodName) {
    Value polyglotBindings = context.getPolyglotBindings();
    Value exported = polyglotBindings.getMember(methodName);
    if (exported != null && exported.canExecute()) {
      return exported;
    }

    Value pyBindings = context.getBindings(languageId());
    Value fromBindings = pyBindings.getMember(methodName);
    if (fromBindings != null && fromBindings.canExecute()) {
      return fromBindings;
    }

    throw new BindingException(
        "Python function '%s' not found or not executable in polyglot or language bindings"
            .formatted(methodName));
  }

  private Value resolveMember(Value target, String methodName) {
    Value member = null;

    if (target.hasMember(methodName)) {
      member = target.getMember(methodName);
    } else if (target.hasHashEntries()) {
      member = target.getHashValue(methodName);
    }

    if (member == null || !member.canExecute()) {
      throw new BindingException(
          "Python method '%s' not found or not executable".formatted(methodName));
    }

    return member;
  }

  /** Clears both the source cache and the Python instance cache. */
  @Override
  public void clearAllCaches() {
    clearInstanceCache();
    super.clearAllCaches();
  }
}
