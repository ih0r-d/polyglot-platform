package io.github.ih0rd.codegen.parsers;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.LanguageParser;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

/**
 * JavaScript contract parser stub.
 *
 * <p><strong>Responsibilities:</strong>
 *
 * <ul>
 *   <li>Act as a placeholder for future JavaScript AST-based code generation
 *   <li>Define an explicit extension point for JavaScript support
 * </ul>
 *
 * <p><strong>Design notes:</strong>
 *
 * <ul>
 *   <li>JavaScript code generation is not supported in the current version
 *   <li>This class intentionally contains no parsing logic
 *   <li>No assumptions are made about JavaScript module systems or exports
 *   <li>{@link ScriptDescriptor} is accepted for API symmetry only
 * </ul>
 */
public final class JsContractParser implements LanguageParser {

  /** Creates the JavaScript parser placeholder. */
  public JsContractParser() {}

  @Override
  public SupportedLanguage language() {
    return SupportedLanguage.JS;
  }

  @Override
  public ContractModel parse(ScriptDescriptor scriptDescriptor, CodegenConfig codegenConfig) {
    throw new UnsupportedOperationException("JavaScript contract generation is not supported yet");
  }
}
