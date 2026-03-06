import polyglot


class QuoteApi:
    def calculateQuote(self, basePrice, customerTier):
        discount = pricing_rules.discountForTier(customerTier)
        discounted = basePrice * (1.0 - discount)
        vat = discounted * pricing_rules.getVatRate()
        total = discounted + vat

        return {
            "basePrice": basePrice,
            "tier": customerTier,
            "discountRate": discount,
            "vatRate": pricing_rules.getVatRate(),
            "total": round(total, 2),
        }


polyglot.export_value("QuoteApi", QuoteApi)
