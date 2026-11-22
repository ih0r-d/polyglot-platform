package io.github.ih0rd.codegen;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import io.github.ih0rd.codegen.compatibility.PythonContract;
import io.github.ih0rd.codegen.internal.python.PythonAstContracts;
import io.github.ih0rd.codegen.internal.python.PythonAstSession;
import io.github.ih0rd.codegen.parsers.JsContractParser;
import io.github.ih0rd.codegen.parsers.PythonContractParser;
import io.github.ih0rd.polyglot.Convention;
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
  private final PythonAstSession batch;
  private boolean closed;

  record PythonBinding(PythonContract contract, Set<String> exports) {}

  /** Creates a generator with the built-in parser set. */
  public DefaultContractGenerator() {
    this(DEFAULT_PARSERS);
  }

  DefaultContractGenerator(Map<SupportedLanguage, LanguageParser> parsers) {
    this(parsers, null);
  }

  private DefaultContractGenerator(
      Map<SupportedLanguage, LanguageParser> parsers, PythonAstSession batch) {
    this.parsers = new EnumMap<>(parsers);
    this.batch = batch;
  }

  /** Opens an isolated lazy Python AST batch; close after processing the build's sources. */
  public static DefaultContractGenerator openBatch() {
    return new DefaultContractGenerator(DEFAULT_PARSERS, new PythonAstSession());
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    if (batch != null) batch.close();
  }

  synchronized PythonBinding analyzePythonBindings(ScriptDescriptor script, Convention convention) {
    if (closed || batch == null)
      throw new IllegalStateException("Python contract batch is not open");
    var binding = PythonAstContracts.bindings(batch, script, convention);
    return new PythonBinding(binding.contract(), binding.exports());
  }

  @Override
  public synchronized ContractModel generate(ScriptDescriptor descriptor, CodegenConfig config) {
    if (closed) throw new IllegalStateException("Contract generator is closed");
    if (batch != null && descriptor.language() == SupportedLanguage.PYTHON)
      return PythonAstContracts.generate(batch, descriptor, config);
    LanguageParser parser = parsers.get(descriptor.language());

    if (parser == null) {
      throw new IllegalStateException(
          "No parser registered for language: " + descriptor.language());
    }

    return parser.parse(descriptor, config);
  }
}
