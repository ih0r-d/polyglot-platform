package io.github.ih0rd.polyglot.annotations.spring.actuator;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

/**
 * Simple actuator health indicator for the runtime adapter.
 *
 * <p>Status mapping:
 *
 * <ul>
 *   <li>{@code UNKNOWN}: {@code polyglot.core.enabled=false}
 *   <li>{@code UP}: at least one executor is available
 *   <li>{@code DOWN}: core enabled but no executors are available
 * </ul>
 */
public final class PolyglotHealthIndicator implements HealthIndicator {

  private final PolyglotExecutors executors;
  private final PolyglotProperties properties;

  /**
   * Creates the health indicator.
   *
   * @param executors available executors
   * @param properties starter properties
   */
  public PolyglotHealthIndicator(PolyglotExecutors executors, PolyglotProperties properties) {
    this.executors = executors;
    this.properties = properties;
  }

  /** Computes the current runtime adapter health state. */
  @Override
  public Health health() {
    if (!properties.core().enabled()) {
      return Health.unknown().build();
    }

    boolean pythonAvailable = executors.isPythonEnabled();
    boolean jsAvailable = executors.isJsEnabled();

    if (pythonAvailable || jsAvailable) {
      return Health.up()
          .withDetail("pythonEnabled", pythonAvailable)
          .withDetail("jsEnabled", jsAvailable)
          .build();
    }

    return Health.down()
        .withDetail("pythonEnabled", false)
        .withDetail("jsEnabled", false)
        .withDetail("reason", "No polyglot executors available")
        .build();
  }
}
