package io.github.ih0rd.polyglot.model.types;

/**
 * List-like type.
 *
 * @param elementType element type
 */
public record PolyList(PolyType elementType) implements PolyType {}
