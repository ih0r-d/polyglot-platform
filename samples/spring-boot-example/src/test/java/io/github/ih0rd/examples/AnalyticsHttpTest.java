package io.github.ih0rd.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonParser;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.examples.contracts.AnalyticsApi;
import io.github.ih0rd.examples.contracts.RiskApi;
import io.github.ih0rd.examples.dto.AnalyticsSummary;
import io.github.ih0rd.examples.dto.RiskAssessment;
import io.github.ih0rd.examples.dto.RollingResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalyticsHttpTest {
  private ConfigurableApplicationContext application;
  private AnalyticsApi analytics;
  private RiskApi risk;
  private String base;
  private AnalyticsSummary detached;

  @BeforeAll
  void start() {
    application =
        new SpringApplicationBuilder(PolyglotSpringbootExampleApplication.class)
            .run(
                "--spring.profiles.active=observability,console",
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN");
    analytics = application.getBean(AnalyticsApi.class);
    risk = application.getBean(RiskApi.class);
    base =
        "http://127.0.0.1:" + application.getEnvironment().getRequiredProperty("local.server.port");
    detached = analytics.describe(List.of(1.0, 2.0, 3.0, 4.0, 5.0));
  }

  @AfterAll
  void stop() {
    if (application != null) {
      PyExecutor executor = application.getBean(PyExecutor.class);
      application.close();
      assertThat(executor.isClosed()).isTrue();
      // All result containers are ordinary detached Java values, safe after shutdown too.
      assertThat(detached.mean()).isEqualTo(3.0);
    }
  }

  @Test
  void servesConsoleAndJavaScriptThroughTheRealHttpServer() throws Exception {
    try (HttpClient http = HttpClient.newHttpClient()) {
      String consolePath =
          application.getEnvironment().getProperty("polyglot.console.path", "/polyglot-console");
      for (String suffix :
          List.of("/", "/assets/js/app.js", "/assets/css/console.css", "/data")) {
        String path = consolePath + suffix;
        var response =
            http.send(
                HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
        assertThat(response.body()).isNotBlank();
      }
    }
  }

  @Test
  void resultRecordsDefensivelyCopyLists() {
    List<Double> values = new ArrayList<>(List.of(1.0));
    RollingResult rolling = new RollingResult(1, 0, values);
    RiskAssessment assessment = new RiskAssessment(1.5, values, List.of(0), 1, 100, 0);
    values.set(0, 9.0);
    assertThat(rolling.means()).containsExactly(1.0);
    assertThat(assessment.zScores()).containsExactly(1.0);
    assertThatThrownBy(() -> rolling.means().add(2.0))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> assessment.anomalyIndices().add(2))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void verifiesNumPyStatisticsAndRollingReferenceValues() {
    AnalyticsSummary stats = analytics.describe(List.of(1.0, 2.0, 3.0, 4.0, 5.0));
    assertThat(stats.numpyVersion()).isEqualTo("2.2.4");
    assertNumber(stats.mean(), 3.0);
    assertNumber(stats.median(), 3.0);
    assertNumber(stats.standardDeviation(), Math.sqrt(2));
    assertNumber(stats.minimum(), 1);
    assertNumber(stats.maximum(), 5);
    assertNumber(stats.p05(), 1.2);
    assertNumber(stats.p95(), 4.8);
    assertThat(analytics.rolling(List.of(1.0, 2.0, 3.0, 4.0), 2).means())
        .containsExactly(1.5, 2.5, 3.5);
    assertThat(analytics.rolling(List.of(7.0), 1).means()).containsExactly(7.0);
  }

  @Test
  void verifiesMethodNameRiskAndZeroVariance() {
    RiskAssessment result = risk.assess(List.of(10.0, 10.0, 10.0, 10.0, 100.0), 1.5);
    assertThat(result.anomalyIndices()).containsExactly(4);
    assertThat(result.zScores()).containsExactly(-0.5, -0.5, -0.5, -0.5, 2.0);
    assertNumber(result.riskScore(), 20);
    assertNumber(result.qualityScore(), 80);
    RiskAssessment constant = risk.assess(List.of(7.0, 7.0, 7.0), 1.5);
    assertThat(constant.zScores()).containsExactly(0.0, 0.0, 0.0);
    assertNumber(constant.qualityScore(), 100);
    assertThatThrownBy(() -> analytics.describe(List.of())).isInstanceOf(InvocationException.class);
    assertThatThrownBy(() -> analytics.describe(List.of(Double.NaN)))
        .isInstanceOf(InvocationException.class);
    assertThatThrownBy(() -> analytics.rolling(List.of(1.0), 2))
        .isInstanceOf(InvocationException.class);
    assertThatThrownBy(() -> risk.assess(List.of(1.0), 0)).isInstanceOf(InvocationException.class);
  }

  @Test
  void translatesMalformedAndInvalidRequestsBeforeGuestExecution()
      throws IOException, InterruptedException {
    try (HttpClient http = HttpClient.newHttpClient()) {
      for (String payload :
          List.of(
              "{",
              "{}",
              "{\"samples\":[]}",
              "{\"samples\":[null]}",
              "{\"samples\":[\"abc\"]}",
              "{\"samples\":[1e309]}",
              "{\"samples\":[1e101]}")) {
        assertInvalid(http, "/analytics/summary", payload);
      }
      for (String path :
          List.of(
              "/analytics/rolling?window=0",
              "/analytics/rolling?window=2",
              "/analytics/rolling?window=abc",
              "/analytics/rolling",
              "/analytics/risk?threshold=NaN",
              "/analytics/risk?threshold=0",
              "/analytics/risk?threshold=abc")) {
        assertInvalid(http, path, "{\"samples\":[1]}");
      }
    }
  }

  @Test
  void concurrentRequestsAcrossBothContractsRecordExactDeltas()
      throws IOException, InterruptedException, ExecutionException {
    try (HttpClient http = HttpClient.newHttpClient()) {
      double successBefore = count(http, "success");
      double failureBefore = count(http, "failure");
      List<CompletableFuture<HttpResponse<String>>> responses = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        String path;
        if (i >= 8) {
          path = "/analytics/failure";
        } else if (i % 2 == 0) {
          path = "/analytics/summary";
        } else {
          path = "/analytics/risk?threshold=1.5";
        }
        responses.add(
            http.sendAsync(
                HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"samples\":[10,10,10,10,100]}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString()));
      }
      for (int i = 0; i < responses.size(); i++) {
        HttpResponse<String> response = responses.get(i).get();
        assertThat(response.statusCode()).isEqualTo(i >= 8 ? 500 : 200);
        assertThat(response.body()).doesNotContain("Synthetic analytics failure");
      }
      assertThat(count(http, "success") - successBefore).isEqualTo(8);
      assertThat(count(http, "failure") - failureBefore).isEqualTo(2);
    }
  }

  private double count(HttpClient http, String outcome) throws IOException, InterruptedException {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(
                        base
                            + "/actuator/metrics/polyglot.client.invocations?tag=language:python&tag=outcome:"
                            + outcome))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    for (var metric :
        JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("measurements")) {
      if (metric.getAsJsonObject().get("statistic").getAsString().equals("COUNT")) {
        return metric.getAsJsonObject().get("value").getAsDouble();
      }
    }
    throw new AssertionError("Missing invocation Timer COUNT");
  }

  private void assertInvalid(HttpClient http, String path, String payload)
      throws IOException, InterruptedException {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(path + " " + payload).isEqualTo(400);
    assertThat(JsonParser.parseString(response.body()).getAsJsonObject().get("code").getAsString())
        .isEqualTo("INVALID_INPUT");
  }

  private static void assertNumber(double actual, double expected) {
    assertThat(actual).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
  }
}
