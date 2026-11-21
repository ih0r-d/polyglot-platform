package io.github.ih0rd.codegen.compatibility;

/** A source-located compatibility finding. Locations for removals refer to the baseline. */
public record CompatibilityDiagnostic(
    String file,
    int line,
    String symbol,
    CompatibilityOutcome outcome,
    Category category,
    String explanation) {

  /** Stable categories for build reports. */
  public enum Category {
    MODULE_REMOVED,
    FUNCTION_REMOVED,
    POSITIONAL_ARITY,
    REQUIRED_KEYWORD,
    PARAMETER_BINDING,
    DEFAULT_VALUE,
    TYPE_ANNOTATION,
    UNSUPPORTED_SYNTAX
  }

  /** Returns a deterministic, single-line build diagnostic. */
  public String format() {
    return file
        + ":"
        + line
        + ": "
        + outcome
        + " ["
        + category
        + "] "
        + symbol
        + ": "
        + explanation;
  }
}
