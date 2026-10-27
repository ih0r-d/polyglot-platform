package io.github.ih0rd.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import io.github.ih0rd.examples.dto.AnalyticsSummary;
import io.github.ih0rd.examples.dto.RiskAssessment;
import io.github.ih0rd.examples.dto.RollingResult;
import io.github.ih0rd.examples.dto.SeriesRequest;
import io.github.ih0rd.polyglot.SupportedLanguage;

class AnalyticsMappingTest {
  @Test
  void convertsGuestDictionariesToDetachedRecordsWithoutNativePackages() {
    var builder =
        Context.newBuilder("python")
            .allowAllAccess(true)
            .option("engine.WarnInterpreterOnly", "false");
    var customizer = new AnalyticsConfiguration().detachedResults();
    customizer.customize(SupportedLanguage.JS, builder);
    customizer.customize(SupportedLanguage.PYTHON, builder);
    AnalyticsSummary summary;
    RollingResult rolling;
    RiskAssessment risk;
    try (Context context = builder.build()) {
      summary =
          context
              .eval(
                  "python",
                  "{'count':3,'mean':2,'median':2,'standardDeviation':1,'minimum':1,'maximum':3,'p05':1.1,'p95':2.9,'numpyVersion':'test'}")
              .as(AnalyticsSummary.class);
      rolling =
          context
              .eval("python", "{'window':2,'firstIndex':1,'means':[1.5,2.5]}")
              .as(RollingResult.class);
      risk =
          context
              .eval(
                  "python",
                  "{'threshold':1.5,'zScores':[0,2],'anomalyIndices':[1],'anomalyCount':1,'riskScore':50,'qualityScore':50}")
              .as(RiskAssessment.class);
    }
    assertThat(summary.mean()).isEqualTo(2);
    assertThat(rolling.means()).containsExactly(1.5, 2.5);
    assertThat(risk.anomalyIndices()).containsExactly(1);
    assertThatThrownBy(() -> rolling.means().add(4.0))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void inputDtoRetainsAnImmutableCopyAndRepresentsMissingInput() {
    List<Double> values = new ArrayList<>(List.of(1.0));
    SeriesRequest input = new SeriesRequest(values);
    values.set(0, 2.0);
    assertThat(input.samples()).containsExactly(1.0);
    assertThatThrownBy(() -> input.samples().add(3.0))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(new SeriesRequest(null).samples()).isNull();
  }
}
