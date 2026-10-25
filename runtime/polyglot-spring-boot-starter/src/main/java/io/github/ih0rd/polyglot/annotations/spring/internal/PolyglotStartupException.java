package io.github.ih0rd.polyglot.annotations.spring.internal;

/** Indicates that configured polyglot startup initialization failed. */
final class PolyglotStartupException extends IllegalStateException {

  PolyglotStartupException(Throwable cause) {
    super("Polyglot startup initialization failed", cause);
  }
}
