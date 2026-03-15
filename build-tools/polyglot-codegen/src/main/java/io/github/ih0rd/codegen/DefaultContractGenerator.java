package io.github.ih0rd.codegen;

import java.util.EnumMap;
import java.util.Map;

import io.github.ih0rd.codegen.parsers.JsContractParser;
import io.github.ih0rd.codegen.parsers.PythonContractParser;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.LanguageParser;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

/**
 * Default {@link ContractGenerator} implementation.
 *
 * <p>This class is a language-agnostic dispatcher that selects a {@link LanguageParser} based on
 * the script language and delegates the actual parsing work.
 */
public final class DefaultContractGenerator implements ContractGenerator {

  private static final Map<SupportedLanguage, LanguageParser> DEFAULT_PARSERS =
      Map.of(
          SupportedLanguage.PYTHON, new PythonContractParser(),
          SupportedLanguage.JS, new JsContractParser());

  private final Map<SupportedLanguage, LanguageParser> parsers;

  public DefaultContractGenerator() {
    this(DEFAULT_PARSERS);
  }

  DefaultContractGenerator(Map<SupportedLanguage, LanguageParser> parsers) {
    this.parsers = new EnumMap<>(parsers);
  }

  @Override
  public ContractModel generate(ScriptDescriptor descriptor, CodegenConfig config) {
    LanguageParser parser = parsers.get(descriptor.language());

    if (parser == null) {
      throw new IllegalStateException(
          "No parser registered for language: " + descriptor.language());
    }

    return parser.parse(descriptor, config);
  }
}
