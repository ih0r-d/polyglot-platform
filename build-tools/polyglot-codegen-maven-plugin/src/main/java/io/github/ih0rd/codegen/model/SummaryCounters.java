package io.github.ih0rd.codegen.model;

/**
 * Accumulated counters across all processed scripts in a single codegen run.
 *
 * @param supportedScripts total number of scripts encountered
 * @param generatedContracts total contracts generated
 * @param writtenFiles total Java files written to disk
 * @param skippedUnchangedFiles total files skipped because content was unchanged
 * @param driftedFiles total files whose on-disk content differs from generated output
 */
public record SummaryCounters(
    int supportedScripts,
    int generatedContracts,
    int writtenFiles,
    int skippedUnchangedFiles,
    int driftedFiles) {

  /**
   * Returns a zero-initialized counter set.
   *
   * @return empty counters
   */
  public static SummaryCounters empty() {
    return new SummaryCounters(0, 0, 0, 0, 0);
  }

  /**
   * Returns a copy with {@code supportedScripts} incremented by one.
   *
   * @return updated counters
   */
  public SummaryCounters incrementSupportedScripts() {
    return new SummaryCounters(
        supportedScripts + 1,
        generatedContracts,
        writtenFiles,
        skippedUnchangedFiles,
        driftedFiles);
  }

  /**
   * Merges per-script counters into this accumulator.
   *
   * @param perScript counters from a single script
   * @return updated counters
   */
  public SummaryCounters add(ScriptSummary perScript) {
    return new SummaryCounters(
        supportedScripts,
        generatedContracts + perScript.generatedContracts(),
        writtenFiles + perScript.writtenFiles(),
        skippedUnchangedFiles + perScript.skippedUnchangedFiles(),
        driftedFiles + perScript.driftedFiles());
  }
}
