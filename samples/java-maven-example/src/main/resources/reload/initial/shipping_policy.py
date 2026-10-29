FREE_SHIPPING_FROM_CENTS = 10_000
STANDARD_SHIPPING_CENTS = 500


def quote(subtotal_cents):
    if subtotal_cents < 0:
        raise ValueError("subtotal must be non-negative")
    return 0 if subtotal_cents >= FREE_SHIPPING_FROM_CENTS else STANDARD_SHIPPING_CENTS


class ShippingPolicy:
    def quote(self, subtotal_cents):
        return quote(subtotal_cents)
