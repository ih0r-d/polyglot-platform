package io.github.ih0rd.examples.contracts;

import java.util.List;

import io.github.ih0rd.examples.dto.AnalyticsSummary;
import io.github.ih0rd.examples.dto.RollingResult;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;

/** NumPy analytics using the default exported Python class convention. */
@PolyglotClient(languages = SupportedLanguage.PYTHON)
public interface AnalyticsApi {
  AnalyticsSummary describe(List<Double> samples);

  RollingResult rolling(List<Double> samples, int window);
}
