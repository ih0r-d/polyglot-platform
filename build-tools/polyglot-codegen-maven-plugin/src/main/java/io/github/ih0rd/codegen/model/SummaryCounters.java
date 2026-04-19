package io.github.ih0rd.codegen.model;

public record SummaryCounters(
    int supportedScripts,
    int generatedContracts,
    int writtenFiles,
    int skippedUnchangedFiles,
    int driftedFiles) {

  public static SummaryCounters empty() {
    return new SummaryCounters(0, 0, 0, 0, 0);
  }

  public SummaryCounters incrementSupportedScripts() {
    return new SummaryCounters(
        supportedScripts + 1,
        generatedContracts,
        writtenFiles,
        skippedUnchangedFiles,
        driftedFiles);
  }

  public SummaryCounters add(ScriptSummary perScript) {
    return new SummaryCounters(
        supportedScripts,
        generatedContracts + perScript.generatedContracts(),
        writtenFiles + perScript.writtenFiles(),
        skippedUnchangedFiles + perScript.skippedUnchangedFiles(),
        driftedFiles + perScript.driftedFiles());
  }
}
