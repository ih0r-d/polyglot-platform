package io.github.ih0rd.polyglot.annotations.spring.actuator;

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
    return python;
  }

  private Map<String, Object> jsInfo() {
    Map<String, Object> js = new LinkedHashMap<>();
    js.put("enabled", true);
    js.put("resourcesPath", properties.js().resourcesPath());
    js.put("warmupOnStartup", properties.js().warmupOnStartup());
    js.put("preloadScripts", properties.js().preloadScripts());
    js.put("available", executors.js().isPresent());
    return js;
  }
}
