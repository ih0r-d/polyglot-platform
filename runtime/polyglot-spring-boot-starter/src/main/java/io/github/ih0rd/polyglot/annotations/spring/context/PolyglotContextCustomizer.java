package io.github.ih0rd.polyglot.annotations.spring.context;

import org.graalvm.polyglot.Context;
import org.springframework.core.Ordered;

import io.github.ih0rd.polyglot.SupportedLanguage;

/**
 * Callback for customizing a GraalVM {@link Context.Builder} before the starter creates an
 * executor.
 */
public interface PolyglotContextCustomizer extends Ordered {

  /**
   * Applies language-specific customization to the builder.
   *
   * @param language target guest language
   * @param builder context builder to customize
   */
  void customize(SupportedLanguage language, Context.Builder builder);

  @Override
  default int getOrder() {
    return 0;
  }
}
