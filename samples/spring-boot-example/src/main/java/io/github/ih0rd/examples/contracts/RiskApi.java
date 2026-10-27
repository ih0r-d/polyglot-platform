package io.github.ih0rd.examples.contracts;

import java.util.List;

import io.github.ih0rd.examples.dto.RiskAssessment;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;

/** Deterministic telemetry checks using top-level Python functions. */
@PolyglotClient(languages = SupportedLanguage.PYTHON, convention = Convention.BY_METHOD_NAME)
public interface RiskApi {
  RiskAssessment assess(List<Double> samples, double threshold);

  void fail();
}
