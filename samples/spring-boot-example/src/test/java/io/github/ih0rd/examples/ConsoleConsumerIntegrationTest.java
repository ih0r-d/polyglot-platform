package io.github.ih0rd.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import com.google.gson.JsonParser;

/** Real dependency-JAR consumer: no console classes or assets live in the sample. */
class ConsoleConsumerIntegrationTest {
  @Test
  void servesDependencyAssetsAndRealMetricsWithoutActuatorHttpExposure()
      throws IOException, InterruptedException {
    try (var application = new SpringApplicationBuilder(PolyglotSpringbootExampleApplication.class)
            .run("--spring.profiles.active=observability,console", "--server.port=0",
                "--server.servlet.context-path=/consumer", "--polyglot.console.path=/ops/python",
                "--polyglot.console.refresh-interval=7s", "--management.endpoints.web.exposure.exclude=*",
                "--logging.level.root=WARN", "--spring.main.banner-mode=off");
        var http = HttpClient.newHttpClient()) {
      String base = "http://127.0.0.1:" + application.getEnvironment().getRequiredProperty("local.server.port") + "/consumer";
      var resource = getClass().getClassLoader().getResource("META-INF/polyglot-console/index.html");
      assertThat(resource).isNotNull();
      assertThat(resource.getProtocol()).isEqualTo("jar");
      var redirect = get(http, base + "/ops/python");
      assertThat(redirect.statusCode()).isEqualTo(302);
      assertThat(redirect.headers().firstValue("location")).contains("/consumer/ops/python/");
      for (String path : new String[] {"/", "/assets/api.js", "/assets/console.css", "/data"}) {
        assertThat(get(http, base + "/ops/python" + path).statusCode()).as(path).isEqualTo(200);
      }
      assertThat(get(http, base + "/actuator/metrics").statusCode()).isEqualTo(404);
      assertThat(get(http, base + "/actuator/health").statusCode()).isEqualTo(404);
      String before = get(http, base + "/ops/python/data").body();
      var invocation = http.send(HttpRequest.newBuilder(URI.create(base + "/analytics/summary"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{\"samples\":[1,2,3]}" )).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(invocation.statusCode()).isEqualTo(200);
      String after = get(http, base + "/ops/python/data").body();
      assertThat(count(after)).isEqualTo(count(before) + 1);
      var snapshot = JsonParser.parseString(after).getAsJsonObject();
      assertThat(snapshot.get("health").getAsString()).isEqualTo("UP");
      assertThat(snapshot.get("refreshIntervalMillis").getAsInt()).isEqualTo(7000);
      assertThat(after).doesNotContain("resourcesPath", "classpath:", "stackTrace");
    }
  }

  private static long count(String json) {
    for (var item : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("invocations")) {
      var invocation = item.getAsJsonObject();
      if (invocation.get("language").getAsString().equals("python") && invocation.get("outcome").getAsString().equals("success")) {
        return invocation.get("count").getAsLong();
      }
    }
    throw new AssertionError("Missing Python success Timer");
  }

  private static HttpResponse<String> get(HttpClient http, String url) throws IOException, InterruptedException {
    return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }
}
