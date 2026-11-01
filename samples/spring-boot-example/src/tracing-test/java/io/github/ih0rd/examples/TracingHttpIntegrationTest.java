package io.github.ih0rd.examples;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ih0rd.adapter.context.PyExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

class TracingHttpIntegrationTest {
  @Test
  void bootManagedOtelExportsRealHttpChildrenWithoutExternalCollector() throws Exception {
    PyExecutor executor;
    try (var app =
            new SpringApplicationBuilder(
                    PolyglotSpringbootExampleApplication.class, TraceCapture.class)
                .run(
                    "--spring.profiles.active=dev",
                    "--server.port=0",
                    "--logging.level.root=WARN",
                    "--management.tracing.export.otlp.enabled=false",
                    "--spring.main.banner-mode=off");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      executor = app.getBean(PyExecutor.class);
      String base =
          "http://127.0.0.1:" + app.getEnvironment().getRequiredProperty("local.server.port");
      var success =
          http.send(
              HttpRequest.newBuilder(URI.create(base + "/stats/random?n=3"))
                  .timeout(Duration.ofSeconds(30))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(success.statusCode()).isEqualTo(200);
      var failure =
          http.send(
              HttpRequest.newBuilder(URI.create(base + "/analytics/failure"))
                  .timeout(Duration.ofSeconds(30))
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(failure.statusCode()).isEqualTo(500);
      var console =
          http.send(
              HttpRequest.newBuilder(URI.create(base + "/polyglot-console/")).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(console.statusCode()).isEqualTo(200);
      assertThat(console.body()).contains("Polyglot Console");
      var metrics =
          http.send(
              HttpRequest.newBuilder(
                      URI.create(base + "/actuator/metrics/polyglot.client.invocations"))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(metrics.statusCode()).isEqualTo(200);
      assertThat(
              app.getBean(SdkTracerProvider.class)
                  .forceFlush()
                  .join(10, TimeUnit.SECONDS)
                  .isSuccess())
          .isTrue();
      var spans = app.getBean(InMemorySpanExporter.class).getFinishedSpanItems();
      var calls = spans.stream().filter(s -> s.getName().equals("polyglot.invoke")).toList();
      assertThat(calls)
          .hasSize(2)
          .allSatisfy(
              child -> {
                assertThat(child.getAttributes().get(AttributeKey.stringKey("polyglot.language")))
                    .isEqualTo("python");
                assertThat(
                        spans.stream()
                            .filter(s -> s.getKind() == SpanKind.SERVER)
                            .map(s -> s.getSpanId()))
                    .contains(child.getParentSpanId());
              });
      assertThat(spans.stream().filter(s -> s.getKind() == SpanKind.SERVER))
          .hasSize(2)
          .allSatisfy(
              span ->
                  assertThat(span.getAttributes().get(AttributeKey.stringKey("uri")))
                      .doesNotStartWith("/polyglot-console")
                      .doesNotStartWith("/actuator"));
      assertThat(
              app.getBean(MeterRegistry.class)
                  .get("http.server.requests")
                  .tag("uri", "/polyglot-console/")
                  .timer()
                  .count())
          .isEqualTo(1);
      assertThat(calls.getFirst().getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
      assertThat(calls.getLast().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
      assertThat(
              app
                  .getBean(MeterRegistry.class)
                  .get("polyglot.client.invocations")
                  .tag("language", "python")
                  .timers()
                  .stream()
                  .mapToLong(Timer::count)
                  .sum())
          .isEqualTo(2);
    }
    assertThat(executor.isClosed()).isTrue();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TraceCapture {
    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
      return InMemorySpanExporter.create();
    }
  }
}
