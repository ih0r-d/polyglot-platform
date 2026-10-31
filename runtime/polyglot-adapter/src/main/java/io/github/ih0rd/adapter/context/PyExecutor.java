package io.github.ih0rd.adapter.context;

import static io.github.ih0rd.adapter.utils.StringCaseConverter.camelToSnake;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.adapter.observability.InvalidationReason;
import io.github.ih0rd.adapter.observability.RuntimeCache;
import io.github.ih0rd.adapter.observability.RuntimeObserver;
import io.github.ih0rd.adapter.observability.RuntimeOperation;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/**
 * Python-specific executor backed by GraalPy.
 *
 * <p>This is the primary runtime entry point for direct, non-Spring usage. Spring Boot users will
 * normally receive this executor through the starter auto-configuration or inject Java interfaces
 * annotated with {@code @PolyglotClient}.
 *
 * <p>The executor follows the repository's default Python convention:
 *
 * <ul>
 *   <li>script name derived from the Java interface simple name in snake case
 *   <li>exported Python value name equal to the Java interface simple name
 * </ul>
 *
 * <p>Sources are cached per Java interface. Resolved Python targets are cached per interface using
 * weak references, so a reclaimed target may be recreated on a later invocation.
 *
 * <p>Minimal direct setup:
 *
 * <pre>{@code
 * ScriptSource scripts = new ClasspathScriptSource();
 * PyExecutor executor = PyExecutor.create(scripts, builder -> builder.allowAllAccess(false));
 * executor.validateBinding(RecommendationService.class, Convention.DEFAULT);
 * }</pre>
 */
public final class PyExecutor extends AbstractPolyglotExecutor {

  /** Cache of resolved Python targets keyed by Java interface type. */
  private final Map<Class<?>, WeakReference<Value>> instanceCache = new ConcurrentHashMap<>();

  /** Tracks method-name contracts whose module source has been evaluated in this context. */
  private final Set<Class<?>> loadedMethodNameContracts = new HashSet<>();

  /**
   * Creates a Python executor from an existing GraalPy context.
   *
   * <p>Use this constructor when the application needs to configure the GraalPy context directly.
   * The executor uses the supplied context and closes it when the executor is closed.
   *
   * @param context GraalPy context
   * @param scriptSource script source abstraction
   */
  public PyExecutor(Context context, ScriptSource scriptSource) {
    super(context, scriptSource);
  }

  /**
   * Creates a Python executor from an existing GraalPy context with explicit context ownership.
   *
   * @param context GraalPy context
   * @param scriptSource script source abstraction
   * @param contextOwnership context lifecycle ownership
   */
  public PyExecutor(Context context, ScriptSource scriptSource, ContextOwnership contextOwnership) {
    super(context, scriptSource, contextOwnership);
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
          RuntimeObserver observer = runtimeObserver();
          long started = RuntimeObservation.start(observer, RuntimeOperation.CONTRACT_VALIDATION);
          boolean failed = true;
          try {
            switch (requireConvention(convention)) {
              case DEFAULT, BY_INTERFACE_EXPORT -> validateInterfaceExportBinding(iface);
              case BY_METHOD_NAME -> validateMethodNameBinding(iface);
            }
            failed = false;
          } finally {
            RuntimeObservation.finish(
                observer, RuntimeOperation.CONTRACT_VALIDATION, started, failed);
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
   * Creates a Python executor with a new context used by the executor.
   *
   * <p>The optional customizer is the place to configure GraalVM access permissions, host access,
   * and other context options needed by trusted scripts.
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
   * <p>Use this factory when the application needs to configure the GraalPy context directly. The
   * executor uses the supplied context and closes it when the executor is closed.
   *
   * @param context GraalPy context supplied by the caller
   * @param scriptSource script source implementation
   * @return configured executor bound to the provided context
   */
  public static PyExecutor createWithContext(Context context, ScriptSource scriptSource) {

    return new PyExecutor(context, scriptSource);
  }

  /**
   * Creates a Python executor with a caller-provided context and explicit lifecycle ownership.
   *
   * <p>Use {@link ContextOwnership#BORROWED} when the context is shared or closed by caller-managed
   * infrastructure. Use {@link ContextOwnership#OWNED_BY_EXECUTOR} when closing this executor
   * should also close the supplied context.
   *
   * @param context GraalPy context supplied by the caller
   * @param scriptSource script source implementation
   * @param contextOwnership context lifecycle ownership
   * @return configured executor bound to the provided context
   */
  public static PyExecutor createWithContext(
      Context context, ScriptSource scriptSource, ContextOwnership contextOwnership) {

    return new PyExecutor(context, scriptSource, contextOwnership);
  }

  /**
   * Resolves and evaluates a Python script by logical name without binding it to a Java contract.
   *
   * <p>This is raw script evaluation only. It does not populate the per-interface source cache or
   * prebind a Java contract, so later contract binding can still evaluate the same script again.
   *
   * @param scriptName logical script name resolved through {@link ScriptSource}
   */
  public void preloadScript(String scriptName) {
    Source source = loadScript(SupportedLanguage.PYTHON, scriptName);
    try {
      withContextLock(() -> evaluateScript(source));
    } catch (PolyglotException e) {
      if (e.isInterrupted()) {
        Thread.currentThread().interrupt();
      }
      throw new InvocationException("Failed to preload script: " + scriptName, e);
    }
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
      RuntimeObservation.cache(runtimeObserver(), RuntimeCache.INSTANCE, true);
      return cached;
    }
    RuntimeObservation.cache(runtimeObserver(), RuntimeCache.INSTANCE, false);

    Source source = resolveSource(iface);
    evaluateScript(source);
    return instantiateExport(iface);
  }

  private <T> Value instantiateExport(Class<T> iface) {
    Value exported = resolveClass(iface);

    Value instance;

    if (exported.canExecute()) {
      // class-style export
      try {
        instance = createObservedInstance(exported);
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

  private Value createObservedInstance(Value exported) {
    RuntimeObserver observer = runtimeObserver();
    long started = RuntimeObservation.start(observer, RuntimeOperation.INSTANCE_CREATION);
    boolean failed = true;
    try {
      Value instance = exported.execute();
      failed = false;
      return instance;
    } finally {
      RuntimeObservation.finish(observer, RuntimeOperation.INSTANCE_CREATION, started, failed);
    }
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
    Source cached = sourceCache.get(iface);
    RuntimeObservation.cache(runtimeObserver(), RuntimeCache.SOURCE, cached != null);
    if (cached != null) return cached;
    return sourceCache.computeIfAbsent(
        iface,
        contract -> loadScript(SupportedLanguage.PYTHON, camelToSnake(contract.getSimpleName())));
  }

  /** Clears cached Python instances resolved for bound contract types. */
  public void clearInstanceCache() {
    withContextLock(() -> clearPythonCaches(InvalidationReason.CLEAR, false));
  }

  /**
   * Invalidates both source and instance cache entries for a single Python contract interface.
   *
   * @param iface contract interface type
   * @param <T> interface type
   */
  @Override
  public <T> void invalidateContractCache(Class<T> iface) {
    if (iface == null) {
      throw new IllegalArgumentException("Interface type must not be null");
    }
    withContextLock(
        () -> {
          invalidatePythonContract(iface, InvalidationReason.EXPLICIT);
          super.invalidateContractCache(iface);
        });
  }

  /** Clears cached Python sources and their per-context module-loaded state. */
  @Override
  public void clearSourceCache() {
    withContextLock(
        () -> {
          int modules = loadedMethodNameContracts.size();
          loadedMethodNameContracts.clear();
          RuntimeObservation.invalidate(
              runtimeObserver(), RuntimeCache.MODULE, InvalidationReason.CLEAR, modules);
          super.clearSourceCache();
        });
  }

  /**
   * Reloads a single Python contract by evicting its caches and eagerly re-validating the binding.
   *
   * <p>This method is useful when callers update one script and want deterministic pickup without
   * clearing all executor caches. Existing proxies resolve the replacement on their next call.
   * Missing sources and syntax errors leave bindings unchanged. Evaluation failures restore known
   * contract bindings and caches, but arbitrary Python side effects cannot be rolled back. Module
   * globals and imported modules are not reset. Reentrant reload from callbacks is rejected.
   *
   * @param iface contract interface type
   * @param <T> interface type
   */
  public <T> void reloadContract(Class<T> iface) {
    reloadContract(iface, Convention.DEFAULT);
  }

  /**
   * Reloads a single Python contract for the provided binding convention.
   *
   * @param iface contract interface type
   * @param convention binding convention used during re-validation
   * @param <T> interface type
   */
  public <T> void reloadContract(Class<T> iface, Convention convention) {
    if (iface == null) {
      throw new IllegalArgumentException("Interface type must not be null");
    }
    Convention effectiveConvention = requireConvention(convention);

    observeReload(() -> reloadSingleUnchecked(iface, effectiveConvention));
  }

  /**
   * Reloads multiple Python contracts in one operation using {@link Convention#DEFAULT}.
   *
   * @param interfaces contract interfaces to reload
   */
  public void reloadContracts(Collection<Class<?>> interfaces) {
    reloadContracts(interfaces, Convention.DEFAULT);
  }

  /**
   * Reloads multiple Python contracts for the provided binding convention.
   *
   * <p>The batch is serialized but not transactional: successful earlier reloads remain published
   * if a later contract fails. Contracts must not publish conflicting Context binding names.
   *
   * @param interfaces contract interfaces to reload
   * @param convention binding convention used during re-validation
   */
  public void reloadContracts(Collection<Class<?>> interfaces, Convention convention) {
    if (interfaces == null) {
      throw new IllegalArgumentException("Interfaces must not be null");
    }
    Convention effectiveConvention = requireConvention(convention);

    List<Class<?>> targets = new ArrayList<>(interfaces.size());
    for (Class<?> iface : interfaces) {
      if (iface == null) {
        throw new IllegalArgumentException("Interfaces must not contain null items");
      }
      targets.add(iface);
    }

    observeReload(
        () -> {
          for (Class<?> iface : targets) {
            reloadSingleUnchecked(iface, effectiveConvention);
          }
        });
  }

  private <T> void validateInterfaceExportBinding(Class<T> iface) {
    validateInstance(iface, resolveInstance(iface));
  }

  private void validateInstance(Class<?> iface, Value instance) {
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
    boolean loaded = loadedMethodNameContracts.contains(iface);
    RuntimeObservation.cache(runtimeObserver(), RuntimeCache.MODULE, loaded);
    if (loaded) return;

    Source source = resolveSource(iface);
    evaluateScript(source);
    loadedMethodNameContracts.add(iface);
  }

  private <T> Value invokeByMethodName(Class<T> iface, String methodName, Object... args) {
    ensureModuleLoaded(iface);
    try {
      return resolveBindingFunction(methodName).execute(args);
    } catch (Exception e) {
      throw new InvocationException("Error executing Python method '%s'".formatted(methodName), e);
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
    withContextLock(
        () -> {
          clearPythonCaches(InvalidationReason.CLEAR, true);
          super.clearSourceCache();
        });
  }

  /** Clears Python-specific caches when the executor is closed. */
  @Override
  protected void closeExecutorResources() {
    clearPythonCaches(InvalidationReason.SHUTDOWN, true);
    super.closeExecutorResources();
  }

  private void observeReload(Runnable reload) {
    RuntimeObserver observer = runtimeObserver();
    long started = RuntimeObservation.start(observer, RuntimeOperation.RELOAD);
    boolean failed = true;
    try {
      rejectReentrantReload();
      withContextLock(reload);
      failed = false;
    } finally {
      RuntimeObservation.finish(observer, RuntimeOperation.RELOAD, started, failed);
    }
  }

  private void invalidatePythonContract(Class<?> iface, InvalidationReason reason) {
    boolean module = loadedMethodNameContracts.remove(iface);
    WeakReference<Value> instance = instanceCache.remove(iface);
    RuntimeObservation.invalidate(runtimeObserver(), RuntimeCache.MODULE, reason, module ? 1 : 0);
    RuntimeObservation.invalidate(
        runtimeObserver(), RuntimeCache.INSTANCE, reason, instance == null ? 0 : 1);
  }

  private void clearPythonCaches(InvalidationReason reason, boolean includeModules) {
    int instances = instanceCache.size();
    instanceCache.clear();
    RuntimeObservation.invalidate(runtimeObserver(), RuntimeCache.INSTANCE, reason, instances);
    if (includeModules) {
      int modules = loadedMethodNameContracts.size();
      loadedMethodNameContracts.clear();
      RuntimeObservation.invalidate(runtimeObserver(), RuntimeCache.MODULE, reason, modules);
    }
  }

  private void rejectReentrantReload() {
    if (Thread.holdsLock(contextMonitor)) {
      throw new BindingException(
          "Cannot reload a Python contract during an active executor operation");
    }
  }

  private void reloadSingleUnchecked(Class<?> iface, Convention convention) {
    String module = camelToSnake(iface.getSimpleName());
    Source replacement = loadScript(SupportedLanguage.PYTHON, module);
    try {
      context.parse(replacement);
    } catch (PolyglotException failure) {
      restoreInterrupt(failure);
      throw failure;
    }

    Set<String> names = new HashSet<>();
    if (convention == Convention.BY_METHOD_NAME) {
      for (Method method : contractMethods(iface)) {
        names.add(method.getName());
      }
    } else {
      names.add(iface.getSimpleName());
    }
    PythonReloadBindings bindings = new PythonReloadBindings(context, names);
    Source previousSource = sourceCache.get(iface);
    WeakReference<Value> previousReference = instanceCache.get(iface);
    Value previousInstance = previousReference == null ? null : previousReference.get();
    boolean previouslyLoaded = loadedMethodNameContracts.contains(iface);
    try {
      bindings.clear();
      invalidatePythonContract(iface, InvalidationReason.RELOAD);
      sourceCache.put(iface, replacement);
      RuntimeObservation.invalidate(
          runtimeObserver(),
          RuntimeCache.SOURCE,
          InvalidationReason.RELOAD,
          previousSource == null ? 0 : 1);
      validateReplacement(iface, convention, module, replacement, names, bindings);
    } catch (RuntimeException failure) {
      restoreInterrupt(failure);
      restoreAfterFailedReload(
          iface, previousSource, previousInstance, previouslyLoaded, bindings, failure);
      throw new BindingException(
          "Failed to reload Python module '%s' for contract '%s'; Python side effects cannot be rolled back. Recreate the executor if clean guest state is required."
              .formatted(module, iface.getName()),
          failure);
    }
  }

  private void validateReplacement(
      Class<?> iface,
      Convention convention,
      String module,
      Source replacement,
      Set<String> names,
      PythonReloadBindings bindings) {
    // Language bindings can expose builtins after deletion; those are not replacement exports.
    evaluateScript(replacement);
    ensureOpen();
    for (String name : names) {
      if (!bindings.contains(name)) {
        throw new BindingException(
            "Replacement Python module '%s' does not define required symbol '%s'"
                .formatted(module, name));
      }
    }
    switch (convention) {
      case DEFAULT, BY_INTERFACE_EXPORT -> validateInstance(iface, instantiateExport(iface));
      case BY_METHOD_NAME -> {
        loadedMethodNameContracts.add(iface);
        validateMethodNameBinding(iface);
      }
    }
  }

  private void restoreAfterFailedReload(
      Class<?> iface,
      Source previousSource,
      Value previousInstance,
      boolean previouslyLoaded,
      PythonReloadBindings bindings,
      RuntimeException failure) {
    if (isClosed()) {
      return;
    }
    restoreContractCaches(iface, previousSource, previousInstance, previouslyLoaded);
    try {
      bindings.restore();
    } catch (RuntimeException restorationFailure) {
      failure.addSuppressed(restorationFailure);
    }
  }

  private void restoreContractCaches(
      Class<?> iface, Source previousSource, Value previousInstance, boolean previouslyLoaded) {
    if (previousSource == null) {
      sourceCache.remove(iface);
    } else {
      sourceCache.put(iface, previousSource);
    }
    if (previousInstance == null) {
      instanceCache.remove(iface);
    } else {
      instanceCache.put(iface, new WeakReference<>(previousInstance));
    }
    if (previouslyLoaded) {
      loadedMethodNameContracts.add(iface);
    } else {
      loadedMethodNameContracts.remove(iface);
    }
  }
}
