package io.github.ih0rd.examples.aot;

import java.util.Map;

public interface QuoteApi {
    Map<String, Object> calculateQuote(double basePrice, String customerTier);
}
