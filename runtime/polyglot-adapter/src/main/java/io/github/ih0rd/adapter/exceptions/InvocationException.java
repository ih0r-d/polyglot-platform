package io.github.ih0rd.adapter.exceptions;

/// # InvocationException
///
/// Thrown when an error occurs while executing guest code.
public class InvocationException extends EvaluationException {

  public InvocationException(String message) {
    super(message);
  }

  public InvocationException(String message, Throwable cause) {
    super(message, cause);
  }
}
