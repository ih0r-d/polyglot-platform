package io.github.ih0rd.polyglot.model;

import java.util.List;

/**
 * One exported guest-language contract discovered during parsing.
 *
 * @param name contract name, typically the exported API name
 * @param methods operations exposed by the contract
 */
public record ContractClass(String name, List<ContractMethod> methods) {

  public ContractClass {
    methods = List.copyOf(methods);
  }
}
