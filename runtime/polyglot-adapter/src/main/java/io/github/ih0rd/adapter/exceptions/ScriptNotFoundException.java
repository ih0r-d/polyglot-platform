package io.github.ih0rd.adapter.exceptions;

/**
 * Thrown when a requested guest-language script cannot be resolved from the configured {@code
 * ScriptSource}.
 */
public class ScriptNotFoundException extends EvaluationException {

  /**
   * Creates an exception with a message.
   *
   * @param message failure description
   */
  public ScriptNotFoundException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and cause.
   *
   * @param message failure description
   * @param cause original resolution failure
   */
  public ScriptNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
