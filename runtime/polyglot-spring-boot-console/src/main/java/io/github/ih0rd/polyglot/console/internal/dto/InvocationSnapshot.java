package io.github.ih0rd.polyglot.console.internal.dto;

/**
 * Measurements from matching Timer scopes, normalized to seconds.
 *
 * @param language GraalVM language identifier
 * @param outcome bounded success/failure outcome
 * @param count cumulative invocation count
 * @param totalSeconds cumulative recorded duration
 * @param windowMaxSeconds maximum within the meter's current time window
 */
public record InvocationSnapshot(
    String language, String outcome, long count, double totalSeconds, double windowMaxSeconds) {}
