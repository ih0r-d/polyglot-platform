package io.github.ih0rd.examples.dto;

/** Detached HTTP response assembled from the two Python client results. */
public record PricingQuote(int totalCents, DeliveryEstimate delivery) {}
