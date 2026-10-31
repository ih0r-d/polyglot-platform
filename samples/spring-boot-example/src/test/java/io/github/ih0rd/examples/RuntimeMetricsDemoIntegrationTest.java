package io.github.ih0rd.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.google.gson.JsonParser;

import io.micrometer.core.instrument.MeterRegistry;

class RuntimeMetricsDemoIntegrationTest {
  @Test
  void profileUsesRealClientsAndConsoleWithoutMetricsHttpExposure() throws Exception {
    try (ConfigurableApplicationContext application =
            new SpringApplicationBuilder(PolyglotSpringbootExampleApplication.class)
                .run(
                    "--spring.profiles.active=observability,runtime-metrics-demo",
                    "--server.port=0",
                    "--polyglot.console.path=/runtime-console",
                    "--management.endpoints.web.exposure.include=health",
                    "--spring.main.banner-mode=off",
                    "--logging.level.root=WARN");
        HttpClient http = HttpClient.newHttpClient()) {
      MeterRegistry registry = application.getBean(MeterRegistry.class);
      assertThat(
              registry
                  .get("polyglot.runtime.operations")
                  .tags("language", "python", "operation", "reload", "outcome", "success")
                  .timer()
                  .count())
          .isEqualTo(2);
      assertThat(
              registry
                  .get("polyglot.runtime.operations")
                  .tags("language", "python", "operation", "reload", "outcome", "failure")
                  .timer()
                  .count())
          .isEqualTo(1);
      assertThat(registry.get("polyglot.runtime.invocations.active").gauge().value()).isZero();
      assertThat(registry.get("polyglot.runtime.invocations.waiting").gauge().value()).isZero();
      int port =
          application.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
      String base = "http://127.0.0.1:" + port;
      var data =
          http.send(
              HttpRequest.newBuilder(URI.create(base + "/runtime-console/data")).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(data.statusCode()).isEqualTo(200);
      var snapshot = JsonParser.parseString(data.body()).getAsJsonObject();
      assertThat(snapshot.getAsJsonArray("operations").size()).isPositive();
      assertThat(snapshot.getAsJsonArray("caches").size()).isEqualTo(3);
      assertThat(
              http.send(
                      HttpRequest.newBuilder(URI.create(base + "/actuator/metrics")).GET().build(),
                      HttpResponse.BodyHandlers.discarding())
                  .statusCode())
          .isEqualTo(404);
      assertThat(data.body())
          .doesNotContain("Synthetic analytics failure", "risk_api", "resourcesPath");
    }
  }
}
