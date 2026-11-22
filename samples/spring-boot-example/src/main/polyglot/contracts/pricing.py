import polyglot


def quote(unit_price_cents: int, quantity: int) -> int:
    subtotal = unit_price_cents * quantity
    discount = subtotal // 10 if quantity >= 10 else 0
    return subtotal - discount


def estimate(destination: str) -> dict:
    if destination == "local":
        return {"days": 2, "serviceLevel": "domestic"}
    return {"days": 5, "serviceLevel": "international"}


polyglot.export_value("PricingApi", {"quote": quote})
