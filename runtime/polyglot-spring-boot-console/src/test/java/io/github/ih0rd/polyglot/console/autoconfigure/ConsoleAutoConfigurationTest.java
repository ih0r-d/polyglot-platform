package io.github.ih0rd.polyglot.console.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;
import io.github.ih0rd.polyglot.console.internal.metrics.ConsoleSnapshotService;
import io.github.ih0rd.polyglot.console.internal.web.ConsoleController;
import io.github.ih0rd.polyglot.console.properties.ConsoleProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.Filter;

class ConsoleAutoConfigurationTest {
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConsoleAutoConfiguration.class))
          .withUserConfiguration(MvcConfiguration.class);

  @Test
  void disabledByDefaultDoesNotRegisterControllerOrAssets() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ConsoleController.class);
          assertThat(context)
              .doesNotHaveBean(
                  io.github.ih0rd.polyglot.console.internal.web.ConsoleStartupLogger.class);
          var mvc = MockMvcBuilders.webAppContextSetup(context).build();
          mvc.perform(get("/polyglot-console")).andExpect(status().isNotFound());
          mvc.perform(get("/polyglot-console/assets/app.js")).andExpect(status().isNotFound());
        });
  }

  @Test
  void bindsDefaultsAndPackagesMetadataAndAutoConfiguration() {
    enabled()
        .run(
            context -> {
              var properties = context.getBean(ConsoleProperties.class);
              assertThat(properties.path()).isEqualTo("/polyglot-console");
              assertThat(properties.refreshInterval()).isEqualTo(Duration.ofSeconds(5));
              String metadata =
                  new ClassPathResource("META-INF/spring-configuration-metadata.json")
                      .getContentAsString(StandardCharsets.UTF_8);
              assertThat(metadata)
                  .contains(
                      "polyglot.console.enabled",
                      "polyglot.console.path",
                      "polyglot.console.refresh-interval",
                      "5s",
                      "Absolute console path");
            });
  }

  @Test
  void servesCustomPathUnderServletContextWithoutActuatorHttpEndpoints() {
    enabled()
        .withPropertyValues(
            "polyglot.console.path=/ops/console", "polyglot.console.refresh-interval=7s")
        .run(
            context -> {
              MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
              mvc.perform(get("/app/ops/console").contextPath("/app"))
                  .andExpect(status().isFound())
                  .andExpect(header().string("Location", "/app/ops/console/"));
              var page =
                  mvc.perform(get("/app/ops/console/").contextPath("/app"))
                      .andExpect(status().isOk())
                      .andExpect(header().string("Cache-Control", "no-store"))
                      .andReturn()
                      .getResponse()
                      .getContentAsString();
              assertThat(page)
                  .contains("./assets/console.css", "./assets/bootstrap.js")
                  .doesNotContain("/actuator/");
              for (String asset :
                  new String[] {
                    "app.js",
                    "api.js",
                    "model.js",
                    "polling.js",
                    "view.js",
                    "charts.js",
                    "bootstrap.js",
                    "console.css",
                    "icons.svg"
                  }) {
                mvc.perform(get("/app/ops/console/assets/" + asset).contextPath("/app"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
              }
              String body =
                  mvc.perform(get("/app/ops/console/data").contextPath("/app"))
                      .andExpect(status().isOk())
                      .andReturn()
                      .getResponse()
                      .getContentAsString();
              assertThat(body)
                  .contains("7000", "UNKNOWN")
                  .doesNotContain("resourcesPath", "password", "stackTrace");
              mvc.perform(get("/actuator/metrics")).andExpect(status().isNotFound());
              mvc.perform(get("/polyglot-console")).andExpect(status().isNotFound());
            });
  }

  @Test
  void mapsRealTimersByLanguageAndOutcomeWithoutRegisteringMeters() {
    enabled()
        .withUserConfiguration(Observability.class)
        .run(
            context -> {
              MeterRegistry registry = context.getBean(MeterRegistry.class);
              Timer.builder("polyglot.client.invocations")
                  .tags("language", "python", "outcome", "success")
                  .register(registry)
                  .record(20, TimeUnit.MILLISECONDS);
              Timer.builder("polyglot.client.invocations")
                  .tags("language", "python", "outcome", "failure")
                  .register(registry)
                  .record(5, TimeUnit.MILLISECONDS);
              Timer.builder("polyglot.client.invocations")
                  .tags("language", "js", "outcome", "success")
                  .register(registry)
                  .record(100, TimeUnit.MILLISECONDS);
              registry.gauge("polyglot.executor.configured.count", 1);
              int before = registry.getMeters().size();
              var snapshot = context.getBean(ConsoleSnapshotService.class).snapshot();
              assertThat(snapshot.health()).isEqualTo("UP");
              assertThat(snapshot.pythonAvailable()).isTrue();
              assertThat(snapshot.invocations()).hasSize(3);
              var success =
                  snapshot.invocations().stream()
                      .filter(
                          value ->
                              value.language().equals("python")
                                  && value.outcome().equals("success"))
                      .findFirst()
                      .orElseThrow();
              assertThat(success.count()).isEqualTo(1);
              assertThat(success.totalSeconds()).isEqualTo(0.02);
              assertThat(success.windowMaxSeconds()).isEqualTo(0.02);
              assertThat(registry.getMeters()).hasSize(before);
              assertThat(context.getBean(ConsoleSnapshotService.class).snapshot().instanceId())
                  .isEqualTo(snapshot.instanceId());
              String body =
                  MockMvcBuilders.webAppContextSetup(context)
                      .build()
                      .perform(get("/polyglot-console/data"))
                      .andExpect(status().isOk())
                      .andReturn()
                      .getResponse()
                      .getContentAsString();
              assertThat(body).doesNotContain("sensitive", "resourcesPath", "reason", "exception");
            });
  }

  @Test
  void missingRegistryAndHealthRemainUnknownWithoutInitializingAnything() {
    enabled()
        .run(
            context -> {
              var snapshot = context.getBean(ConsoleSnapshotService.class).snapshot();
              assertThat(snapshot.health()).isEqualTo("UNKNOWN");
              assertThat(snapshot.invocations()).isEmpty();
              assertThat(snapshot.pythonAvailable()).isNull();
              assertThat(snapshot.runtime()).allMatch(value -> value.value() == null);
              assertThat(snapshot.configuration()).isEmpty();
            });
  }

  @Test
  void backsOffWhenOptionalWebOrObservabilityClassesAreMissing() {
    for (Class<?> missing :
        new Class<?>[] {
          MeterRegistry.class,
          HealthIndicator.class,
          org.springframework.web.servlet.DispatcherServlet.class
        }) {
      runner
          .withUserConfiguration()
          .withPropertyValues("polyglot.console.enabled=true")
          .withClassLoader(new FilteredClassLoader(missing))
          .run(context -> assertThat(context).doesNotHaveBean(ConsoleController.class));
    }
  }

  @Test
  void rejectsUnsafePathsAndPollingIntervalsWithActionableBindingErrors() {
    for (String path :
        new String[] {
          "/", "relative", "/../private", "/a//b", "/a/", "/a?b", "/actuator", "/error", "/a%2fb"
        }) {
      enabled()
          .withPropertyValues("polyglot.console.path=" + path)
          .run(
              context ->
                  assertThat(context.getStartupFailure())
                      .hasStackTraceContaining("polyglot.console.path"));
    }
    for (String interval : new String[] {"0s", "500ms", "61s", "invalid"}) {
      enabled()
          .withPropertyValues("polyglot.console.refresh-interval=" + interval)
          .run(
              context ->
                  assertThat(context.getStartupFailure())
                      .hasStackTraceContaining("polyglot.console.refresh-interval"));
    }
  }

  @Test
  void restrictsUiAndDataToLoopbackAndRejectsUnrecognizedHosts() {
    enabled()
        .run(
            context -> {
              MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
              for (String path :
                  new String[] {
                    "/polyglot-console/",
                    "/polyglot-console/data",
                    "/polyglot-console/assets/app.js"
                  }) {
                mvc.perform(
                        get(path)
                            .with(
                                request -> {
                                  request.setRemoteAddr("192.0.2.1");
                                  return request;
                                }))
                    .andExpect(status().isForbidden());
                mvc.perform(
                        get(path)
                            .with(
                                request -> {
                                  request.setServerName("untrusted.example");
                                  return request;
                                }))
                    .andExpect(status().isForbidden());
              }
            });
  }

  @Test
  void applicationSecurityChainControlsBothUiAndDataWithoutBypass() {
    enabled()
        .withUserConfiguration(Security.class)
        .run(
            context -> {
              MockMvc mvc =
                  MockMvcBuilders.webAppContextSetup(context)
                      .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                      .build();
              for (String path :
                  new String[] {
                    "/polyglot-console/",
                    "/polyglot-console/data",
                    "/polyglot-console/assets/app.js"
                  }) {
                mvc.perform(get(path)).andExpect(status().isForbidden());
              }
              assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
            });
  }

  private WebApplicationContextRunner enabled() {
    return runner.withPropertyValues("polyglot.console.enabled=true");
  }

  @Test
  void includesEveryGaugeRegisteredByTheRealRuntimeBinder() {
    enabled()
        .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .withBean(PolyglotProperties.class, () -> languages(true, true))
        .run(
            context -> {
              var registry = context.getBean(SimpleMeterRegistry.class);
              var properties = context.getBean(PolyglotProperties.class);
              new io.github.ih0rd.polyglot.annotations.spring.metrics.PolyglotMetricsBinder(
                      context.getBeanProvider(io.github.ih0rd.adapter.context.PyExecutor.class),
                      context.getBeanProvider(io.github.ih0rd.adapter.context.JsExecutor.class),
                      context.getBeanProvider(MeterRegistry.class),
                      properties,
                      () -> 1,
                      () -> 25)
                  .afterSingletonsInstantiated();
              var expected =
                  registry.getMeters().stream()
                      .map(
                          meter ->
                              meter.getId().getName()
                                  + "|"
                                  + java.util.Objects.requireNonNullElse(
                                      meter.getId().getTag("language"), ""))
                      .collect(java.util.stream.Collectors.toSet());
              var actual =
                  context.getBean(ConsoleSnapshotService.class).snapshot().runtime().stream()
                      .map(metric -> metric.name() + "|" + metric.language())
                      .collect(java.util.stream.Collectors.toSet());
              assertThat(actual).hasSize(10).isEqualTo(expected);
            });
  }

  @Test
  void nullHealthSnapshotRemainsUnknown() {
    enabled()
        .withBean("polyglotHealthIndicator", HealthIndicator.class, () -> () -> null)
        .run(
            context ->
                assertThat(context.getBean(ConsoleSnapshotService.class).snapshot().health())
                    .isEqualTo("UNKNOWN"));
  }

  @Test
  void reactiveApplicationsDoNotActivateServletConsoleWiring() {
    new org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConsoleAutoConfiguration.class))
        .withPropertyValues("polyglot.console.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(ConsoleAutoConfiguration.class));
  }

  @Test
  void recordedDownHealthAndUnavailableGaugesRemainSafe() {
    enabled()
        .withBean(
            "polyglotHealthIndicator",
            HealthIndicator.class,
            () -> () -> Health.down().withDetail("password", "sensitive").build())
        .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              var registry = context.getBean(SimpleMeterRegistry.class);
              registry.gauge("polyglot.executor.available.count", Double.NaN);
              var snapshot = context.getBean(ConsoleSnapshotService.class).snapshot();
              assertThat(snapshot.health()).isEqualTo("DOWN");
              assertThat(snapshot.pythonAvailable()).isNull();
              assertThat(snapshot.runtime()).allMatch(value -> value.value() == null);
            });
  }

  @Test
  void multipleUnqualifiedRegistriesDoNotSelectArbitraryMetrics() {
    enabled()
        .withBean("first", SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .withBean("second", SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context ->
                assertThat(context.getBean(ConsoleSnapshotService.class).snapshot().invocations())
                    .isEmpty());
  }

  private static PolyglotProperties languages(boolean python, boolean js) {
    return new PolyglotProperties(
        null,
        new PolyglotProperties.PythonProperties(
            python, "classpath:python", true, false, java.util.List.of()),
        new PolyglotProperties.JsProperties(js, "classpath:js", false, java.util.List.of()),
        null,
        null);
  }

  @Test
  void disabledLanguagesAreExcludedEvenWhenZeroTimersAreAlreadyRegistered() {
    for (boolean python : new boolean[] {true, false}) {
      enabled()
          .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
          .withBean(PolyglotProperties.class, () -> languages(python, !python))
          .run(
              context -> {
                var registry = context.getBean(SimpleMeterRegistry.class);
                for (var language : io.github.ih0rd.polyglot.SupportedLanguage.values()) {
                  for (String outcome : java.util.List.of("success", "failure")) {
                    registry.timer(
                        "polyglot.client.invocations",
                        "language",
                        language.id(),
                        "outcome",
                        outcome);
                  }
                }
                var snapshot = context.getBean(ConsoleSnapshotService.class).snapshot();
                String active = python ? "python" : "js";
                assertThat(snapshot.invocations())
                    .hasSize(2)
                    .allMatch(row -> row.language().equals(active) && row.count() == 0);
                assertThat(snapshot.runtime())
                    .allMatch(row -> row.language().isEmpty() || row.language().equals(active));
                assertThat(registry.getMeters()).hasSize(4);
              });
    }
  }

  @Test
  void disabledCoreDoesNotAdvertiseLanguageMetrics() {
    var defaults = languages(true, true);
    var disabled =
        new PolyglotProperties(
            new PolyglotProperties.CoreProperties(false, true, true, "INFO"),
            defaults.python(),
            defaults.js(),
            defaults.actuator(),
            defaults.metrics());
    enabled()
        .withBean(PolyglotProperties.class, () -> disabled)
        .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              context
                  .getBean(SimpleMeterRegistry.class)
                  .timer("polyglot.client.invocations", "language", "python", "outcome", "success");
              var snapshot = context.getBean(ConsoleSnapshotService.class).snapshot();
              assertThat(snapshot.invocations()).isEmpty();
              assertThat(snapshot.runtime()).hasSize(3).allMatch(row -> row.language().isEmpty());
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  static class MvcConfiguration {}

  @Configuration(proxyBeanMethods = false)
  static class Observability {
    @Bean
    SimpleMeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    HealthIndicator polyglotHealthIndicator() {
      return () ->
          Health.up()
              .withDetail("pythonEnabled", true)
              .withDetail("sensitive", "/private/password")
              .build();
    }

    @Bean
    PolyglotProperties polyglotProperties() {
      return languages(true, true);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebSecurity
  static class Security {
    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(requests -> requests.anyRequest().denyAll()).build();
    }
  }
}
