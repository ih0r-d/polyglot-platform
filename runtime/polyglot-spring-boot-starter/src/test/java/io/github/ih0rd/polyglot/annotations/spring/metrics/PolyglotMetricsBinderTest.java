package io.github.ih0rd.polyglot.annotations.spring.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotRuntimeState;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;
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

    PolyglotRuntimeState runtimeState = runtimeState(42, 2);
    PolyglotMetricsBinder binder =
        new PolyglotMetricsBinder(
            provider(pyExecutor),
            provider(jsExecutor),
            provider(registry()),
            enabledProperties(true),
            runtimeState::availableExecutors,
            runtimeState::startupDurationMs);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    binder.bindTo(registry);

    assertEquals(
        2.0,
        Objects.requireNonNull(
                registry
                    .find("polyglot.executor.source.cache.size")
                    .tag("language", "python")
                    .gauge())
            .value());
    assertEquals(
        3.0,
        Objects.requireNonNull(
                registry
                    .find("polyglot.python.instance.cache.size")
                    .tag("language", "python")
                    .gauge())
            .value());
    assertEquals(
        3.0,
        Objects.requireNonNull(
                registry
                    .find("polyglot.executor.contract.cache.size")
                    .tag("language", "python")
                    .gauge())
            .value());
    assertEquals(
        1.0,
        Objects.requireNonNull(
                registry.find("polyglot.js.loaded.interfaces.count").tag("language", "js").gauge())
            .value());
    assertEquals(
        1.0,
        Objects.requireNonNull(
                registry
                    .find("polyglot.executor.contract.cache.size")
                    .tag("language", "js")
                    .gauge())
            .value());
    assertEquals(
        2.0,
        Objects.requireNonNull(registry.find("polyglot.executor.available.count").gauge()).value());
    assertEquals(
        2.0,
        Objects.requireNonNull(registry.find("polyglot.executor.configured.count").gauge())
            .value());
    assertEquals(
        42.0, Objects.requireNonNull(registry.find("polyglot.startup.duration").gauge()).value());
  }

  @Test
  void bindToTreatsMissingMetadataAsZero() {
    when(pyExecutor.metadata()).thenReturn(Map.of("cachedInterfaces", "not-a-collection"));

    PolyglotRuntimeState runtimeState = runtimeState(-1, 1);
    PolyglotMetricsBinder binder =
        new PolyglotMetricsBinder(
            provider(pyExecutor),
            provider(null),
            provider(registry()),
            enabledProperties(false),
            runtimeState::availableExecutors,
            runtimeState::startupDurationMs);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    binder.bindTo(registry);

    assertEquals(
        0.0,
        Objects.requireNonNull(
                registry
                    .find("polyglot.executor.source.cache.size")
                    .tag("language", "python")
                    .gauge())
            .value());
    assertEquals(
        0.0,
        Objects.requireNonNull(
                registry
                    .find("polyglot.python.bound.interfaces.count")
                    .tag("language", "python")
                    .gauge())
            .value());
  }

  @Test
  void bindToDoesNotResolveExecutorsEagerly() {
    AtomicBoolean accessed = new AtomicBoolean(false);
    ObjectProvider<PyExecutor> pyProvider = trackingProvider(pyExecutor, accessed);

    PolyglotRuntimeState runtimeState = runtimeState(-1, 1);
    PolyglotMetricsBinder binder =
        new PolyglotMetricsBinder(
            pyProvider,
            provider(null),
            provider(registry()),
            enabledProperties(false),
            runtimeState::availableExecutors,
            runtimeState::startupDurationMs);

    binder.bindTo(new SimpleMeterRegistry());

    assertFalse(accessed.get());
  }

  private static <T> ObjectProvider<T> provider(T instance) {
    return new ObjectProvider<>() {
      @Override
      public T getObject(Object... args) {
        return getObject();
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

  private static PolyglotProperties enabledProperties(boolean jsEnabled) {
    return new PolyglotProperties(
        null,
        new PolyglotProperties.PythonProperties(true, "classpath:/python", true, false, List.of()),
        new PolyglotProperties.JsProperties(jsEnabled, "classpath:/js", false, List.of()),
        null,
        null);
  }

  private static SimpleMeterRegistry registry() {
    return new SimpleMeterRegistry();
  }

  private static PolyglotRuntimeState runtimeState(long startupDurationMs, int availableExecutors) {
    PolyglotRuntimeState state = new PolyglotRuntimeState();
    PyExecutor availablePy =
        availableExecutors > 0 ? org.mockito.Mockito.mock(PyExecutor.class) : null;
    JsExecutor availableJs =
        availableExecutors > 1 ? org.mockito.Mockito.mock(JsExecutor.class) : null;
    state.recordStartup(availablePy, availableJs, startupDurationMs);
    return state;
  }

  private static <T> ObjectProvider<T> trackingProvider(T instance, AtomicBoolean accessed) {
    return new ObjectProvider<>() {
      @Override
      public T getObject(Object... args) {
        return getObject();
      }

      @Override
      public T getObject() {
        accessed.set(true);
        return instance;
      }

      @Override
      public Stream<T> stream() {
        accessed.set(true);
        return instance == null ? Stream.empty() : Stream.of(instance);
      }

      @Override
      public Stream<T> orderedStream() {
        accessed.set(true);
        return stream();
      }
    };
  }
}
