package io.github.ih0rd.codegen.model;

import java.nio.file.Path;

import io.github.ih0rd.codegen.ContractGenerator;
import io.github.ih0rd.codegen.JavaInterfaceGenerator;

/**
 * Bundles the generators and output configuration needed to process a single script.
 *
 * @param generator contract generator for the script
 * @param javaGenerator Java interface generator
 * @param outputDir directory where generated files are written
 * @param failOnDrift whether to fail when generated output has drifted
 */
public record ScriptProcessingContext(
    ContractGenerator generator,
    JavaInterfaceGenerator javaGenerator,
    Path outputDir,
    boolean failOnDrift) {}
