package io.github.ih0rd.adapter.context;

/**
 * Defines whether an executor owns the GraalVM {@code Context} it uses.
 *
 * <p>Ownership controls only lifecycle responsibility during executor {@code close()}; it does not
 * change the executor's serialized access model.
 */
public enum ContextOwnership {
  /** The executor closes the context when the executor is closed. */
  OWNED_BY_EXECUTOR,

  /** The caller retains responsibility for closing the context. */
  BORROWED
}
