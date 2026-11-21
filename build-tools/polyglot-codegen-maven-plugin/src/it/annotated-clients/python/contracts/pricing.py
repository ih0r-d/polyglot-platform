import polyglot

class PricingApi:
    def quote(self, sku: str) -> int:
        return len(sku)

polyglot.export_value('PricingApi', PricingApi)
