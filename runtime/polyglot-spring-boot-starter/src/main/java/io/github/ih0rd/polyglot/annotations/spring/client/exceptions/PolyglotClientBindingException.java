package io.github.ih0rd.polyglot.annotations.spring.client.exceptions;

/**
 * Thrown when a polyglot client contract resolves to a runtime executor but binding still fails.
 *
 * <p>Typical causes include missing scripts, missing exported members, or guest objects that cannot
 * be adapted to the requested Java interface.
 */
public final class PolyglotClientBindingException extends PolyglotClientException {

  /**
   * Creates the exception.
   *
   * @param clientType client interface type
   * @param language selected guest language
   * @param cause original binding failure
   */
  public PolyglotClientBindingException(String clientType, String language, Throwable cause) {
    super(
        "Failed to bind polyglot client '%s' for language '%s'".formatted(clientType, language),
        cause);
  }
}
