package io.github.ih0rd.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.exporter.FinishedSpan;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DevTracingConfigurationTest {
  @Test
  void excludesOnlyDiagnosticGetServerSpansAtPathBoundaries() {
    var predicate =
        new DevTracingConfiguration().diagnosticPollingSpans("/ops/console", "/manage", "/app");
    for (String path :
        new String[] {
          "/ops/console",
          "/ops/console/data",
          "/manage/metrics",
          "/manage/health/readiness",
          "/manage/info",
          "/app/ops/console/data"
        }) {
      assertThat(predicate.isExportable(span(Span.Kind.SERVER, "GET", path))).isFalse();
      assertThat(predicate.isExportable(span(Span.Kind.SERVER, "POST", path))).isTrue();
      assertThat(predicate.isExportable(span(Span.Kind.CLIENT, "GET", path))).isTrue();
    }
    for (String path :
        new String[] {
          "/ops/console-business",
          "/manage/metrics-business",
          "/analytics/summary",
          "/stats/random",
          "/manage/loggers"
        }) {
      assertThat(predicate.isExportable(span(Span.Kind.SERVER, "GET", path))).isTrue();
    }
  }

  @Test
  void preservesUnknownRoutesAndSupportsRootActuatorBasePath() {
    var predicate = new DevTracingConfiguration().diagnosticPollingSpans("/console", "/", "");
    assertThat(predicate.isExportable(span(Span.Kind.SERVER, "GET", "/metrics/name"))).isFalse();
    assertThat(predicate.isExportable(span(Span.Kind.SERVER, "GET", "/health"))).isFalse();
    var unknown = mock(FinishedSpan.class);
    when(unknown.getKind()).thenReturn(Span.Kind.SERVER);
    when(unknown.getTags()).thenReturn(Map.of("method", "GET"));
    assertThat(predicate.isExportable(unknown)).isTrue();
  }

  private FinishedSpan span(Span.Kind kind, String method, String uri) {
    var span = mock(FinishedSpan.class);
    when(span.getKind()).thenReturn(kind);
    when(span.getTags()).thenReturn(Map.of("method", method, "uri", uri));
    return span;
  }
}
