package io.github.ih0rd.adapter.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;

import org.graalvm.polyglot.Value;

import io.github.ih0rd.adapter.exceptions.EvaluationException;

/**
 * Utility class providing reflection and polyglot adapter helpers.
 *
 * <p><strong>Responsibilities:</strong>
 *
 * <ul>
 *   <li>Fast method invocation using {@link java.lang.invoke.MethodHandle}
 *   <li>Primitive and wrapper argument coercion for GraalVM calls
 *   <li>Reflection helpers for method discovery and validation
 *   <li>Lightweight polyglot utilities such as {@code getFirstElement()}
 * </ul>
 *
 * <p><strong>Notes:</strong>
 *
 * <p>All reflection or invocation errors are wrapped in {@link EvaluationException}.
 *
 * <p><strong>Example:</strong>
 *
 * <pre>{@code
 * var result = CommonUtils.invokeMethod(MyApi.class, instance, "ping");
 * System.out.println(result.asString());
 * }</pre>
 */
public final class CommonUtils {

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  // Use WeakHashMap to avoid classloader leaks in long-running contexts
  private static final Map<Method, MethodHandle> HANDLE_CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  private CommonUtils() {
    // utility class, do not instantiate
  }

  /**
   * Reflectively invokes a method on the given target instance using {@link MethodHandle}.
   *
   * @param <T> Java contract type
   * @param targetType the Java interface type bound to the polyglot class
   * @param targetInstance the polyglot-mapped Java instance
   * @param methodName the method name to call
   * @param args optional arguments to pass
   * @return {@link Value} containing the unwrapped return value
   * @throws EvaluationException if reflection fails or the method cannot be invoked
   */
  public static <T> Value invokeMethod(
      Class<T> targetType, T targetInstance, String methodName, Object... args) {

    try {
      if (targetInstance == null) {
        throw new EvaluationException("Target instance is null for " + targetType.getSimpleName());
      }
      if (args == null) args = new Object[0];

      Method method = getMethodByName(targetType, methodName);
      MethodHandle handle =
          HANDLE_CACHE.computeIfAbsent(
              method,
              m -> {
                try {
                  return LOOKUP.unreflect(m);
                } catch (IllegalAccessException e) {
                  throw new EvaluationException("Cannot unreflect method: " + m, e);
                }
              });

      Object result =
          (args.length > 0)
              ? handle
                  .bindTo(targetInstance)
                  .invokeWithArguments(coerceArguments(method.getParameterTypes(), args))
              : handle.bindTo(targetInstance).invoke();

      // Wrap Java result into Graal Value for uniformity
      return result == null ? Value.asValue((Object) null) : Value.asValue(result);

    } catch (Throwable e) {
      throw new EvaluationException("Could not invoke method '%s'".formatted(methodName), e);
    }
  }

  private static <T> Method getMethodByName(Class<T> targetType, String methodName)
      throws NoSuchMethodException {
    Class<?>[] parameterTypes = getParameterTypesByMethodName(targetType, methodName);
    return targetType.getMethod(methodName, parameterTypes);
  }

  private static <T> Class<?>[] getParameterTypesByMethodName(
      Class<T> targetType, String methodName) {
    return Arrays.stream(targetType.getDeclaredMethods())
        .filter(method -> method.getName().equals(methodName))
        .findFirst()
        .map(Method::getParameterTypes)
        .orElseThrow(() -> new EvaluationException("Method '" + methodName + "' not found"));
  }

  /**
   * Coerces wrapper arguments to primitive parameter types before reflective invocation.
   *
   * <p>GraalVM conversion is used where applicable.
   */
  private static Object[] coerceArguments(Class<?>[] paramTypes, Object[] args) {
    Object[] coerced = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];
      Class<?> target = paramTypes[i];
      coerced[i] = target.isPrimitive() ? coercePrimitive(target, arg) : arg;
    }
    return coerced;
  }

  /**
   * Uses GraalVM {@link Value#as(Class)} for safe primitive coercion.
   *
   * @throws IllegalArgumentException if {@code null} is passed for a primitive parameter
   */
  private static Object coercePrimitive(Class<?> primitiveType, Object arg) {
    if (arg == null) {
      throw new IllegalArgumentException("Null passed for primitive parameter: " + primitiveType);
    }
    if (arg instanceof Value val) {
      return val.as(primitiveType);
    }
    return arg;
  }

  /**
   * Checks whether a method exists on a given interface.
   *
   * @param interfaceClass Java interface to inspect
   * @param methodName method name to look up
   * @return {@code true} if the interface declares a method with the given name
   * @throws EvaluationException if the provided class is not an interface
   */
  public static boolean checkIfMethodExists(Class<?> interfaceClass, String methodName) {
    if (!interfaceClass.isInterface()) {
      throw new EvaluationException(
          "Provided class '" + interfaceClass.getName() + "' must be an interface");
    }

    return Arrays.stream(interfaceClass.getDeclaredMethods())
        .map(Method::getName)
        .anyMatch(name -> name.equals(methodName));
  }

  /**
   * Returns the first element from a given {@link Set}, or {@code null} if empty.
   *
   * @param <T> element type
   * @param memberKeys set to inspect
   * @return first element of the set, or {@code null} when the set is null or empty
   */
  public static <T> T getFirstElement(Set<T> memberKeys) {
    if (memberKeys == null || memberKeys.isEmpty()) {
      return null;
    }
    return memberKeys.iterator().next();
  }
}
