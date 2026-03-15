package io.github.ih0rd.polyglot.model.config;

import java.io.IOException;
import java.io.Reader;

import io.github.ih0rd.polyglot.SupportedLanguage;

/**
 * Abstraction for resolving and opening polyglot scripts.
 *
 * <p>This SPI defines where scripts come from without forcing the runtime to know about the
 * filesystem, classpath, Spring resources, or any other storage mechanism.
 *
 * <p>Core executors rely on this contract exclusively and must not perform direct I/O themselves.
 */
public interface ScriptSource {

  /**
   * Checks whether a script with the given logical name exists for the specified language.
   *
   * @param language target script language
   * @param scriptName logical script name
   * @return {@code true} if the script can be resolved, {@code false} otherwise
   */
  boolean exists(SupportedLanguage language, String scriptName);

  /**
   * Opens a fresh {@link Reader} for the specified script.
   *
   * @param language target script language
   * @param scriptName logical script name
   * @return reader for the script content
   * @throws IOException if an I/O error occurs while opening the script
   */
  Reader open(SupportedLanguage language, String scriptName) throws IOException;
}
