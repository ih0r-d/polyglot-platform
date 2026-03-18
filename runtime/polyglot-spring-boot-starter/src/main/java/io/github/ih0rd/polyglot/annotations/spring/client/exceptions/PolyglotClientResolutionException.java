package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/**
 * Thrown when the starter cannot resolve exactly one executor for a polyglot client contract.
 *
 * <p>This usually means that no executors are available, multiple executors are available without
 * an explicit language selection, or the requested language is disabled.
 */
public final class PolyglotClientResolutionException extends PolyglotClientException {

  /**
   * Creates the exception.
   *
   * @param message failure description
   */
  public PolyglotClientResolutionException(String message) {
    super(message);
  }
}
