package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/** Thrown when {@code @PolyglotClient} is declared on a type other than a Java interface. */
public final class InvalidPolyglotClientTypeException extends PolyglotClientException {

  /**
   * Creates the exception for the offending type.
   *
   * @param className invalid target type
   */
  public InvalidPolyglotClientTypeException(String className) {
    super(
        "@PolyglotClient can only be applied to interfaces. Invalid target: %s"
            .formatted(className));
  }
}
