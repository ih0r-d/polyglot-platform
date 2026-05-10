package io.github.ih0rd.codegen.model;

import java.util.List;

/**
 * Aggregated result of processing a single script file.
 *
 * @param generatedContracts number of contracts generated from the script
 * @param writtenFiles number of Java files written to disk
 * @param skippedUnchangedFiles number of files skipped because content was unchanged
 * @param driftedFiles number of files whose on-disk content differs from generated output
 * @param driftMessages human-readable drift descriptions
 */
public record ScriptSummary(
    int generatedContracts,
    int writtenFiles,
    int skippedUnchangedFiles,
    int driftedFiles,
    List<String> driftMessages) {

  /** Defensive copy of drift messages on construction. */
  public ScriptSummary {
    driftMessages = List.copyOf(driftMessages);
  }
}
