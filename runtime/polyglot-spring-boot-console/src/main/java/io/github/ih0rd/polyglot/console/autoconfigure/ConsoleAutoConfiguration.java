package io.github.ih0rd.polyglot.console.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;
import io.github.ih0rd.polyglot.console.internal.metrics.ConsoleSnapshotService;
import io.github.ih0rd.polyglot.console.internal.web.ConsoleAccess;
import io.github.ih0rd.polyglot.console.internal.web.ConsoleController;
import io.github.ih0rd.polyglot.console.internal.web.ConsoleStartupLogger;
import io.github.ih0rd.polyglot.console.properties.ConsoleProperties;
import io.micrometer.core.instrument.MeterRegistry;

/** Experimental servlet console. No HTTP Actuator endpoint exposure is required. */
@AutoConfiguration(
    afterName = {
      "io.github.ih0rd.polyglot.annotations.spring.config.PolyglotActuatorAutoConfiguration",
      "io.github.ih0rd.polyglot.annotations.spring.config.PolyglotMetricsAutoConfiguration"
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({DispatcherServlet.class, MeterRegistry.class, HealthIndicator.class})
@ConditionalOnProperty(prefix = "polyglot.console", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ConsoleProperties.class)
public final class ConsoleAutoConfiguration implements WebMvcConfigurer {
  private final ConsoleProperties properties;

  /**
   * Creates console-only MVC contributions.
   *
   * @param properties validated console settings
   */
  public ConsoleAutoConfiguration(ConsoleProperties properties) {
    this.properties = properties;
  }

  @Bean
  ConsoleSnapshotService consoleData(
      ObjectProvider<MeterRegistry> registries,
      @Qualifier("polyglotHealthIndicator") ObjectProvider<HealthIndicator> health,
      ObjectProvider<PolyglotProperties> polyglotProperties) {
    return new ConsoleSnapshotService(properties, registries, health, polyglotProperties);
  }

  @Bean
  ConsoleController consoleController(ConsoleSnapshotService data) {
    return new ConsoleController(properties, data);
  }

  @Bean
  ConsoleStartupLogger consoleStartupLogger(
      org.springframework.context.ApplicationContext context,
      ObjectProvider<org.springframework.boot.web.server.autoconfigure.ServerProperties>
          serverProperties) {
    return new ConsoleStartupLogger(context, properties, serverProperties);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler(properties.path() + "/assets/**")
        .addResourceLocations("classpath:/META-INF/polyglot-console/");
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new ConsoleAccess())
        .addPathPatterns(properties.path(), properties.path() + "/**");
  }
}
