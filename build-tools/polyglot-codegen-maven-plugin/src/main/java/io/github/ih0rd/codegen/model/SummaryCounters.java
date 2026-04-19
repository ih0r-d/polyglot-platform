package io.github.ih0rd.codegen.model;

import java.util.concurrent.atomic.AtomicInteger;

public record SummaryCounters(
    AtomicInteger supportedScripts,
    AtomicInteger generatedContracts,
    AtomicInteger writtenFiles,
    AtomicInteger skippedUnchangedFiles,
    AtomicInteger driftedFiles) {}
