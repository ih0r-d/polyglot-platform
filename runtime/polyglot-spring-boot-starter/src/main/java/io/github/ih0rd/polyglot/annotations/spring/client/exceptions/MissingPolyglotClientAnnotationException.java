package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/// Thrown when a polyglot client bean is being created,
/// but the target type does not have
/// {@link io.github.ih0rd.polyglot.annotations.PolyglotClient}.
public final class MissingPolyglotClientAnnotationException extends PolyglotClientException {

  public MissingPolyglotClientAnnotationException(String className) {
    super("Missing @PolyglotClient on " + className);
  }
}
