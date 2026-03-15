package io.github.ih0rd.polyglot.model;

import java.util.List;

/**
 * Root model representing the contracts discovered from a single script input.
 *
 * @param classes exported contracts discovered in the source
 */
public record ContractModel(List<ContractClass> classes) {

  public ContractModel {
    classes = List.copyOf(classes);
  }
}
