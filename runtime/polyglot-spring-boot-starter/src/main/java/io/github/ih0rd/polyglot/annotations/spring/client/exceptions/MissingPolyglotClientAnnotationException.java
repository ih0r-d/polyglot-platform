package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/**
 * Thrown when the starter is asked to create a polyglot client bean for a type that is not
 * annotated with {@code @PolyglotClient}.
 */
public final class MissingPolyglotClientAnnotationException extends PolyglotClientException {

  /**
   * Creates the exception for the offending type.
   *
   * @param className target type missing the annotation
   */
  public MissingPolyglotClientAnnotationException(String className) {
    super("Missing @PolyglotClient on " + className);
  }
}
