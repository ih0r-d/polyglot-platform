package io.github.ih0rd.polyglot.model.types;

/** Fallback type used when a parser cannot infer a more specific portable type. */
public record PolyUnknown() implements PolyType {}
