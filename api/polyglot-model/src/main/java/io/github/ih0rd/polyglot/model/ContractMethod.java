package io.github.ih0rd.polyglot.model;

import java.util.List;

import io.github.ih0rd.polyglot.model.types.PolyType;

/**
 * One callable operation on a generated contract.
 *
 * @param name method name exposed to Java callers
 * @param params ordered parameters
 * @param returnType canonical return type
 */
public record ContractMethod(String name, List<ContractParam> params, PolyType returnType) {

  public ContractMethod {
    params = List.copyOf(params);
  }
}
