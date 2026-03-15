package io.github.ih0rd.polyglot.annotations.spring.client;

import java.lang.annotation.*;

import org.springframework.context.annotation.Import;

/**
 * Enables scanning for interfaces annotated with {@link
 * io.github.ih0rd.polyglot.annotations.PolyglotClient}.
 *
 * <p>The Spring starter uses this annotation to discover Java contracts that should be bound to
 * guest-language implementations through the runtime adapter.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(PolyglotClientRegistrar.class)
public @interface EnablePolyglotClients {

  /** Base packages to scan for polyglot client interfaces. */
  String[] basePackages() default {};
}
