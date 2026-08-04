package io.github.ih0rd.codegen.model;

import java.nio.file.Path;

import io.github.ih0rd.codegen.ContractGenerator;

/**
 * Bundles the contract generator and output configuration needed to process a single script.
 *
 * @param generator contract generator for the script
 * @param outputDir directory where generated files are written
 * @param failOnDrift whether to fail when generated output has drifted
 */
public record ScriptProcessingContext(
    ContractGenerator generator, Path outputDir, boolean failOnDrift) {}
