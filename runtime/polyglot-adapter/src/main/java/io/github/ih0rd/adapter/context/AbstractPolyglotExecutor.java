package io.github.ih0rd.adapter.context;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.adapter.exceptions.EvaluationException;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.adapter.exceptions.ScriptNotFoundException;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/**
 * Common base class for language-specific executors.
 *
 * <p>The base class owns:
 *
 * <ul>
 *   <li>the GraalVM {@link Context}
 *   <li>script loading through {@link ScriptSource}
 *   <li>dynamic proxy binding to Java interfaces
 *   <li>common source caching and metadata
 * </ul>
 *
 * <p>This type is part of the runtime implementation and is not intended as a stable third-party
 * subclassing surface.
 */
public abstract class AbstractPolyglotExecutor implements AutoCloseable {

  /** Underlying GraalVM context used for script evaluation and invocation. */
  protected final Context context;

  /** Strategy used to resolve script content by logical name. */
  protected final ScriptSource scriptSource;

  /** Cache of evaluated or compiled sources keyed by Java contract type. */
  protected final Map<Class<?>, Source> sourceCache = new ConcurrentHashMap<>();

  /**
   * Creates a new executor instance.
   *
   * @param context GraalVM context
   * @param scriptSource script source abstraction
   */
  protected AbstractPolyglotExecutor(Context context, ScriptSource scriptSource) {
    if (context == null) {
      throw new IllegalArgumentException("Context must not be null");
    }
    if (scriptSource == null) {
      throw new IllegalArgumentException("ScriptSource must not be null");
    }
    this.context = context;
    this.scriptSource = scriptSource;
  }

  /** Returns the GraalVM language id, for example {@code python} or {@code js}. */
  protected abstract String languageId();

  /**
   * Executes a guest-language method corresponding to a Java contract method.
   *
   * @param methodName method name on the Java interface
   * @param memberTargetType bound contract type
   * @param args call arguments
   * @param <T> contract type
   * @return raw polyglot value
   */
  protected abstract <T> Value evaluate(
      Convention convention, String methodName, Class<T> memberTargetType, Object... args);

  /**
   * Evaluates inline guest-language code in this context.
   *
   * <p>This method exists mainly for lightweight warmup and compatibility scenarios.
   *
   * @param code guest-language source code
   * @return evaluation result
   */
  public Value evaluate(String code) {
    try {
      Source source =
          Source.newBuilder(languageId(), code, "inline." + languageId()).buildLiteral();
      return context.eval(source);
    } catch (Exception e) {
      throw new InvocationException("Error during " + languageId() + " inline code execution", e);
    }
  }

  /**
   * Binds a Java interface to a guest-language implementation.
   *
   * <p>The returned proxy delegates each non-{@link Object} method to the language-specific
   * executor implementation and converts the result using {@link Value#as(Class)}.
   *
   * @param iface interface to bind
   * @param <T> interface type
   * @return proxy backed by guest-language code
   */
  @SuppressWarnings("unchecked")
  public <T> T bind(Class<T> iface) {
    return bind(iface, Convention.DEFAULT);
  }

  /**
   * Binds a Java interface to a guest-language implementation using a specific convention.
   *
   * @param iface interface to bind
   * @param convention binding convention
   * @param <T> interface type
   * @return proxy backed by guest-language code
   */
  @SuppressWarnings("unchecked")
  public <T> T bind(Class<T> iface, Convention convention) {
    if (iface == null) {
      throw new IllegalArgumentException("Interface type must not be null");
    }
    Convention effectiveConvention = requireConvention(convention);

    return (T)
        Proxy.newProxyInstance(
            iface.getClassLoader(),
            new Class<?>[] {iface},
            (proxy, method, args) -> {
              if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
              }
              String methodName = method.getName();
              Object[] safeArgs = (args != null ? args : new Object[0]);
              Value result = evaluate(effectiveConvention, methodName, iface, safeArgs);
              if (result == null || result.isNull()) {
                return null;
              }
              return result.as(method.getReturnType());
            });
  }

  /**
   * Validates that a contract can be bound successfully.
   *
   * <p>Subclasses override this to perform language-specific eager validation.
   *
   * @param iface contract type
   * @param <T> interface type
   */
  public <T> void validateBinding(Class<T> iface) {
    validateBinding(iface, Convention.DEFAULT);
  }

  /**
   * Validates that a contract can be bound successfully using a specific convention.
   *
   * @param iface contract type
   * @param convention binding convention
   * @param <T> interface type
   */
  public <T> void validateBinding(Class<T> iface, Convention convention) {
    if (iface == null) {
      throw new IllegalArgumentException("Interface type must not be null");
    }
    requireConvention(convention);
    throw new UnsupportedOperationException(
        "Binding validation is not implemented for executor: " + getClass().getSimpleName());
  }

  protected final Convention requireConvention(Convention convention) {
    return Objects.requireNonNull(convention, "Convention must not be null");
  }

  protected final Method[] contractMethods(Class<?> iface) {
    return java.util.Arrays.stream(iface.getMethods())
        .filter(method -> method.getDeclaringClass() != Object.class)
        .toArray(Method[]::new);
  }

  /**
   * Invokes a function from language bindings.
   *
   * @param methodName function name
   * @param args call arguments
   * @return invocation result
   */
  protected Value callFunction(String methodName, Object... args) {
    try {
      Value bindings = context.getBindings(languageId());
      Value fn = bindings.getMember(methodName);

      if (fn == null || !fn.canExecute()) {
        throw new BindingException("Function not found: " + methodName);
      }
      return fn.execute(args);
    } catch (BindingException e) {
      throw e;
    } catch (Exception e) {
      throw new InvocationException("Error executing function: " + methodName, e);
    }
  }

  /**
   * Loads a script from the configured {@link ScriptSource}.
   *
   * @param language guest language
   * @param scriptName logical script name
   * @return compiled source
   */
  protected Source loadScript(SupportedLanguage language, String scriptName) {
    if (!scriptSource.exists(language, scriptName)) {
      throw new ScriptNotFoundException(
          "Script not found: " + scriptName + " for language " + language);
    }

    try (Reader reader = scriptSource.open(language, scriptName)) {
      return Source.newBuilder(language.id(), reader, scriptName).buildLiteral();
    } catch (IOException e) {
      throw new EvaluationException(
          "Failed to load script: " + scriptName + " for language " + language, e);
    }
  }

  /** Clears the per-interface source cache. */
  public void clearSourceCache() {
    sourceCache.clear();
  }

  /** Clears all caches maintained by this executor. */
  public void clearAllCaches() {
    clearSourceCache();
  }

  /**
   * Returns a lightweight metadata snapshot intended for diagnostics and operational reporting.
   *
   * @return mutable metadata map
   */
  public Map<String, Object> metadata() {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("executorType", getClass().getName());
    info.put("languageId", languageId());
    info.put("sourceCacheSize", sourceCache.size());
    return info;
  }

  /** Closes the underlying GraalVM context. */
  @Override
  public void close() {
    context.close();
  }
}
