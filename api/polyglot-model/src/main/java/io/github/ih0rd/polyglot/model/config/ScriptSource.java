package io.github.ih0rd.polyglot.model.config;

import java.io.IOException;
import java.io.Reader;

import io.github.ih0rd.polyglot.SupportedLanguage;

/**
 * Abstraction for resolving and opening guest-language scripts.
 *
 * <p>This SPI keeps the runtime independent from the storage mechanism used by an application.
 * Scripts can come from the classpath, local files, Spring resources, memory, or a custom source
 * without changing executor code.
 *
 * <p>Script names are logical names, usually without a file extension. Implementations are
 * responsible for applying the language-specific location rules they document, such as {@code
 * python/recommendation_service.py} for Python classpath resources.
 *
 * <p>Each call to {@link #open(SupportedLanguage, String)} must return a fresh {@link Reader};
 * callers may close it after the script has been loaded.
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
