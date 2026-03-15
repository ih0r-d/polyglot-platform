package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/// Thrown when a polyglot client cannot resolve a single executor.
///
/// Common reasons:
/// - no executors available
/// - multiple executors available and language not specified
/// - requested executor is missing
public final class PolyglotClientResolutionException extends PolyglotClientException {

  public PolyglotClientResolutionException(String message) {
    super(message);
  }
}
