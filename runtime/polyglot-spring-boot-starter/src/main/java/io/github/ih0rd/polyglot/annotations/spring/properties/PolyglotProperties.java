package io.github.ih0rd.polyglot.annotations.spring.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root configuration properties for the Polyglot Spring Boot starter.
 *
 * <p>Prefix: {@code polyglot.*}
 *
 * @param core core starter configuration
 * @param python python executor configuration
 * @param js javascript executor configuration
 * @param actuator actuator integration configuration
 * @param metrics micrometer metrics configuration
 */
@ConfigurationProperties(prefix = "polyglot")
public record PolyglotProperties(
    CoreProperties core,
    PythonProperties python,
    JsProperties js,
    ActuatorProperties actuator,
    MetricsProperties metrics) {

  /** Creates normalized root starter properties with defaults for missing nested groups. */
  public PolyglotProperties {
    core = (core != null) ? core : CoreProperties.defaults();
    python = (python != null) ? python : PythonProperties.defaults();
    js = (js != null) ? js : JsProperties.defaults();
    actuator = (actuator != null) ? actuator : ActuatorProperties.defaults();
    metrics = (metrics != null) ? metrics : MetricsProperties.defaults();
  }

  /**
   * Core starter settings.
   *
   * <p>Prefix: {@code polyglot.core.*}
   *
   * @param enabled enables the polyglot starter globally
   * @param failFast fails application startup on critical configuration errors
   * @param logMetadataOnStartup logs a single startup summary for the starter
   * @param logLevel log level used for the startup summary
   */
  public record CoreProperties(
      boolean enabled, boolean failFast, boolean logMetadataOnStartup, String logLevel) {

    /** Creates normalized core properties with a fallback startup log level. */
    public CoreProperties {
      if (logLevel == null || logLevel.isBlank()) {
        logLevel = "debug";
      }
    }

    /**
     * Returns the default core settings.
     *
     * @return default core properties
     */
    public static CoreProperties defaults() {
      return new CoreProperties(true, true, true, "DEBUG");
    }
  }

  /**
   * Python executor settings.
   *
   * <p>Prefix: {@code polyglot.python.*}
   *
   * @param enabled enables Python executor bean
   * @param resourcesPath base script location expressed as a Spring Resource location (e.g. {@code
   *     classpath:/python/}, {@code file:./python/})
   * @param safeDefaults applies the starter's recommended GraalPy defaults; when disabled, the
   *     starter creates a more minimal Python context and expects callers to customize it
   * @param warmupOnStartup performs lightweight warmup during application startup
   * @param preloadScripts optional list of logical script names to evaluate during startup after
   *     warmup; this does not populate interface caches or prebind Java contracts, so repeated
   *     evaluation and repeated script side effects remain possible
   */
  public record PythonProperties(
      boolean enabled,
      String resourcesPath,
      boolean safeDefaults,
      boolean warmupOnStartup,
      List<String> preloadScripts) {

    /** Creates normalized Python properties with immutable preload scripts. */
    public PythonProperties {
      preloadScripts = (preloadScripts != null) ? List.copyOf(preloadScripts) : List.of();
    }

    /**
     * Returns the default Python settings.
     *
     * @return default Python properties
     */
    public static PythonProperties defaults() {
      return new PythonProperties(false, "classpath:python", true, false, List.of());
    }
  }

  /**
   * JavaScript executor settings.
 *
 * <p>Prefix: {@code polyglot.js.*}
 *
 * <p>This configuration group controls the repository's experimental JavaScript support path.
 *
 * @param enabled enables JavaScript executor bean
   * @param resourcesPath base script location expressed as a Spring Resource location (e.g. {@code
   *     classpath:/js/}, {@code file:./js/})
   * @param warmupOnStartup performs lightweight warmup during application startup
   * @param preloadScripts optional list of logical script names to evaluate during startup after
   *     warmup; this does not populate interface caches or prebind Java contracts, so repeated
   *     evaluation and repeated script side effects remain possible
   */
  public record JsProperties(
      boolean enabled, String resourcesPath, boolean warmupOnStartup, List<String> preloadScripts) {

    /** Creates normalized JavaScript properties with immutable preload scripts. */
    public JsProperties {
      preloadScripts = (preloadScripts != null) ? List.copyOf(preloadScripts) : List.of();
    }

    /**
     * Returns the default JavaScript settings.
     *
     * @return default JavaScript properties
     */
    public static JsProperties defaults() {
      return new JsProperties(false, "classpath:js", false, List.of());
    }
  }

  /**
   * Actuator settings.
   *
   * <p>Prefix: {@code polyglot.actuator.*}
   *
   * @param enabled enables all starter actuator integrations
   * @param info info contributor settings
   * @param health health indicator settings
   */
  public record ActuatorProperties(boolean enabled, InfoProperties info, HealthProperties health) {

    /** Creates normalized actuator properties with nested defaults. */
    public ActuatorProperties {
      info = (info != null) ? info : new InfoProperties(true);
      health = (health != null) ? health : new HealthProperties(true);
    }

    /**
     * Returns the default actuator settings.
     *
     * @return default actuator properties
     */
    public static ActuatorProperties defaults() {
      return new ActuatorProperties(true, new InfoProperties(true), new HealthProperties(true));
    }

    /**
     * Actuator info contributor settings.
     *
     * <p>Prefix: {@code polyglot.actuator.info.*}
     *
     * @param enabled enables polyglot section in /actuator/info
     */
    public record InfoProperties(boolean enabled) {}

    /**
     * Actuator health indicator settings.
     *
     * <p>Prefix: {@code polyglot.actuator.health.*}
     *
     * @param enabled enables polyglot health contributor
     */
    public record HealthProperties(boolean enabled) {}
  }

  /**
   * Metrics settings.
   *
   * <p>Prefix: {@code polyglot.metrics.*}
   *
   * @param enabled enables polyglot meters if Micrometer is present
   */
  public record MetricsProperties(boolean enabled) {

    /**
     * Returns the default metrics settings.
     *
     * @return default metrics properties
     */
    public static MetricsProperties defaults() {
      return new MetricsProperties(true);
    }
  }
}
