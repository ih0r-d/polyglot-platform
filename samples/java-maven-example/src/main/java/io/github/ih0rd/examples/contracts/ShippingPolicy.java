package io.github.ih0rd.examples.contracts;

/** Python shipping policy; all amounts are non-negative integer cents. */
public interface ShippingPolicy {

    /** Returns the shipping charge for an order subtotal, both expressed in cents. */
    int quote(int subtotalCents);
}
