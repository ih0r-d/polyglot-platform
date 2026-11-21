package io.github.ih0rd.codegen.parsers;

import java.util.function.Function;

import io.github.ih0rd.codegen.compatibility.PythonContract;
import io.github.ih0rd.codegen.internal.python.PythonAstContracts;
import io.github.ih0rd.codegen.internal.python.PythonAstSession;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.LanguageParser;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

/** Official GraalPy AST parsing. Application modules and annotations are never executed. */
public final class PythonContractParser implements LanguageParser, AutoCloseable {
  private final PythonAstSession batch;
  private boolean closed;

  /** Creates a one-shot parser. Each operation owns and closes its AST interpreter. */
  public PythonContractParser() {
    batch = null;
  }

  private PythonContractParser(PythonAstSession batch) {
    this.batch = batch;
  }

  /** Opens an isolated lazy batch. Close it after analysis to release its Context and Engine. */
  public static PythonContractParser openBatch() {
    return new PythonContractParser(new PythonAstSession());
  }

  @Override
  public SupportedLanguage language() {
    return SupportedLanguage.PYTHON;
  }

  private synchronized <T> T using(Function<PythonAstSession, T> action) {
    if (closed) throw new IllegalStateException("Python contract parser is closed");
    if (batch != null) return action.apply(batch);
    try (var session = new PythonAstSession()) {
      return action.apply(session);
    }
  }

  @Override
  public ContractModel parse(ScriptDescriptor script, CodegenConfig config) {
    return using(session -> PythonAstContracts.generate(session, script, config));
  }

  /** Reads AST-derived calling metadata with conservative compatibility semantics. */
  public PythonContract parseCompatibility(ScriptDescriptor script) {
    return using(
        session -> PythonAstContracts.bindings(session, script, "compatibility").contract());
  }

  /** Reads AST-derived positional binding metadata for the selected runtime convention. */
  public PythonContract parseBindings(ScriptDescriptor script, Convention convention) {
    java.util.Objects.requireNonNull(convention, "convention");
    return using(
        session -> PythonAstContracts.bindings(session, script, convention.name()).contract());
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    if (batch != null) batch.close();
  }
}
