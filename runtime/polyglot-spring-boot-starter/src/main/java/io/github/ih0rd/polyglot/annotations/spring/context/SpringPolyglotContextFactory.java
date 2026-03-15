package io.github.ih0rd.polyglot.annotations.spring.context;

import org.graalvm.polyglot.Context;
import org.springframework.beans.factory.ObjectProvider;

import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.polyglot.SupportedLanguage;

/**
 * Spring-aware factory for creating language-specific contexts.
 *
 * <p>The factory delegates the actual default configuration to {@link PolyglotHelper} and then
 * applies all registered {@link PolyglotContextCustomizer} instances in order.
 */
public final class SpringPolyglotContextFactory {

  private final ObjectProvider<PolyglotContextCustomizer> customizers;

  public SpringPolyglotContextFactory(ObjectProvider<PolyglotContextCustomizer> customizers) {
    this.customizers = customizers;
  }

  /**
   * Creates a new context for the requested language and applies all registered customizers.
   *
   * @param language target guest language
   * @return initialized context
   */
  public Context create(SupportedLanguage language) {
    return PolyglotHelper.newContext(
        language,
        builder -> customizers.orderedStream().forEach(c -> c.customize(language, builder)));
  }
}
