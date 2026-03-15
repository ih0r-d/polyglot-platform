package io.github.ih0rd.polyglot.model.types;

/**
 * Map-like type.
 *
 * @param keyType key type
 * @param valueType value type
 */
public record PolyMap(PolyType keyType, PolyType valueType) implements PolyType {}
