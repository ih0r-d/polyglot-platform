package io.github.ih0rd.polyglot.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public API element as experimental.
 *
 * <p>Experimental API can exist in published artifacts and may be useful in real applications, but
 * it is not yet treated as a stabilization target. Behavior, scope, and compatibility guarantees
 * may still be narrowed or clarified before a {@code 1.0.0} release.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
  ElementType.TYPE,
  ElementType.METHOD,
  ElementType.CONSTRUCTOR,
  ElementType.FIELD,
  ElementType.ANNOTATION_TYPE
})
public @interface ExperimentalApi {}
