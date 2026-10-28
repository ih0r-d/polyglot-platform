package io.github.ih0rd.polyglot.console.internal.dto;

import org.jspecify.annotations.Nullable;

/**
 * A known gauge; null distinguishes unavailable readings from real zero values.
 *
 * @param name allowlisted meter name
 * @param language language identifier, or empty for global gauges
 * @param unit declared display unit
 * @param value observed finite nonnegative value, or null when unavailable/ambiguous
 */
public record RuntimeMetricSnapshot(
    String name, String language, String unit, @Nullable Double value) {}
