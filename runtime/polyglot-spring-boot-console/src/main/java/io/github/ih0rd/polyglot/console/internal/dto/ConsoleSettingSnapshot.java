package io.github.ih0rd.polyglot.console.internal.dto;

/**
 * One explicitly allowlisted boolean; never an arbitrary application property.
 *
 * @param name display label
 * @param value effective configured value, not observed runtime state
 */
public record ConsoleSettingSnapshot(String name, boolean value) {}
