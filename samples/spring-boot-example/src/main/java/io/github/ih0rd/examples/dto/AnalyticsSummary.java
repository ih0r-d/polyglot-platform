package io.github.ih0rd.examples.dto;

/** Detached NumPy descriptive statistics; deviation uses the population denominator. */
public record AnalyticsSummary(
    int count,
    double mean,
    double median,
    double standardDeviation,
    double minimum,
    double maximum,
    double p05,
    double p95,
    String numpyVersion) {}
