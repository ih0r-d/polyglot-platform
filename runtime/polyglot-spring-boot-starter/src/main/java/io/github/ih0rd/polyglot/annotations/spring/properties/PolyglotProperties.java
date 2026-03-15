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
   * @param logMetadataOnStartup logs polyglot metadata once on startup
   * @param logLevel semantic log level hint used by the starter
   */
  public record CoreProperties(
      boolean enabled, boolean failFast, boolean logMetadataOnStartup, String logLevel) {

    public CoreProperties {
      if (logLevel == null || logLevel.isBlank()) {
        logLevel = "debug";
      }
    }

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
   * @param safeDefaults applies safe default options for GraalPy
   * @param warmupOnStartup performs lightweight warmup during application startup
   * @param preloadScripts optional list of script/module names to preload during warmup
   */
  public record PythonProperties(
      boolean enabled,
      String resourcesPath,
      boolean safeDefaults,
      boolean warmupOnStartup,
      List<String> preloadScripts) {

    public PythonProperties {
      preloadScripts = (preloadScripts != null) ? preloadScripts : List.of();
    }

    public static PythonProperties defaults() {
      return new PythonProperties(false, "classpath:python", true, false, List.of());
    }
  }

  /**
   * JavaScript executor settings.
   *
   * <p>Prefix: {@code polyglot.js.*}
   *
   * @param enabled enables JavaScript executor bean
   * @param resourcesPath base script location expressed as a Spring Resource location (e.g. {@code
   *     classpath:/js/}, {@code file:./js/})
   * @param warmupOnStartup performs lightweight warmup during application startup
   * @param preloadScripts optional list of script/module names to preload during warmup
   */
  public record JsProperties(
      boolean enabled, String resourcesPath, boolean warmupOnStartup, List<String> preloadScripts) {

    public JsProperties {
      preloadScripts = (preloadScripts != null) ? preloadScripts : List.of();
    }

    public static JsProperties defaults() {
      return new JsProperties(false, "classpath:js", false, List.of());
    }
  }

  /**
   * Actuator settings.
   *
   * <p>Prefix: {@code polyglot.actuator.*}
   *
   * @param info info contributor settings
   * @param health health indicator settings
   */
  public record ActuatorProperties(InfoProperties info, HealthProperties health) {

    public ActuatorProperties {
      info = (info != null) ? info : new InfoProperties(true);
      health = (health != null) ? health : new HealthProperties(true);
    }

    public static ActuatorProperties defaults() {
      return new ActuatorProperties(new InfoProperties(true), new HealthProperties(true));
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

    public static MetricsProperties defaults() {
      return new MetricsProperties(true);
    }
  }
}
