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
   */
  DEFAULT
}
