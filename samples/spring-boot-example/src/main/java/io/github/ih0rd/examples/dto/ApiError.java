package io.github.ih0rd.examples.dto;

/** Safe HTTP error without guest source, inputs or exception details. */
public record ApiError(String code, String message) {}
