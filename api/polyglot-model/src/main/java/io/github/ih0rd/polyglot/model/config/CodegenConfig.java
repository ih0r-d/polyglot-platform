package io.github.ih0rd.polyglot.model.config;

/**
 * Immutable configuration for contract generation.
 *
 * @param onlyIncludedMethods when {@code true}, only methods marked for explicit inclusion by the
 *     language parser are generated
 * @param strictMode when {@code true}, generation fails if unresolved/unknown types are detected
 */
public record CodegenConfig(boolean onlyIncludedMethods, boolean strictMode) {

  /**
   * Creates configuration with strict mode disabled.
   *
   * @param onlyIncludedMethods include-only flag
   */
  public CodegenConfig(boolean onlyIncludedMethods) {
    this(onlyIncludedMethods, false);
  }
}
