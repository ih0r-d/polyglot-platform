package io.github.ih0rd.polyglot.model;

import io.github.ih0rd.polyglot.model.types.PolyType;

/**
 * Parameter definition for a generated contract method.
 *
 * @param name parameter name
 * @param type canonical parameter type
 */
public record ContractParam(String name, PolyType type) {}
