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
public final class DefaultContractGenerator implements ContractGenerator, AutoCloseable {

  private static final Map<SupportedLanguage, LanguageParser> DEFAULT_PARSERS =
      Map.of(
          SupportedLanguage.PYTHON, new PythonContractParser(),
          SupportedLanguage.JS, new JsContractParser());

  private final Map<SupportedLanguage, LanguageParser> parsers;
  private final boolean ownsBatch;
  private boolean closed;

  /** Creates a generator with the built-in parser set. */
  public DefaultContractGenerator() {
    this(DEFAULT_PARSERS);
  }

  DefaultContractGenerator(Map<SupportedLanguage, LanguageParser> parsers) {
    this(parsers, false);
  }

  private DefaultContractGenerator(
      Map<SupportedLanguage, LanguageParser> parsers, boolean ownsBatch) {
    this.parsers = new EnumMap<>(parsers);
    this.ownsBatch = ownsBatch;
  }

  /** Opens an isolated lazy Python AST batch; close after processing the build's sources. */
  public static DefaultContractGenerator openBatch() {
    return new DefaultContractGenerator(
        Map.of(
            SupportedLanguage.PYTHON,
            PythonContractParser.openBatch(),
            SupportedLanguage.JS,
            new JsContractParser()),
        true);
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    if (ownsBatch) ((PythonContractParser) parsers.get(SupportedLanguage.PYTHON)).close();
  }

  @Override
  public synchronized ContractModel generate(ScriptDescriptor descriptor, CodegenConfig config) {
    if (closed) throw new IllegalStateException("Contract generator is closed");
    LanguageParser parser = parsers.get(descriptor.language());

    if (parser == null) {
      throw new IllegalStateException(
          "No parser registered for language: " + descriptor.language());
    }

    return parser.parse(descriptor, config);
  }
}
