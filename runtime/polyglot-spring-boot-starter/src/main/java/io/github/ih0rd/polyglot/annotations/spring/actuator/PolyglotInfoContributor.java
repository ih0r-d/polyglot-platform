package io.github.ih0rd.polyglot.annotations.spring.actuator;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

/** Actuator info contributor that publishes adapter configuration and executor availability. */
public class PolyglotInfoContributor implements InfoContributor {

  private final PolyglotExecutors executors;
  private final PolyglotProperties properties;

  /**
   * Creates the contributor.
   *
   * @param executors available executors
   * @param properties starter properties
   */
  public PolyglotInfoContributor(PolyglotExecutors executors, PolyglotProperties properties) {
    this.executors = executors;
    this.properties = properties;
  }

  /** Contributes polyglot adapter details to the actuator info endpoint. */
  @Override
  public void contribute(Info.@NonNull Builder builder) {
    Map<String, Object> polyglot = new LinkedHashMap<>();
    polyglot.put("core", properties.core());
    polyglot.put("executors", executorsSummary());
    if (properties.python().enabled()) {
      polyglot.put("python", pythonInfo());
    }
    if (properties.js().enabled()) {
      polyglot.put("js", jsInfo());
    }

    builder.withDetails(polyglot);
  }

  private Map<String, Object> pythonInfo() {
    Map<String, Object> python = new LinkedHashMap<>();
    python.put("enabled", true);
    python.put("resourcesPath", properties.python().resourcesPath());
    python.put("safeDefaults", properties.python().safeDefaults());
    python.put("warmupOnStartup", properties.python().warmupOnStartup());
    python.put("preloadScripts", properties.python().preloadScripts());
    python.put("available", executors.python().isPresent());
    executors
        .python()
        .ifPresent(
            executor ->
                python.put(
                    "metadata",
                    executorMetadata(
                        executor.metadata(), "instanceCacheSize", "cachedInterfaces")));
    return python;
  }

  private Map<String, Object> jsInfo() {
    Map<String, Object> js = new LinkedHashMap<>();
    js.put("enabled", true);
    js.put("resourcesPath", properties.js().resourcesPath());
    js.put("warmupOnStartup", properties.js().warmupOnStartup());
    js.put("preloadScripts", properties.js().preloadScripts());
    js.put("available", executors.js().isPresent());
    executors
        .js()
        .ifPresent(
            executor ->
                js.put(
                    "metadata",
                    executorMetadata(executor.metadata(), "loadedInterfaces", "loadedInterfaces")));
    return js;
  }

  private Map<String, Object> executorsSummary() {
    Map<String, Object> summary = new LinkedHashMap<>();
    int configuredCount = 0;
    if (properties.python().enabled()) {
      configuredCount++;
    }
    if (properties.js().enabled()) {
      configuredCount++;
    }
    int availableCount = 0;
    if (executors.python().isPresent()) {
      availableCount++;
    }
    if (executors.js().isPresent()) {
      availableCount++;
    }
    summary.put("configuredCount", configuredCount);
    summary.put("availableCount", availableCount);
    return summary;
  }

  private Map<String, Object> executorMetadata(
      Map<String, Object> metadata, String numericKey, String collectionKey) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("executorType", metadata.get("executorType"));
    result.put("languageId", metadata.get("languageId"));
    result.put("sourceCacheSize", metadata.getOrDefault("sourceCacheSize", 0));
    result.put("contractCacheSize", contractCacheSize(metadata, numericKey, collectionKey));
    return result;
  }

  private static int contractCacheSize(
      Map<String, Object> metadata, String numericKey, String collectionKey) {
    Object numericValue = metadata.get(numericKey);
    if (numericValue instanceof Number number) {
      return number.intValue();
    }
    Object collectionValue = metadata.get(collectionKey);
    if (collectionValue instanceof Collection<?> collection) {
      return collection.size();
    }
    return 0;
  }
}
