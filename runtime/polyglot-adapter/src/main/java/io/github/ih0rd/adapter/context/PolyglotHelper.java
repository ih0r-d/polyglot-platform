package io.github.ih0rd.adapter.context;

import java.util.Objects;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;

import io.github.ih0rd.polyglot.SupportedLanguage;

/**
 * Helper for creating language-specific GraalVM contexts with the repository's default options.
 *
 * <p>The helper centralizes context initialization so executors and Spring integration use the same
 * defaults.
 */
public final class PolyglotHelper {

  private static final String OPTION_FALSE = "false";

  private static final String ENGINE_WARN_INTERPRETER_ONLY = "engine.WarnInterpreterOnly";

  private static final String PYTHON_WARN_EXPERIMENTAL_FEATURES = "python.WarnExperimentalFeatures";

  private PolyglotHelper() {}

  /**
   * Creates and initializes a new context for the given language.
   *
   * @param language guest language
   * @param applyRecommendedDefaults whether the repository's recommended language defaults should
   *     be applied
   * @param customizer optional builder customizer
   * @return initialized context
   */
  public static Context newContext(
      SupportedLanguage language,
      boolean applyRecommendedDefaults,
      Consumer<Context.Builder> customizer) {

    Objects.requireNonNull(language, "language must not be null");

    Context.Builder builder;

    switch (language) {
      case PYTHON -> {
        VirtualFileSystem vfs =
            VirtualFileSystem.newBuilder().resourceDirectory("org.graalvm.python.vfs").build();

        builder = GraalPyResources.contextBuilder(vfs);
        if (applyRecommendedDefaults) {
          builder =
              builder
                  .allowAllAccess(true)
                  .allowExperimentalOptions(true)
                  .option(ENGINE_WARN_INTERPRETER_ONLY, OPTION_FALSE)
                  .option(PYTHON_WARN_EXPERIMENTAL_FEATURES, OPTION_FALSE);
        }
      }

      case JS -> {
        builder =
            Context.newBuilder(language.id())
                .allowAllAccess(true)
                .allowExperimentalOptions(true)
                .option(ENGINE_WARN_INTERPRETER_ONLY, OPTION_FALSE);
      }

      default -> throw new IllegalStateException("Unsupported language: " + language);
    }

    if (customizer != null) {
      customizer.accept(builder);
    }

    Context context = builder.build();
    context.initialize(language.id());
    return context;
  }

  /**
   * Creates and initializes a new context with the default repository configuration.
   *
   * @param language guest language
   * @return initialized context
   */
  public static Context newContext(SupportedLanguage language) {
    return newContext(language, true, null);
  }

  /**
   * Creates and initializes a new context using the repository's recommended defaults.
   *
   * @param language guest language
   * @param customizer optional builder customizer
   * @return initialized context
   */
  public static Context newContext(
      SupportedLanguage language, Consumer<Context.Builder> customizer) {
    return newContext(language, true, customizer);
  }
}
