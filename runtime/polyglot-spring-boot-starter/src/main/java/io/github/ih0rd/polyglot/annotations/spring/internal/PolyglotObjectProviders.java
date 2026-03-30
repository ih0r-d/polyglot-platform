package io.github.ih0rd.polyglot.annotations.spring.internal;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

/** Shared helpers for adapting optional instances to {@link ObjectProvider}. */
public final class PolyglotObjectProviders {

  private PolyglotObjectProviders() {}

  public static <T> ObjectProvider<T> providerOf(@Nullable T instance) {
    return new ObjectProvider<>() {
      @Override
      public T getObject(Object... args) {
        return instance;
      }

      @Override
      public T getIfAvailable() {
        return instance;
      }

      @Override
      public T getIfUnique() {
        return instance;
      }

      @Override
      public T getObject() {
        return instance;
      }
    };
  }
}
