package io.github.ih0rd.polyglot.console.internal.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Internal browser wire contract, without arbitrary Actuator details or meter tags.
 *
 * @param instanceId identity of the current Spring-context console service
 * @param refreshIntervalMillis configured browser polling interval
 * @param health recorded polyglot lifecycle status
 * @param pythonAvailable recorded Python availability, or null when unknown
 * @param invocations matching language/outcome Timer measurements
 * @param runtime allowlisted gauge measurements
 * @param configuration allowlisted boolean configuration
 * @param jvm allowlisted standard JVM meter readings
 * @param operations bounded runtime operation measurements
 * @param caches actual cache lookups and evictions
 */
public record ConsoleSnapshot(
    String instanceId,
    long refreshIntervalMillis,
    String health,
    @Nullable Boolean pythonAvailable,
    List<InvocationSnapshot> invocations,
    List<RuntimeMetricSnapshot> runtime,
    List<ConsoleSettingSnapshot> configuration,
    JvmSnapshot jvm,
    List<OperationSnapshot> operations,
    List<CacheSnapshot> caches) {
  /**
   * Copies snapshot containers to prevent mutable state escaping the collector.
   *
   * @param instanceId context identity
   * @param refreshIntervalMillis polling interval
   * @param health lifecycle status
   * @param pythonAvailable recorded availability
   * @param invocations timer snapshots
   * @param runtime gauge snapshots
   * @param configuration safe boolean settings
   * @param jvm standard JVM meter readings
   * @param operations runtime operations
   * @param caches cache observations
   */
  public ConsoleSnapshot {
    invocations = List.copyOf(invocations);
    runtime = List.copyOf(runtime);
    configuration = List.copyOf(configuration);
    operations = List.copyOf(operations);
    caches = List.copyOf(caches);
  }
}
