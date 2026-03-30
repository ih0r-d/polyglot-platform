package io.github.ih0rd.polyglot.annotations.spring.config;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.actuator.PolyglotHealthIndicator;
import io.github.ih0rd.polyglot.annotations.spring.actuator.PolyglotInfoContributor;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

/**
 * Auto-configuration for actuator integrations exposed by the polyglot starter.
 *
 * <p>The configuration contributes optional {@code /actuator/info} and health integration when the
 * relevant Spring Boot actuator types are on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(InfoContributor.class)
@ConditionalOnProperty(prefix = "polyglot.actuator", name = "enabled", matchIfMissing = true)
public class PolyglotActuatorAutoConfiguration {

  /** Creates the actuator auto-configuration bean container. */
  public PolyglotActuatorAutoConfiguration() {
    // Default constructor required for Spring Boot auto-configuration instantiation.
  }

  /**
   * Creates the actuator info contributor.
   *
   * @param executors available executors
   * @param properties starter properties
   * @return info contributor
   */
  @Bean
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
      PolyglotExecutors executors, PolyglotProperties properties) {
    return new PolyglotHealthIndicator(executors, properties);
  }
}
