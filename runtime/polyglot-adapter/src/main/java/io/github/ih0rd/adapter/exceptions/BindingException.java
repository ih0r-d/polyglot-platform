package io.github.ih0rd.adapter.exceptions;

/**
 * Thrown when the adapter cannot bind a Java contract to a guest-language implementation.
 *
 * <p>Typical causes include a missing script, a missing exported Python contract, a method name
 * mismatch, or a guest-language value that exists but is not executable.
 */
public class BindingException extends EvaluationException {

  /**
   * Creates an exception with a message.
   *
   * @param message failure description
   */
  public BindingException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and cause.
   *
   * @param message failure description
   * @param cause original cause
   */
  public BindingException(String message, Throwable cause) {
    super(message, cause);
  }
}
