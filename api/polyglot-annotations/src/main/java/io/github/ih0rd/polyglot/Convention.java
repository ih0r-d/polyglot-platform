package io.github.ih0rd.polyglot;

/**
 * Defines the naming and binding convention between a Java interface and its guest-language
 * implementation.
 */
public enum Convention {

  /**
   * Default repository convention.
   *
   * <p>Current rules:
   *
   * <ul>
   *   <li>the script name is derived from the Java interface simple name in snake case
   *   <li>the exported Python contract name must match the Java interface simple name
   *   <li>JavaScript functions must match the Java method names
   * </ul>
   *
   * <p>This value preserves the repository's historical default behavior. For Python, it uses the
   * interface-export model and now performs stricter validation by checking that required interface
   * methods are actually resolvable during {@code validateBinding(...)}.
   */
  DEFAULT,

  /**
   * Explicit interface-export convention.
   *
   * <p>The guest implementation is resolved via an exported value named after the Java interface.
   *
   * <p>For Python, this is the explicit form of the existing export-based model that {@link
   * #DEFAULT} already uses for backward compatibility.
   */
  BY_INTERFACE_EXPORT,

  /**
   * Method-name convention.
   *
   * <p>Each Java interface method is resolved directly from guest-language bindings by method name.
   */
  BY_METHOD_NAME
}
