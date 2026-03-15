package io.github.ih0rd.polyglot.annotations.spring.internal;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

/// # PolyglotStartupLifecycle
///
/// Internal Spring lifecycle component responsible for:
/// - Polyglot engine warmup
/// - Early runtime validation
/// - Single startup summary log
///
/// ## Responsibilities
/// - Perform safe NOOP warmup for enabled languages
/// - Fail fast if configured and warmup fails
/// - Emit a single structured startup summary
///
/// ## Design notes
/// - Internal component (not public API)
/// - No user scripts are executed
/// - Executors own Context lifecycle
/// - Uses SmartLifecycle for deterministic startup ordering
///
public final class PolyglotStartupLifecycle implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(PolyglotStartupLifecycle.class);

  private final PolyglotProperties properties;
  private final PyExecutor pyExecutor;
  private final JsExecutor jsExecutor;

  private volatile boolean running;

  public PolyglotStartupLifecycle(
      PolyglotProperties properties, PyExecutor pyExecutor, JsExecutor jsExecutor) {

    this.properties = properties;
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
      warmupJs();

      logStartupSummary(startedAt);
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

  private void warmupJs() {
    if (jsExecutor == null || !properties.js().enabled() || !properties.js().warmupOnStartup()) {
      return;
    }

    log.debug("[Polyglot][JS] Warmup started");
    jsExecutor.evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
  }

  private void logStartupSummary(long startedAtNanos) {
    long startupMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);

    log.info("---- Polyglot Starter ----------------------------------------");

    log.info(
        "Core        : {}, failFast={}, logLevel={}",
        properties.core().enabled() ? "ENABLED" : "DISABLED",
        properties.core().failFast(),
        properties.core().logLevel().toUpperCase());

    if (properties.python().enabled()) {
      log.info("Python      : ENABLED ({})", pyExecutor != null ? "available" : "missing runtime");
      log.info("  warmup    : {}", properties.python().warmupOnStartup());
      log.info(
          "  preload   : {}",
          properties.python().preloadScripts().isEmpty()
              ? "none"
              : properties.python().preloadScripts());
    } else {
      log.info("Python      : DISABLED");
    }

    if (properties.js().enabled()) {
      log.info("JavaScript  : ENABLED ({})", jsExecutor != null ? "available" : "missing runtime");
      log.info("  warmup    : {}", properties.js().warmupOnStartup());
    } else {
      log.info("JavaScript  : DISABLED");
    }

    log.info(
        "Executors   : python={}, js={}",
        pyExecutor != null ? "ACTIVE" : "OFF",
        jsExecutor != null ? "ACTIVE" : "OFF");

    log.info("Startup     : polyglot={} ms", startupMs);

    log.info("--------------------------------------------------------------");
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
