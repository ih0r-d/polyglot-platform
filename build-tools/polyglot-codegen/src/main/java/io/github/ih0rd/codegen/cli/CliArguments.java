package io.github.ih0rd.codegen.cli;

import java.nio.file.Path;

import io.github.ih0rd.polyglot.model.config.CodegenConfig;

/// # CliArguments
///
/// Structured representation of CLI input.
///
/// Decouples raw CLI parsing from code generation logic.
public record CliArguments(
    Path inputDir, Path outputDir, String basePackage, CodegenConfig config) {}
