import polyglot

class PricingApi:
    def quote(self, sku: str, quantity: int) -> int:
        return len(sku) * quantity

polyglot.export_value('PricingApi', PricingApi)
