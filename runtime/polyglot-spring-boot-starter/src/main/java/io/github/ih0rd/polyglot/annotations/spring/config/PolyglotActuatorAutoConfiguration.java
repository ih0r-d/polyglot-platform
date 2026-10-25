package io.github.ih0rd.polyglot.annotations.spring.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.actuator.PolyglotHealthIndicator;
import io.github.ih0rd.polyglot.annotations.spring.actuator.PolyglotInfoContributor;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotRuntimeState;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

/**
 * Auto-configuration for actuator integrations exposed by the polyglot starter.
 *
 * <p>The configuration contributes optional {@code /actuator/info} and health integration when the
 * relevant Spring Boot actuator types are on the classpath.
 */
@AutoConfiguration(after = PolyglotAutoConfiguration.class)
@EnableConfigurationProperties(PolyglotProperties.class)
@ConditionalOnClass(InfoContributor.class)
@ConditionalOnProperty(prefix = "polyglot.actuator", name = "enabled", matchIfMissing = true)
public class PolyglotActuatorAutoConfiguration {

  private final @Nullable ObjectProvider<PolyglotRuntimeState> runtimeState;

  /** Creates the actuator auto-configuration bean container. */
  public PolyglotActuatorAutoConfiguration() {
    this.runtimeState = null;
  }

  @Autowired
  PolyglotActuatorAutoConfiguration(ObjectProvider<PolyglotRuntimeState> runtimeState) {
    this.runtimeState = runtimeState;
  }

  /**
   * Creates the actuator info contributor.
   *
   * @param executors available executors
   * @param properties starter properties
   * @return info contributor
   */
  @Bean
  @ConditionalOnBean(PolyglotExecutors.class)
  @ConditionalOnProperty(prefix = "polyglot.actuator.info", name = "enabled", matchIfMissing = true)
  @ConditionalOnMissingBean
  public InfoContributor polyglotInfoContributor(
      PolyglotExecutors executors, PolyglotProperties properties) {
    return new PolyglotInfoContributor(executors, properties);
  }

  /**
   * Creates the health indicator for the polyglot runtime.
   *
   * @param executors available executors
   * @param properties starter properties
   * @return health indicator
   */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnProperty(
      prefix = "polyglot.actuator.health",
      name = "enabled",
      matchIfMissing = true)
  public HealthIndicator polyglotHealthIndicator(
      @Nullable PolyglotExecutors executors, PolyglotProperties properties) {
    if (runtimeState == null && executors != null) {
      return new PolyglotHealthIndicator(executors, properties);
    }
    PolyglotRuntimeState state = runtimeState != null ? runtimeState.getIfAvailable() : null;
    return () -> lifecycleHealth(properties, state);
  }

  private static Health lifecycleHealth(
      PolyglotProperties properties, @Nullable PolyglotRuntimeState state) {
    if (!properties.core().enabled() || state == null) {
      return Health.unknown().build();
    }
    PolyglotRuntimeState.Snapshot snapshot = state.snapshot();
    return switch (snapshot.healthState()) {
      case UNKNOWN -> Health.unknown().build();
      case DOWN -> Health.down().withDetail("reason", "Polyglot startup is unavailable").build();
      case UP ->
          Health.up()
              .withDetail("pythonEnabled", snapshot.pythonAvailable())
              .withDetail("jsEnabled", snapshot.jsAvailable())
              .build();
    };
  }
}
