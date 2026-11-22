import polyglot


def quote(unit_price_cents: int, quantity: int, extra_fee_cents: int) -> int:
    return unit_price_cents * quantity + extra_fee_cents


def estimate(destination: str) -> dict:
    if destination == "local":
        return {"days": 2, "serviceLevel": "domestic"}
    return {"days": 5, "serviceLevel": "international"}


polyglot.export_value("PricingApi", {"quote": quote})
