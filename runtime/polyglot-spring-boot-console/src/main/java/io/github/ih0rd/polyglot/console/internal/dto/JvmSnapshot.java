package io.github.ih0rd.polyglot.console.internal.dto;

import org.jspecify.annotations.Nullable;

/**
 * Allowlisted JVM measurements from existing Micrometer meters, without arbitrary tags.
 *
 * @param javaVersion runtime version reported by jvm.info
 * @param vendor JVM vendor reported by jvm.info
 * @param runtime runtime name reported by jvm.info
 * @param uptimeSeconds process uptime, or null when unavailable
 * @param heapUsedBytes total used heap across reported memory pools, or null when unavailable
 * @param heapCommittedBytes committed heap across reported memory pools, not maximum heap
 * @param liveThreads current live platform threads, or null when unavailable
 */
public record JvmSnapshot(
    @Nullable String javaVersion,
    @Nullable String vendor,
    @Nullable String runtime,
    @Nullable Double uptimeSeconds,
    @Nullable Double heapUsedBytes,
    @Nullable Double heapCommittedBytes,
    @Nullable Double liveThreads) {}
