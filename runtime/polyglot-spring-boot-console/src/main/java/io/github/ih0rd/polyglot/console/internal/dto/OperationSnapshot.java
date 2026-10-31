package io.github.ih0rd.polyglot.console.internal.dto;

/** Detached measurements for one allowlisted language/operation/outcome timer scope. */
public record OperationSnapshot(
    String language,
    String operation,
    String outcome,
    long count,
    double totalSeconds,
    double windowMaxSeconds) {}
