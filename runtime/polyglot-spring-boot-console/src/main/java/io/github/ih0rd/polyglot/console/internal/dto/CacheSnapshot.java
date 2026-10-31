package io.github.ih0rd.polyglot.console.internal.dto;

import org.jspecify.annotations.Nullable;

/** Cache measurements; unknown sizes/counters stay null, never fabricated as zero. */
public record CacheSnapshot(
    String language,
    String cache,
    @Nullable Double hits,
    @Nullable Double misses,
    @Nullable Double invalidatedEntries,
    @Nullable Double reloadInvalidatedEntries,
    @Nullable Double size) {}
