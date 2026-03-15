package io.github.ih0rd.polyglot.annotations.spring.script;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/**
 * Spring-backed {@link ScriptSource} implementation bound to a single guest language.
 *
 * <p>This adapter lets the runtime layer resolve scripts through Spring's {@link ResourceLoader}
 * while keeping Spring-specific resource handling out of the core runtime module.
 */
public final class SpringResourceScriptSource implements ScriptSource {

  private final ResourceLoader resourceLoader;
  private final SupportedLanguage language;
  private final String basePath;

  /**
   * Creates a language-bound Spring resource script source.
   *
   * @param resourceLoader Spring resource loader
   * @param language guest language handled by this source
   * @param basePath base resource location
   */
  public SpringResourceScriptSource(
      ResourceLoader resourceLoader, SupportedLanguage language, String basePath) {

    if (resourceLoader == null) {
      throw new IllegalArgumentException("ResourceLoader must not be null");
    }
    if (language == null) {
      throw new IllegalArgumentException("SupportedLanguage must not be null");
    }

    this.resourceLoader = resourceLoader;
    this.language = language;
    this.basePath = normalizeBase(basePath);
  }

  /**
   * Checks whether a script exists for the requested language and logical name.
   *
   * @param language requested language
   * @param scriptName logical script name without extension
   * @return {@code true} if the resource exists and the language matches
   */
  @Override
  public boolean exists(SupportedLanguage language, String scriptName) {
    return this.language == language && resolve(scriptName).exists();
  }

  /**
   * Opens the requested script as a UTF-8 {@link Reader}.
   *
   * @param language requested language
   * @param scriptName logical script name without extension
   * @return reader for the script content
   * @throws IOException if the resource cannot be read
   */
  @Override
  public Reader open(SupportedLanguage language, String scriptName) throws IOException {

    if (this.language != language) {
      throw new IllegalArgumentException(
          "ScriptSource bound to " + this.language + ", but requested " + language);
    }

    return new InputStreamReader(resolve(scriptName).getInputStream(), StandardCharsets.UTF_8);
  }

  /** Resolves a Spring {@link Resource} for the given logical script name. */
  private Resource resolve(String scriptName) {
    return resourceLoader.getResource(basePath + scriptName + language.ext());
  }

  /** Normalizes the base path so resource resolution always appends to a trailing slash. */
  private static String normalizeBase(String base) {
    if (base == null || base.isBlank()) {
      throw new IllegalArgumentException("Script base location must not be null or blank");
    }
    return base.endsWith("/") ? base : base + "/";
  }
}
