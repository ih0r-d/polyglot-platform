package io.github.ih0rd.codegen.compatibility;

/** Result of static Java-facing contract analysis; BREAKING takes precedence over UNKNOWN. */
public enum CompatibilityOutcome {
  COMPATIBLE,
  UNKNOWN,
  BREAKING
}
