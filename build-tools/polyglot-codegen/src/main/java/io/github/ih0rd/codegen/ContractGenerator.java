package io.github.ih0rd.codegen;

import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

/**
 * High-level entry point for static contract generation.
 *
 * <p>Implementations accept a fully materialized {@link ScriptDescriptor}, delegate parsing, and
 * return the shared {@link ContractModel}.
 */
public interface ContractGenerator {

  /**
   * Generates a contract model from the given script descriptor.
   *
   * @param descriptor fully resolved script descriptor
   * @param config code generation configuration
   * @return generated contract model
   */
  ContractModel generate(ScriptDescriptor descriptor, CodegenConfig config);
}
