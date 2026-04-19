package io.github.ih0rd.codegen.model;

import java.nio.file.Path;

import io.github.ih0rd.codegen.ContractGenerator;
import io.github.ih0rd.codegen.JavaInterfaceGenerator;

public record ScriptProcessingContext(
    ContractGenerator generator,
    JavaInterfaceGenerator javaGenerator,
    Path outputDir,
    boolean failOnDrift) {}
