package io.github.ih0rd.adapter.exceptions;

/**
 * Base runtime exception for failures raised by the polyglot adapter.
 *
 * <p>This type groups binding, script resolution, and guest invocation failures under one unchecked
 * exception hierarchy that callers can catch at integration boundaries.
 */
public class EvaluationException extends RuntimeException {

  /**
   * Creates an exception with a human-readable message.
   *
   * @param message failure description
   */
  public EvaluationException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and root cause.
   *
   * @param message failure description
   * @param cause original cause
   */
  public EvaluationException(String message, Throwable cause) {
    super(message, cause);
  }
}
