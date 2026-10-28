package io.github.ih0rd.polyglot.console.autoconfigure;

import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.*;
import org.springframework.boot.health.contributor.*;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

import io.github.ih0rd.polyglot.annotations.spring.properties.*;
import io.github.ih0rd.polyglot.console.internal.metrics.ConsoleSnapshotService;
import io.github.ih0rd.polyglot.console.internal.web.ConsoleAccess;
import io.github.ih0rd.polyglot.console.internal.web.ConsoleController;
import io.github.ih0rd.polyglot.console.internal.web.ConsoleStartupLogger;
import io.github.ih0rd.polyglot.console.properties.ConsoleProperties;
import io.micrometer.core.instrument.*;

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
