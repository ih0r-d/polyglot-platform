package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/// Thrown when a polyglot client class cannot be loaded by the classloader.
public final class PolyglotClientClassNotFoundException extends PolyglotClientException {

  public PolyglotClientClassNotFoundException(String className, Throwable cause) {
    super("Polyglot client class not found: " + className, cause);
  }
}
