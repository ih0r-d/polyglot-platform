package consumer;

import consumer.generated.Api;

public final class Consumer {
  public String call(Api api) {
    return api.hello("Ada");
  }
}
