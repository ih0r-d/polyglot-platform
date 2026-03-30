package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.SmartLifecycle;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.client.PolyglotClientFactoryBean;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

/**
 * Internal Spring lifecycle component responsible for:
 *
 * <ul>
 *   <li>Polyglot engine warmup
 *   <li>Early client validation when fail-fast is enabled
 *   <li>A single startup summary log
 * </ul>
 *
 * <p><strong>Responsibilities:</strong>
 *
 * <ul>
 *   <li>Perform safe NOOP warmup for enabled languages
 *   <li>Optionally preload configured scripts
 *   <li>Eagerly instantiate polyglot clients when fail-fast is enabled
 *   <li>Fail fast if configured startup work fails
 *   <li>Emit a single structured startup summary when enabled
 * </ul>
 *
 * <p><strong>Design notes:</strong>
 *
 * <ul>
 *   <li>Internal component, not public API
 *   <li>Executors own context lifecycle
 *   <li>Uses {@link SmartLifecycle} for deterministic startup ordering
 * </ul>
 */
public final class PolyglotStartupLifecycle implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(PolyglotStartupLifecycle.class);

  private final PolyglotProperties properties;
  private final ConfigurableListableBeanFactory beanFactory;
  private final PolyglotRuntimeState runtimeState;
  private final ObjectProvider<PyExecutor> pyExecutor;
  private final ObjectProvider<JsExecutor> jsExecutor;

  private volatile boolean running;

  /**
   * Creates a lifecycle using eager executor instances.
   *
   * @param properties starter properties
   * @param beanFactory bean factory used for eager client validation
   * @param runtimeState runtime state recorder
   * @param pyExecutor Python executor instance, if available
   * @param jsExecutor JavaScript executor instance, if available
   */
  public PolyglotStartupLifecycle(
      PolyglotProperties properties,
      ConfigurableListableBeanFactory beanFactory,
      PolyglotRuntimeState runtimeState,
      PyExecutor pyExecutor,
      JsExecutor jsExecutor) {
    this(
        properties,
        beanFactory,
        runtimeState,
        PolyglotObjectProviders.providerOf(pyExecutor),
        PolyglotObjectProviders.providerOf(jsExecutor));
  }

  /**
   * Creates a lifecycle using lazy executor providers.
   *
   * @param properties starter properties
   * @param beanFactory bean factory used for eager client validation
   * @param runtimeState runtime state recorder
   * @param pyExecutor provider for the optional Python executor
   * @param jsExecutor provider for the optional JavaScript executor
   */
  public PolyglotStartupLifecycle(
      PolyglotProperties properties,
      ConfigurableListableBeanFactory beanFactory,
      PolyglotRuntimeState runtimeState,
      ObjectProvider<PyExecutor> pyExecutor,
      ObjectProvider<JsExecutor> jsExecutor) {

    this.properties = properties;
    this.beanFactory = beanFactory;
    this.runtimeState = runtimeState;
    this.pyExecutor = pyExecutor;
    this.jsExecutor = jsExecutor;
  }

  @Override
  public void start() {
    if (!properties.core().enabled()) {
      return;
    }

    long startedAt = System.nanoTime();
    PyExecutor pythonExecutor = pyExecutor.getIfAvailable();
    JsExecutor jsLanguageExecutor = jsExecutor.getIfAvailable();

    try {
      warmupPython(pythonExecutor);
      preloadPython(pythonExecutor);
      warmupJs(jsLanguageExecutor);
      preloadJs(jsLanguageExecutor);
      eagerValidateClients();
      long startupMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      runtimeState.recordStartup(pythonExecutor, jsLanguageExecutor, startupMs);

      if (properties.core().logMetadataOnStartup()) {
        logStartupSummary(startupMs, pythonExecutor, jsLanguageExecutor);
      }
      running = true;

    } catch (Exception ex) {
      if (properties.core().failFast()) {
        throw new IllegalStateException("Polyglot startup initialization failed", ex);
      }
      log.warn("Polyglot startup initialization failed (failFast=false)", ex);
    }
  }

  private void warmupPython(PyExecutor pyExecutor) {
    if (pyExecutor == null
        || !properties.python().enabled()
        || !properties.python().warmupOnStartup()) {
      return;
    }

    log.debug("[Polyglot][PYTHON] Warmup started");
    pyExecutor.evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
  }

  private void preloadPython(PyExecutor pyExecutor) {
    if (pyExecutor == null
        || !properties.python().enabled()
        || properties.python().preloadScripts().isEmpty()) {
      return;
    }

    for (String scriptName : properties.python().preloadScripts()) {
      log.debug("[Polyglot][PYTHON] Preloading {}", scriptName);
      pyExecutor.preloadScript(scriptName);
    }
  }

  private void warmupJs(JsExecutor jsExecutor) {
    if (jsExecutor == null || !properties.js().enabled() || !properties.js().warmupOnStartup()) {
      return;
    }

    log.debug("[Polyglot][JS] Warmup started");
    jsExecutor.evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
  }

  private void preloadJs(JsExecutor jsExecutor) {
    if (jsExecutor == null
        || !properties.js().enabled()
        || properties.js().preloadScripts().isEmpty()) {
      return;
    }

    for (String scriptName : properties.js().preloadScripts()) {
      log.debug("[Polyglot][JS] Preloading {}", scriptName);
      jsExecutor.preloadScript(scriptName);
    }
  }

  private void eagerValidateClients() {
    if (!properties.core().failFast()) {
      return;
    }

    for (String beanName : beanFactory.getBeanDefinitionNames()) {
      BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
      if (!PolyglotClientFactoryBean.class.getName().equals(definition.getBeanClassName())) {
        continue;
      }

      log.debug("[Polyglot][CLIENT] Eagerly validating {}", beanName);
      beanFactory.getBean(beanName);
    }
  }

  private void logStartupSummary(long startupMs, PyExecutor pyExecutor, JsExecutor jsExecutor) {
    logAtConfiguredLevel("Polyglot starter ready");
    logAtConfiguredLevel(
        "Core        : enabled={}, failFast={}, logLevel={}",
        properties.core().enabled() ? "ENABLED" : "DISABLED",
        properties.core().failFast(),
        properties.core().logLevel().toUpperCase());
    logAtConfiguredLevel(
        "Executors   : available={}/{}, python={}, js={}",
        runtimeState.availableExecutors(),
        configuredExecutors(),
        pyExecutor != null ? "ACTIVE" : "OFF",
        jsExecutor != null ? "ACTIVE" : "OFF");
    logAtConfiguredLevel("Startup     : polyglot={} ms", startupMs);

    if (properties.python().enabled()) {
      logAtConfiguredLevel(
          "Python      : {}, warmup={}, safeDefaults={}, preload={}, metadata={}",
          pyExecutor != null ? "AVAILABLE" : "MISSING",
          properties.python().warmupOnStartup(),
          properties.python().safeDefaults(),
          preloadSummary(properties.python().preloadScripts()),
          pythonMetadataSummary(pyExecutor));
    } else {
      logAtConfiguredLevel("Python      : DISABLED");
    }

    if (properties.js().enabled()) {
      logAtConfiguredLevel(
          "JavaScript  : {}, warmup={}, preload={}, metadata={}",
          jsExecutor != null ? "AVAILABLE" : "MISSING",
          properties.js().warmupOnStartup(),
          preloadSummary(properties.js().preloadScripts()),
          jsMetadataSummary(jsExecutor));
    } else {
      logAtConfiguredLevel("JavaScript  : DISABLED");
    }
  }

  private int configuredExecutors() {
    int configured = 0;
    if (properties.python().enabled()) {
      configured++;
    }
    if (properties.js().enabled()) {
      configured++;
    }
    return configured;
  }

  private static String preloadSummary(java.util.List<String> preloadScripts) {
    return preloadScripts.isEmpty() ? "none" : preloadScripts.toString();
  }

  private static Map<String, Object> pythonMetadataSummary(PyExecutor executor) {
    if (executor == null) {
      return Map.of("available", false);
    }
    Map<String, Object> metadata = executor.metadata();
    return Map.of(
        "available", true,
        "executorType", metadata.getOrDefault("executorType", executor.getClass().getName()),
        "languageId", metadata.getOrDefault("languageId", "python"),
        "sourceCacheSize", metadata.getOrDefault("sourceCacheSize", 0),
        "contractCacheSize", metadata.getOrDefault("instanceCacheSize", 0),
        "boundInterfacesCount", size(metadata.get("cachedInterfaces")));
  }

  private static Map<String, Object> jsMetadataSummary(JsExecutor executor) {
    if (executor == null) {
      return Map.of("available", false);
    }
    Map<String, Object> metadata = executor.metadata();
    return Map.of(
        "available", true,
        "executorType", metadata.getOrDefault("executorType", executor.getClass().getName()),
        "languageId", metadata.getOrDefault("languageId", "js"),
        "sourceCacheSize", metadata.getOrDefault("sourceCacheSize", 0),
        "contractCacheSize", size(metadata.get("loadedInterfaces")),
        "loadedInterfacesCount", size(metadata.get("loadedInterfaces")));
  }

  private static int size(Object value) {
    return value instanceof Collection<?> collection ? collection.size() : 0;
  }

  private void logAtConfiguredLevel(String message, Object... args) {
    String level = properties.core().logLevel().trim().toUpperCase();
    switch (level) {
      case "TRACE" -> log.trace(message, args);
      case "INFO" -> log.info(message, args);
      case "WARN" -> log.warn(message, args);
      case "ERROR" -> log.error(message, args);
      default -> log.debug(message, args);
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /** Runs as late as possible in the Spring lifecycle. */
  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }

  @Override
  public void stop() {
    // no-op
  }
}
