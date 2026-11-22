package io.github.ih0rd.examples.contracts;

import io.github.ih0rd.examples.dto.DeliveryEstimate;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;

@PolyglotClient(
    languages = SupportedLanguage.PYTHON,
    script = "contracts/pricing",
    convention = Convention.BY_METHOD_NAME)
public interface DeliveryEstimateApi {
  DeliveryEstimate estimate(String destination);
}
