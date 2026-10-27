package io.github.ih0rd.examples;

import java.util.Arrays;
import java.util.List;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.ih0rd.examples.dto.AnalyticsSummary;
import io.github.ih0rd.examples.dto.RiskAssessment;
import io.github.ih0rd.examples.dto.RollingResult;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.context.PolyglotContextCustomizer;

/** Copies the new analytics results under the executor lock, preserving legacy Map conversions. */
@Configuration(proxyBeanMethods = false)
public class AnalyticsConfiguration {
  @Bean
  PolyglotContextCustomizer detachedResults() {
    HostAccess access =
        HostAccess.newBuilder(HostAccess.ALL)
            .targetTypeMapping(
                Value.class,
                AnalyticsSummary.class,
                Value::hasHashEntries,
                AnalyticsConfiguration::summary)
            .targetTypeMapping(
                Value.class,
                RollingResult.class,
                Value::hasHashEntries,
                value ->
                    new RollingResult(
                        value.getHashValue("window").asInt(),
                        value.getHashValue("firstIndex").asInt(),
                        doubles(value.getHashValue("means"))))
            .targetTypeMapping(
                Value.class,
                RiskAssessment.class,
                Value::hasHashEntries,
                value ->
                    new RiskAssessment(
                        number(value, "threshold"),
                        doubles(value.getHashValue("zScores")),
                        Arrays.stream(value.getHashValue("anomalyIndices").as(int[].class))
                            .boxed()
                            .toList(),
                        value.getHashValue("anomalyCount").asInt(),
                        number(value, "riskScore"),
                        number(value, "qualityScore")))
            .build();
    return (language, builder) -> {
      if (language == SupportedLanguage.PYTHON) {
        builder.allowHostAccess(access);
      }
    };
  }

  private static AnalyticsSummary summary(Value value) {
    return new AnalyticsSummary(
        value.getHashValue("count").asInt(),
        number(value, "mean"),
        number(value, "median"),
        number(value, "standardDeviation"),
        number(value, "minimum"),
        number(value, "maximum"),
        number(value, "p05"),
        number(value, "p95"),
        value.getHashValue("numpyVersion").asString());
  }

  private static double number(Value value, String key) {
    return value.getHashValue(key).asDouble();
  }

  private static List<Double> doubles(Value value) {
    return Arrays.stream(value.as(double[].class)).boxed().toList();
  }
}
