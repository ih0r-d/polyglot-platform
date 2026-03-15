package io.github.ih0rd.adapter.exceptions;

/// # BindingException
///
/// Thrown when a Java interface cannot be bound to a guest implementation,
/// e.g. missing guest class, method, or non-callable member.
public class BindingException extends EvaluationException {

  public BindingException(String message) {
    super(message);
  }

  public BindingException(String message, Throwable cause) {
    super(message, cause);
  }
}
