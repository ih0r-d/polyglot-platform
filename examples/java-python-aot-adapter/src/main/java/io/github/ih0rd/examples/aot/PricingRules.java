package io.github.ih0rd.examples.aot;

public class PricingRules {
    private final double vatRate;

    public PricingRules(double vatRate) {
        this.vatRate = vatRate;
    }

    public double getVatRate() {
        return vatRate;
    }

    public double discountForTier(String tier) {
        return switch (tier) {
            case "GOLD" -> 0.15;
            case "SILVER" -> 0.08;
            default -> 0.0;
        };
    }
}
