package io.github.ih0rd.polyglot.model.config;

/**
 * Immutable configuration for contract generation.
 *
 * @param onlyIncludedMethods when {@code true}, only methods marked for explicit inclusion by the
 *     language parser are generated
 */
public record CodegenConfig(boolean onlyIncludedMethods) {}
