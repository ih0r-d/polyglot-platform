package io.github.ih0rd.polyglot.annotations.spring.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PolyglotMetricsBinderTest {

  @Mock private PyExecutor pyExecutor;
  @Mock private JsExecutor jsExecutor;

  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  void bindToRegistersPythonAndJsGaugesFromMetadata() {
    when(pyExecutor.metadata())
        .thenReturn(
            Map.of(
                "sourceCacheSize", 2,
                "instanceCacheSize", 3,
                "cachedInterfaces", List.of("a", "b")));
    when(jsExecutor.metadata())
        .thenReturn(Map.of("sourceCacheSize", 4, "loadedInterfaces", List.of("Api")));

    PolyglotMetricsBinder binder =
        new PolyglotMetricsBinder(provider(pyExecutor), provider(jsExecutor));
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    binder.bindTo(registry);

    assertEquals(
        2.0,
        registry
            .find("polyglot.executor.source.cache.size")
            .tag("language", "python")
            .gauge()
            .value());
    assertEquals(
        3.0,
        registry
            .find("polyglot.python.instance.cache.size")
            .tag("language", "python")
            .gauge()
            .value());
    assertEquals(
        1.0,
        registry.find("polyglot.js.loaded.interfaces.count").tag("language", "js").gauge().value());
  }

  @Test
  void bindToTreatsMissingMetadataAsZero() {
    when(pyExecutor.metadata()).thenReturn(Map.of("cachedInterfaces", "not-a-collection"));

    PolyglotMetricsBinder binder = new PolyglotMetricsBinder(provider(pyExecutor), provider(null));
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    binder.bindTo(registry);

    assertEquals(
        0.0,
        registry
            .find("polyglot.executor.source.cache.size")
            .tag("language", "python")
            .gauge()
            .value());
    assertEquals(
        0.0,
        registry
            .find("polyglot.python.bound.interfaces.count")
            .tag("language", "python")
            .gauge()
            .value());
  }

  private static <T> ObjectProvider<T> provider(T instance) {
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

      @Override
      public Stream<T> stream() {
        return instance == null ? Stream.empty() : Stream.of(instance);
      }

      @Override
      public Stream<T> orderedStream() {
        return stream();
      }
    };
  }
}
