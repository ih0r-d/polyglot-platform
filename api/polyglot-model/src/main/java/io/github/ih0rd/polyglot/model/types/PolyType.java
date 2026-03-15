package io.github.ih0rd.polyglot.model.types;

/** Marker interface for the portable type system shared by parsers and generators. */
public sealed interface PolyType
    permits PolyPrimitive, PolyList, PolyMap, PolyObject, PolyUnion, PolyUnknown {}
