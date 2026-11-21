package consumer;

import consumer.generated.PricingApi;

final class Consumer {
    int quote(PricingApi api, String sku) {
        return api.quote(sku);
    }
}
