package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/// Thrown when a polyglot client fails binding validation.
///
/// This usually means:
/// - script not found
/// - exported class/function missing
/// - guest code cannot be instantiated
public final class PolyglotClientBindingException extends PolyglotClientException {

  public PolyglotClientBindingException(String clientType, String language, Throwable cause) {
    super(
        "Failed to bind polyglot client '%s' for language '%s'".formatted(clientType, language),
        cause);
  }
}
