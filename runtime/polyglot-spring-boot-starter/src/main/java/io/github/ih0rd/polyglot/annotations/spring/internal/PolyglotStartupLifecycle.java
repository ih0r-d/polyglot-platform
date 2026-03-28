package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.SmartLifecycle;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.client.PolyglotClientFactoryBean;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

/// # PolyglotStartupLifecycle
///
/// Internal Spring lifecycle component responsible for:
/// - Polyglot engine warmup
/// - Early client validation when fail-fast is enabled
/// - Single startup summary log
///
/// ## Responsibilities
/// - Perform safe NOOP warmup for enabled languages
/// - Optionally preload configured scripts
/// - Eagerly instantiate polyglot clients when fail-fast is enabled
/// - Fail fast if configured startup work fails
/// - Emit a single structured startup summary when enabled
///
/// ## Design notes
/// - Internal component (not public API)
/// - Executors own Context lifecycle
/// - Uses SmartLifecycle for deterministic startup ordering
///
public final class PolyglotStartupLifecycle implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(PolyglotStartupLifecycle.class);

  private final PolyglotProperties properties;
  private final ConfigurableListableBeanFactory beanFactory;
  private final PyExecutor pyExecutor;
  private final JsExecutor jsExecutor;

  private volatile boolean running;

  public PolyglotStartupLifecycle(
      PolyglotProperties properties,
      ConfigurableListableBeanFactory beanFactory,
      PyExecutor pyExecutor,
      JsExecutor jsExecutor) {

    this.properties = properties;
    this.beanFactory = beanFactory;
    this.pyExecutor = pyExecutor;
    this.jsExecutor = jsExecutor;
  }

  @Override
  public void start() {
    if (!properties.core().enabled()) {
      return;
    }

    long startedAt = System.nanoTime();

    try {
      warmupPython();
      preloadPython();
      warmupJs();
      preloadJs();
      eagerValidateClients();

      if (properties.core().logMetadataOnStartup()) {
        logStartupSummary(startedAt);
      }
      running = true;

    } catch (Exception ex) {
      if (properties.core().failFast()) {
        throw new IllegalStateException("Polyglot startup initialization failed", ex);
      }
      log.warn("Polyglot startup initialization failed (failFast=false)", ex);
    }
  }

  private void warmupPython() {
    if (pyExecutor == null
        || !properties.python().enabled()
        || !properties.python().warmupOnStartup()) {
      return;
    }

    log.debug("[Polyglot][PYTHON] Warmup started");
    pyExecutor.evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
  }

  private void preloadPython() {
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

  private void warmupJs() {
    if (jsExecutor == null || !properties.js().enabled() || !properties.js().warmupOnStartup()) {
      return;
    }

    log.debug("[Polyglot][JS] Warmup started");
    jsExecutor.evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
  }

  private void preloadJs() {
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

  private void logStartupSummary(long startedAtNanos) {
    long startupMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);

    logAtConfiguredLevel("---- Polyglot Starter ----------------------------------------");

    logAtConfiguredLevel(
        "Core        : {}, failFast={}, logLevel={}",
        properties.core().enabled() ? "ENABLED" : "DISABLED",
        properties.core().failFast(),
        properties.core().logLevel().toUpperCase());

    if (properties.python().enabled()) {
      logAtConfiguredLevel(
          "Python      : ENABLED ({})", pyExecutor != null ? "available" : "missing runtime");
      logAtConfiguredLevel("  warmup    : {}", properties.python().warmupOnStartup());
      logAtConfiguredLevel("  safeDefaults: {}", properties.python().safeDefaults());
      logAtConfiguredLevel(
          "  preload   : {}",
          properties.python().preloadScripts().isEmpty()
              ? "none"
              : properties.python().preloadScripts());
    } else {
      logAtConfiguredLevel("Python      : DISABLED");
    }

    if (properties.js().enabled()) {
      logAtConfiguredLevel(
          "JavaScript  : ENABLED ({})", jsExecutor != null ? "available" : "missing runtime");
      logAtConfiguredLevel("  warmup    : {}", properties.js().warmupOnStartup());
      logAtConfiguredLevel(
          "  preload   : {}",
          properties.js().preloadScripts().isEmpty() ? "none" : properties.js().preloadScripts());
    } else {
      logAtConfiguredLevel("JavaScript  : DISABLED");
    }

    logAtConfiguredLevel(
        "Executors   : python={}, js={}",
        pyExecutor != null ? "ACTIVE" : "OFF",
        jsExecutor != null ? "ACTIVE" : "OFF");

    logAtConfiguredLevel("Startup     : polyglot={} ms", startupMs);

    logAtConfiguredLevel("--------------------------------------------------------------");
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

  /// Run as late as possible
  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }

  @Override
  public void stop() {
    // no-op
  }
}
