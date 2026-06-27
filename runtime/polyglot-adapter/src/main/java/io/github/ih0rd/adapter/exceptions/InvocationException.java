package io.github.ih0rd.adapter.exceptions;

/**
 * Thrown when guest code execution fails after the adapter has already resolved the target script
 * and binding.
 *
 * <p>Use this exception to distinguish runtime failures raised by Python or the GraalVM execution
 * layer from binding problems such as missing exports.
 */
public class InvocationException extends EvaluationException {

  /**
   * Creates an exception with a message.
   *
   * @param message failure description
   */
  public InvocationException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and cause.
   *
   * @param message failure description
   * @param cause original guest/runtime failure
   */
  public InvocationException(String message, Throwable cause) {
    super(message, cause);
  }
}
