package io.github.ih0rd.polyglot.annotations.spring.properties;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import io.github.ih0rd.polyglot.annotations.ExperimentalApi;

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
   * @param enabled Enables core services and language auto-configurations. Disabling this prevents
   *     creation of Python and experimental JavaScript executors.
   * @param failFast Eagerly validates discovered @PolyglotClient beans and fails startup on warmup,
   *     preload or client-binding errors. Validation may execute module initialization code.
   * @param logMetadataOnStartup Logs one starter summary after warmup, preload and fail-fast
   *     validation complete.
   * @param logLevel Log level for the startup summary: TRACE, DEBUG, INFO, WARN or ERROR.
   */
  public record CoreProperties(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("true") boolean failFast,
      @DefaultValue("true") boolean logMetadataOnStartup,
      @DefaultValue("DEBUG") String logLevel) {

    /** Creates normalized core properties with a fallback startup log level. */
    public CoreProperties {
      if (logLevel == null || logLevel.isBlank()) {
        logLevel = "debug";
      } else if (!Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR")
          .contains(logLevel.trim().toUpperCase(Locale.ROOT))) {
        throw new IllegalArgumentException(
            "Invalid value for 'polyglot.core.log-level': '%s'. Supported values: TRACE, DEBUG, INFO, WARN, ERROR."
                .formatted(logLevel));
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
   * @param enabled Enables the Python executor when core support is enabled.
   * @param resourcesPath Base directory for Python scripts, expressed as a Spring Resource
   *     location, for example classpath:/python/ or file:./python/.
   * @param safeDefaults Applies the starter's recommended GraalPy defaults. Disabling this creates
   *     a more minimal context that can be configured through PolyglotContextCustomizer beans.
   * @param warmupOnStartup Evaluates a no-op Python expression during startup to initialize the
   *     guest runtime. This is independent of fail-fast client validation.
   * @param preloadScripts Logical Python script names without .py to evaluate after startup warmup.
   *     Names are resolved through ScriptSource. This executes module-level code without prebinding
   *     contracts or populating interface caches; later binding may evaluate it again.
   */
  public record PythonProperties(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("classpath:python") String resourcesPath,
      @DefaultValue("true") boolean safeDefaults,
      @DefaultValue("false") boolean warmupOnStartup,
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
   * @param enabled Enables the experimental JavaScript executor when core support is enabled.
   * @param resourcesPath Base directory for experimental JavaScript scripts, expressed as a Spring
   *     Resource location, for example classpath:/js/ or file:./js/.
   * @param warmupOnStartup Evaluates a no-op expression to initialize the experimental JavaScript
   *     runtime during startup. This is independent of fail-fast client validation.
   * @param preloadScripts Logical experimental JavaScript script names without .js to evaluate
   *     after startup warmup. Names are resolved through ScriptSource. This executes script code
   *     without prebinding contracts or populating interface caches; later binding may evaluate it
   *     again.
   */
  @ExperimentalApi
  public record JsProperties(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("classpath:js") String resourcesPath,
      @DefaultValue("false") boolean warmupOnStartup,
      List<String> preloadScripts) {

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
   * @param enabled Enables the starter's optional Actuator health and info integrations when
   *     Actuator is present.
   * @param info info contributor settings
   * @param health health indicator settings
   */
  public record ActuatorProperties(
      @DefaultValue("true") boolean enabled, InfoProperties info, HealthProperties health) {

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
     * @param enabled Contributes configuration and executor metadata to Actuator info when core
     *     support and the starter's Actuator integration are enabled.
     */
    public record InfoProperties(@DefaultValue("true") boolean enabled) {}

    /**
     * Actuator health indicator settings.
     *
     * <p>Prefix: {@code polyglot.actuator.health.*}
     *
     * @param enabled Contributes recorded startup and shutdown state to Actuator health without
     *     executing guest code on health requests. This does not check business-method liveness.
     */
    public record HealthProperties(@DefaultValue("true") boolean enabled) {}
  }

  /**
   * Metrics settings.
   *
   * <p>Prefix: {@code polyglot.metrics.*}
   *
   * @param enabled Registers executor and startup gauges when Micrometer is present, and bounded
   *     Spring-managed client invocation timers and runtime operation/cache observations when a
   *     MeterRegistry is available.
   */
  public record MetricsProperties(@DefaultValue("true") boolean enabled) {

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
