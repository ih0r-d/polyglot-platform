package io.github.ih0rd.codegen.model;

import java.util.List;

public record ScriptSummary(
    int generatedContracts,
    int writtenFiles,
    int skippedUnchangedFiles,
    int driftedFiles,
    List<String> driftMessages) {

  public ScriptSummary {
    driftMessages = List.copyOf(driftMessages);
  }
}
