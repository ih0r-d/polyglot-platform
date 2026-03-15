package io.github.ih0rd.adapter.context;

import static io.github.ih0rd.adapter.utils.StringCaseConverter.camelToSnake;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import io.github.ih0rd.adapter.exceptions.BindingException;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/**
 * JavaScript-specific executor backed by GraalJS.
 *
 * <p>The executor loads one script per Java contract and resolves interface methods from JavaScript
 * language bindings.
 */
public final class JsExecutor extends AbstractPolyglotExecutor {

  /**
   * Creates a JavaScript executor.
   *
   * @param context GraalJS context
   * @param scriptSource script source abstraction
   */
  public JsExecutor(Context context, ScriptSource scriptSource) {
    super(context, scriptSource);
  }

  @Override
  public String languageId() {
    return SupportedLanguage.JS.id();
  }

  /** Ensures the script is loaded, then invokes a function from JavaScript bindings. */
  @Override
  protected <T> Value evaluate(String methodName, Class<T> memberTargetType, Object... args) {

    ensureModuleLoaded(memberTargetType);
    return callFunction(methodName, args);
  }

  /** No-argument variant of JavaScript method invocation. */
  @Override
  protected <T> Value evaluate(String methodName, Class<T> memberTargetType) {

    ensureModuleLoaded(memberTargetType);
    return callFunction(methodName);
  }

  /**
   * Validates that each Java contract method has a corresponding executable JavaScript function.
   */
  @Override
  public <T> void validateBinding(Class<T> iface) {
    if (iface == null) {
      throw new IllegalArgumentException("Interface type must not be null");
    }

    ensureModuleLoaded(iface);

    Value bindings = context.getBindings(languageId());

    for (Method method : iface.getMethods()) {
      if (method.getDeclaringClass() == Object.class) {
        continue;
      }

      String name = method.getName();
      Value fn = bindings.getMember(name);

      if (fn == null || !fn.canExecute()) {
        throw new BindingException(
            "JavaScript function '%s' not found or not executable for interface '%s'"
                .formatted(name, iface.getName()));
      }
    }
  }

  /** Adds JavaScript-specific loading information to the executor metadata snapshot. */
  @Override
  public Map<String, Object> metadata() {
    Map<String, Object> info = new LinkedHashMap<>(super.metadata());
    info.put("loadedInterfaces", sourceCache.keySet().stream().map(Class::getName).toList());
    return info;
  }

  /**
   * Creates a JavaScript executor with a new internally managed context.
   *
   * @param scriptSource script source implementation
   * @param customizer optional context-builder customizer
   * @return configured executor
   */
  public static JsExecutor create(ScriptSource scriptSource, Consumer<Context.Builder> customizer) {

    Context context = PolyglotHelper.newContext(SupportedLanguage.JS, customizer);
    return new JsExecutor(context, scriptSource);
  }

  /** Creates a JavaScript executor using a caller-provided context. */
  public static JsExecutor createWithContext(Context context, ScriptSource scriptSource) {

    return new JsExecutor(context, scriptSource);
  }

  /** Loads and evaluates the JavaScript script associated with the given contract if needed. */
  private <T> void ensureModuleLoaded(Class<T> iface) {
    sourceCache.computeIfAbsent(
        iface,
        cls -> {
          String interfaceName = cls.getSimpleName();
          String moduleName = camelToSnake(interfaceName);
          Source src = loadScript(SupportedLanguage.JS, moduleName);
          context.eval(src);
          return src;
        });
  }
}
