package io.github.ih0rd.examples.dto;

import java.util.List;

/** Batch-relative anomaly diagnostics; scores are percentages, not probabilities. */
public record RiskAssessment(
    double threshold,
    List<Double> zScores,
    List<Integer> anomalyIndices,
    int anomalyCount,
    double riskScore,
    double qualityScore) {
  public RiskAssessment {
    zScores = List.copyOf(zScores);
    anomalyIndices = List.copyOf(anomalyIndices);
  }
}
