package io.github.ih0rd.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.ih0rd.adapter.context.PyExecutor;

class ObservabilityDemoTest {

  private static final String METRIC = "/actuator/metrics/polyglot.client.invocations";

  @Test
  void servesPythonAndObservabilityOverRealHttp() throws IOException, InterruptedException {
    verifyDemo(true);
  }

  static void verifyDemo(boolean enabled) throws IOException, InterruptedException {
    Map<String, JsonElement> responses = new LinkedHashMap<>();
    PyExecutor executor;
    try (ConfigurableApplicationContext application =
            new SpringApplicationBuilder(PolyglotSpringbootExampleApplication.class)
                .run(
                    "--spring.profiles.active=observability",
                    "--server.port=0",
                    "--polyglot.metrics.enabled=" + enabled,
                    "--spring.main.banner-mode=off",
                    "--logging.level.root=WARN");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      int port =
          application.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
      String base = "http://127.0.0.1:" + port;
      executor = application.getBean(PyExecutor.class);

      JsonObject health = get(http, base, "/actuator/health", 200);
      assertThat(healthStatus(health)).isEqualTo("UP");
      assertThat(healthStatus(health.getAsJsonObject("components").getAsJsonObject("polyglot")))
          .isEqualTo("UP");
      responses.put("health", health);
      JsonObject info = get(http, base, "/actuator/info", 200);
      assertThat(info.getAsJsonObject("python").get("available").getAsBoolean()).isTrue();
      assertThat(info.getAsJsonObject("executors").get("availableCount").getAsInt()).isEqualTo(1);
      responses.put("info", info);

      if (enabled) {
        assertThat(
                measure(
                    get(http, base, METRIC + "?tag=language:python&tag=outcome:success", 200),
                    "COUNT"))
            .isZero();
      }
      HttpResponse<String> numbers =
          http.send(
              request(base, "/stats/random?n=3").GET().build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(numbers.statusCode()).isEqualTo(200);
      assertThat(JsonParser.parseString(numbers.body()).getAsJsonArray()).hasSize(3);
      responses.put("pythonNumbers", JsonParser.parseString(numbers.body()));
      responses.put("pythonStats", get(http, base, "/stats/data?n=3", 200));

      HttpResponse<String> failure =
          http.send(
              request(base, "/forecast?steps=0&seasonPeriod=4")
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("[1,2,3,4]"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(failure.statusCode()).isEqualTo(500);
      responses.put("pythonFailure", JsonParser.parseString(failure.body()));

      // Runtime liveness is not rechecked by this startup-snapshot health contributor.
      assertThat(healthStatus(get(http, base, "/actuator/health", 200))).isEqualTo("UP");
      if (enabled) {
        JsonObject success =
            get(http, base, METRIC + "?tag=language:python&tag=outcome:success", 200);
        JsonObject failed =
            get(http, base, METRIC + "?tag=language:python&tag=outcome:failure", 200);
        JsonObject all = get(http, base, METRIC, 200);
        assertThat(measure(success, "COUNT")).isEqualTo(2);
        assertThat(measure(failed, "COUNT")).isEqualTo(1);
        assertThat(measure(success, "TOTAL_TIME")).isPositive();
        assertThat(measure(failed, "TOTAL_TIME")).isPositive();
        assertThat(success.get("baseUnit").getAsString()).isEqualTo("seconds");
        assertBoundedTags(all);
        assertThat(measure(all, "COUNT")).isEqualTo(3);
        responses.put("successMetric", success);
        responses.put("failureMetric", failed);
        responses.put("aggregateMetric", all);
      } else {
        get(http, base, METRIC, 404);
      }
      assertThat(executor.isClosed()).isFalse();
    }
    assertThat(executor.isClosed()).isTrue();
    Files.writeString(
        Path.of("target", "observability-" + enabled + ".json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(responses));
  }

  private static HttpRequest.Builder request(String base, String path) {
    return HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
  }

  private static String healthStatus(JsonObject health) {
    // Gson exposes the contributor wrapper and serializes Status as an object with a code field.
    JsonObject content = health.has("health") ? health.getAsJsonObject("health") : health;
    assertThat(content.has("status")).as("Health response: %s", health).isTrue();
    JsonElement status = content.get("status");
    return status.isJsonPrimitive()
        ? status.getAsString()
        : status.getAsJsonObject().get("code").getAsString();
  }

  private static JsonObject get(HttpClient http, String base, String path, int status)
      throws IOException, InterruptedException {
    HttpResponse<String> response =
        http.send(request(base, path).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(path).isEqualTo(status);
    if (status == 404) {
      return new JsonObject();
    }
    return JsonParser.parseString(response.body()).getAsJsonObject();
  }

  private static void assertBoundedTags(JsonObject metric) {
    Map<String, List<String>> expected =
        Map.of("language", List.of("python", "js"), "outcome", List.of("success", "failure"));
    var tags = metric.getAsJsonArray("availableTags");
    assertThat(
            StreamSupport.stream(tags.spliterator(), false)
                .map(tag -> tag.getAsJsonObject().get("tag").getAsString())
                .toList())
        .containsExactlyInAnyOrderElementsOf(expected.keySet());
    for (JsonElement element : tags) {
      JsonObject tag = element.getAsJsonObject();
      assertThat(
              StreamSupport.stream(tag.getAsJsonArray("values").spliterator(), false)
                  .map(JsonElement::getAsString)
                  .toList())
          .containsExactlyInAnyOrderElementsOf(expected.get(tag.get("tag").getAsString()));
    }
  }

  private static double measure(JsonObject metric, String statistic) {
    for (JsonElement measurement : metric.getAsJsonArray("measurements")) {
      JsonObject value = measurement.getAsJsonObject();
      if (statistic.equals(value.get("statistic").getAsString())) {
        return value.get("value").getAsDouble();
      }
    }
    throw new AssertionError("Missing metric statistic: " + statistic);
  }
}
