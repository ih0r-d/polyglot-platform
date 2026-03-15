package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/// Thrown when {@code @PolyglotClient} is used on an invalid Java type.
///
/// Valid targets:
/// - Java interfaces only
public final class InvalidPolyglotClientTypeException extends PolyglotClientException {

  public InvalidPolyglotClientTypeException(String className) {
    super(
        "@PolyglotClient can only be applied to interfaces. Invalid target: %s"
            .formatted(className));
  }
}
