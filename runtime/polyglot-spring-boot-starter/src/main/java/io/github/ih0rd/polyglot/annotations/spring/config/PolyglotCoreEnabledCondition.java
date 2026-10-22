package io.github.ih0rd.polyglot.annotations.spring.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Applies the global starter switch to language auto-configurations. */
final class PolyglotCoreEnabledCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String enabled = context.getEnvironment().getProperty("polyglot.core.enabled");
    return enabled == null || "true".equalsIgnoreCase(enabled);
  }
}
