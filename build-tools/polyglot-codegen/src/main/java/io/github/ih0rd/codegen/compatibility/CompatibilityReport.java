package io.github.ih0rd.codegen.compatibility;

import java.util.Comparator;
import java.util.List;

/** Immutable report, ordered by file, line, symbol, category and explanation. */
public record CompatibilityReport(List<CompatibilityDiagnostic> diagnostics) {
  public CompatibilityReport {
    diagnostics =
        List.copyOf(
            diagnostics.stream()
                .sorted(
                    Comparator.comparing(CompatibilityDiagnostic::file)
                        .thenComparingInt(CompatibilityDiagnostic::line)
                        .thenComparing(CompatibilityDiagnostic::symbol)
                        .thenComparing(d -> d.category().name())
                        .thenComparing(CompatibilityDiagnostic::explanation))
                .toList());
  }

  /** Returns BREAKING when any confirmed break exists, otherwise UNKNOWN or COMPATIBLE. */
  public CompatibilityOutcome outcome() {
    return diagnostics.stream()
        .map(CompatibilityDiagnostic::outcome)
        .max(Comparator.naturalOrder())
        .orElse(CompatibilityOutcome.COMPATIBLE);
  }
}
