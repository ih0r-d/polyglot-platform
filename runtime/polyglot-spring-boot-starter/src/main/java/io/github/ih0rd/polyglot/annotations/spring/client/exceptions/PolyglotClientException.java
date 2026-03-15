package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/// Base exception for polyglot client configuration errors.
///
/// Thrown during Spring context initialization
/// when a {@code @PolyglotClient} contract is violated.
public class PolyglotClientException extends RuntimeException {

  public PolyglotClientException(String message) {
    super(message);
  }

  public PolyglotClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
