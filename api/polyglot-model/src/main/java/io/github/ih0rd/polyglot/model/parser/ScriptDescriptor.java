package io.github.ih0rd.polyglot.model.parser;

import io.github.ih0rd.polyglot.SupportedLanguage;

/**
 * Fully materialized description of a script passed into the code generation pipeline.
 *
 * @param language detected script language
 * @param source full script source code
 * @param fileName original file name when available
 */
public record ScriptDescriptor(
    SupportedLanguage language, String source, String fileName // optional, may be null
    ) {}
