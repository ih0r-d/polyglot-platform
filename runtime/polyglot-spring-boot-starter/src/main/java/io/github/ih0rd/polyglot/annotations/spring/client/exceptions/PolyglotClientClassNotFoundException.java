package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/** Thrown when the starter cannot load a configured polyglot client type by class name. */
public final class PolyglotClientClassNotFoundException extends PolyglotClientException {

  /**
   * Creates the exception.
   *
   * @param className missing class name
   * @param cause original lookup failure
   */
  public PolyglotClientClassNotFoundException(String className, Throwable cause) {
    super("Polyglot client class not found: " + className, cause);
  }
}
