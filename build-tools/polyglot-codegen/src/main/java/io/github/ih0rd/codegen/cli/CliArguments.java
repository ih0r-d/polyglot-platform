package io.github.ih0rd.codegen.cli;

import java.nio.file.Path;

import io.github.ih0rd.polyglot.model.config.CodegenConfig;

/**
 * Structured representation of CLI input.
 *
 * <p>Decouples raw CLI parsing from code generation logic.
 *
 * @param inputDir directory containing source scripts to parse
 * @param outputDir directory where generated Java sources are written
 * @param basePackage base Java package for generated types
 * @param config code generation options applied to parsing and rendering
 */
public record CliArguments(
    Path inputDir, Path outputDir, String basePackage, CodegenConfig config) {}
