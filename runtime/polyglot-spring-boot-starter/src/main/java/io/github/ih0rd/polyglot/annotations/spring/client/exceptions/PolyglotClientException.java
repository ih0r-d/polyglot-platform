package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/**
 * Base exception for Spring polyglot client configuration and resolution errors.
 *
 * <p>These exceptions are typically raised during application context initialization when a
 * {@code @PolyglotClient} contract cannot be resolved safely.
 */
public class PolyglotClientException extends RuntimeException {

  /**
   * Creates an exception with a message.
   *
   * @param message failure description
   */
  public PolyglotClientException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and cause.
   *
   * @param message failure description
   * @param cause original cause
   */
  public PolyglotClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
