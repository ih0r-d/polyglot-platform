package io.github.ih0rd.polyglot.annotations;

import java.lang.annotation.*;

import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;

/**
 * Marks a Java interface as a contract that should be backed by a polyglot runtime implementation.
 *
 * <p>The annotation is consumed by the Spring Boot starter when {@code @EnablePolyglotClients} is
 * active.
 *
 * <p>Language resolution rules:
 *
 * <ul>
 *   <li>if {@link #languages()} is empty, exactly one executor must be available
 *   <li>if one language is specified, that executor is used
 *   <li>if multiple languages are specified, client creation fails
 * </ul>
 *
 * <p>Binding currently follows {@link Convention#DEFAULT}.
 *
 * <p>Python is the primary stabilization target. JavaScript-backed clients are currently treated as
 * experimental and should be used with a narrower support expectation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PolyglotClient {

  /**
   * Allowed guest languages for this client.
   *
   * <p>An empty array enables automatic resolution based on the executors available at runtime.
   *
   * <p>When using {@link SupportedLanguage#JS}, treat the client as part of the experimental
   * JavaScript support surface.
   *
   * @return explicitly allowed guest languages, or an empty array for automatic resolution
   */
  SupportedLanguage[] languages() default {};

  /**
   * Binding convention used by the adapter.
   *
   * <p>{@link Convention#DEFAULT} preserves the historical repository behavior.
   *
   * <p>For Python, {@link Convention#DEFAULT} and {@link Convention#BY_INTERFACE_EXPORT} currently
   * use the same export-based invocation path, but {@code BY_INTERFACE_EXPORT} is the explicit
   * convention name while {@code DEFAULT} remains the backward-compatible default.
   *
   * @return binding convention used when resolving guest-language members
   */
  Convention convention() default Convention.DEFAULT;
}
