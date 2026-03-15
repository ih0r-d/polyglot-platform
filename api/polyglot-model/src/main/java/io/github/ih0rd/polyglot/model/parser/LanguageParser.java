package io.github.ih0rd.polyglot.model.parser;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;

/**
 * SPI for language-specific static parsers.
 *
 * <p>Implementations turn a language-specific script representation into the shared {@link
 * ContractModel}.
 */
public interface LanguageParser {

  /** Returns the language handled by this parser. */
  SupportedLanguage language();

  /**
   * Parses one script into the shared contract model.
   *
   * @param script script descriptor containing language, source, and optional file name
   * @param config code generation configuration
   * @return parsed contract model
   */
  ContractModel parse(ScriptDescriptor script, CodegenConfig config);
}
