package io.github.ih0rd.codegen;

/** Thrown when a parsed contract cannot be represented as valid Java source. */
public final class JavaContractValidationException extends IllegalArgumentException {

  /**
   * Creates a validation exception.
   *
   * @param message user-facing validation error
   */
  public JavaContractValidationException(String message) {
    super(message);
  }
}
