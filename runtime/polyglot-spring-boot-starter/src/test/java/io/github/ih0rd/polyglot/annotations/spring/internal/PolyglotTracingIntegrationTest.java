package io.github.ih0rd.polyglot.annotations.spring.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.client.PolyglotClientFactoryBean;
import io.github.ih0rd.polyglot.annotations.spring.config.PolyglotTracingAutoConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

class PolyglotTracingIntegrationTest {
  private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
  private final SdkTracerProvider provider =
      SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
  private final Tracer tracer = tracer(provider);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  private static Tracer tracer(SdkTracerProvider provider) {
    return new OtelTracer(
        provider.get("polyglot-test"), new OtelCurrentTraceContext(), event -> {});
  }

  @AfterEach
  void close() {
    provider.close();
    registry.close();
  }

  @PolyglotClient(languages = SupportedLanguage.PYTHON, convention = Convention.BY_METHOD_NAME)
  public interface TracedApi {
    int value();

    int fail();

    String callback();
  }

  @PolyglotClient(languages = SupportedLanguage.PYTHON)
  public interface DefaultApi {
    int value();
  }

  @Test
  void realPythonKeepsHttpParentMetricsAndCallbackCorrelationWithoutLeakingErrors() {
    Map<String, String> scripts =
        new ConcurrentHashMap<>(
            Map.of(
                "python/traced_api",
                """
        def value(): return 42
        def fail(): raise ValueError('sensitive-guest-data')
        def callback(): return host_callback()
        """));
    try (Context context = context();
        PyExecutor executor = executor(context, scripts)) {
      context
          .getBindings("python")
          .putMember(
              "host_callback",
              (ProxyExecutable) arguments -> tracer.currentSpan().context().spanId());
      TracedApi client = client(executor, TracedApi.class, tracer, true);
      assertThat(exporter.getFinishedSpanItems()).isEmpty();
      var http =
          provider
              .get("http-test")
              .spanBuilder("http.server.requests")
              .setSpanKind(SpanKind.SERVER)
              .startSpan();
      try (var scope = http.makeCurrent()) {
        assertThat(client.value()).isEqualTo(42);
        String callbackSpan = client.callback();
        assertThat(callbackSpan).isEqualTo(exporter.getFinishedSpanItems().getLast().getSpanId());
        assertThatThrownBy(client::fail).isInstanceOf(InvocationException.class);
        assertThat(tracer.currentSpan().context().spanId())
            .isEqualTo(http.getSpanContext().getSpanId());
      } finally {
        http.end();
      }
      assertThat(tracer.currentSpan()).isNull();
      var invocations =
          exporter.getFinishedSpanItems().stream()
              .filter(s -> s.getName().equals("polyglot.invoke"))
              .toList();
      assertThat(invocations)
          .hasSize(3)
          .allSatisfy(
              span -> {
                assertThat(span.getTraceId()).isEqualTo(http.getSpanContext().getTraceId());
                assertThat(span.getParentSpanId()).isEqualTo(http.getSpanContext().getSpanId());
                assertThat(span.getAttributes().get(AttributeKey.stringKey("polyglot.language")))
                    .isEqualTo("python");
                assertThat(span.toString())
                    .doesNotContain("sensitive-guest-data", "host_callback", "traced_api");
              });
      assertThat(invocations.getLast().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
      assertThat(invocations.getLast().getEvents()).hasSize(1);
      assertThat(
              registry
                  .get("polyglot.client.invocations")
                  .tags("language", "python", "outcome", "success")
                  .timer()
                  .count())
          .isEqualTo(2);
      assertThat(
              registry
                  .get("polyglot.client.invocations")
                  .tags("language", "python", "outcome", "failure")
                  .timer()
                  .count())
          .isEqualTo(1);
      assertThat(registry.get("polyglot.runtime.invocations.active").gauge().value()).isZero();
    }
  }

  @Test
  void defaultBindingAndObjectMethodsDoNotCreateDuplicateSpans() {
    try (Context context = context();
        PyExecutor executor =
            executor(
                context,
                Map.of("python/default_api", "class DefaultApi:\n def value(self): return 7\n"))) {
      DefaultApi client = client(executor, DefaultApi.class, tracer, false);
      assertThat(client.value()).isEqualTo(7);
      assertThat(client.toString()).isNotEmpty();
      assertThat(exporter.getFinishedSpanItems()).hasSize(1);
      assertThat(registry.find("polyglot.client.invocations").timers()).isEmpty();
    }
  }

  @Test
  void existingProxySurvivesReloadFailureAndShutdownWithoutLeakingScope() {
    Map<String, String> scripts =
        new ConcurrentHashMap<>(
            Map.of(
                "python/traced_api",
                "def value(): return 1\ndef fail(): return 0\ndef callback(): return ''\n"));
    try (Context context = context();
        PyExecutor executor = executor(context, scripts)) {
      TracedApi client = client(executor, TracedApi.class, tracer, false);
      assertThat(client.value()).isEqualTo(1);
      scripts.put(
          "python/traced_api",
          "def value(): return 2\ndef fail(): return 0\ndef callback(): return ''\n");
      executor.reloadContract(TracedApi.class, Convention.BY_METHOD_NAME);
      assertThat(client.value()).isEqualTo(2);
      scripts.put("python/traced_api", "def broken(:\n");
      assertThatThrownBy(() -> executor.reloadContract(TracedApi.class, Convention.BY_METHOD_NAME))
          .isInstanceOf(PolyglotException.class);
      assertThat(client.value()).isEqualTo(2);
      executor.close();
      assertThatThrownBy(client::value).isInstanceOf(IllegalStateException.class);
      assertThat(exporter.getFinishedSpanItems()).hasSize(4);
      assertThat(tracer.currentSpan()).isNull();
    }
  }

  @Test
  void concurrentCallsKeepTheirOwnParentsAndAllScopesClose() throws Exception {
    try (Context context = context();
        PyExecutor executor =
            executor(
                context,
                Map.of("python/default_api", "class DefaultApi:\n def value(self): return 7\n"));
        var threads = Executors.newFixedThreadPool(2)) {
      DefaultApi client = client(executor, DefaultApi.class, tracer, true);
      var tasks =
          IntStream.range(0, 8)
              .mapToObj(
                  i ->
                      threads.submit(
                          () -> {
                            Span parent = tracer.nextSpan().name("request").start();
                            try (var scope = tracer.withSpan(parent)) {
                              assertThat(client.value()).isEqualTo(7);
                              assertThat(tracer.currentSpan().context().spanId())
                                  .isEqualTo(parent.context().spanId());
                            } finally {
                              parent.end();
                            }
                            assertThat(tracer.currentSpan()).isNull();
                          }))
              .toList();
      for (var task : tasks) task.get(10, TimeUnit.SECONDS);
      var spans = exporter.getFinishedSpanItems();
      assertThat(spans).hasSize(16);
      assertThat(spans.stream().filter(s -> s.getName().equals("polyglot.invoke")))
          .allSatisfy(
              span ->
                  assertThat(
                          spans.stream()
                              .filter(s -> s.getName().equals("request"))
                              .map(s -> s.getSpanId()))
                      .contains(span.getParentSpanId()));
      assertThat(
              registry
                  .get("polyglot.client.invocations")
                  .tags("language", "python", "outcome", "success")
                  .timer()
                  .count())
          .isEqualTo(8);
    }
  }

  @Test
  void samplerSuppressesExportsWithoutSuppressingPythonExecution() {
    try (var unsampled =
            SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOff())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Context context = context();
        PyExecutor executor =
            executor(
                context,
                Map.of("python/default_api", "class DefaultApi:\n def value(self): return 7\n"))) {
      assertThat(client(executor, DefaultApi.class, tracer(unsampled), false).value()).isEqualTo(7);
      assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }
  }

  @Test
  void missingTracingOrTracerAndDisabledConfigurationBackOff() {
    runner().run(c -> assertThat(c).doesNotHaveBean(PolyglotInvocationTracing.class));
    runner()
        .withClassLoader(new FilteredClassLoader("io.micrometer.tracing"))
        .run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(PolyglotInvocationTracing.class));
    for (String property :
        new String[] {"management.tracing.enabled=false", "polyglot.core.enabled=false"}) {
      runner()
          .withBean(Tracer.class, () -> tracer)
          .withPropertyValues(property)
          .run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(PolyglotInvocationTracing.class));
    }
  }

  @Test
  void applicationContextsGetSeparateTracingBindings() {
    runner()
        .withBean(Tracer.class, () -> tracer)
        .run(
            first ->
                runner()
                    .withBean(Tracer.class, () -> tracer)
                    .run(
                        second -> {
                          assertThat(first.getBean(PolyglotInvocationTracing.class))
                              .isNotSameAs(second.getBean(PolyglotInvocationTracing.class));
                          assertThat(first).doesNotHaveBean(PyExecutor.class);
                        }));
  }

  @Test
  void tracerFailuresDoNotChangeTheCallOrOriginalException() throws Throwable {
    Tracer broken = mock(Tracer.class);
    when(broken.nextSpan()).thenThrow(new IllegalStateException("provider failure"));
    var failure = new InvocationException("application failure");
    var handler =
        new PolyglotInvocationTracer(broken)
            .decorate(
                (p, m, a) -> {
                  throw failure;
                },
                SupportedLanguage.PYTHON);
    assertThatThrownBy(() -> handler.invoke(null, DefaultApi.class.getMethod("value"), null))
        .isSameAs(failure);
    Span span = mock(Span.class, Answers.RETURNS_SELF);
    doReturn(span).when(broken).nextSpan();
    doThrow(new IllegalStateException("scope failure")).when(broken).withSpan(span);
    var scopeHandler =
        new PolyglotInvocationTracer(broken).decorate((p, m, a) -> 7, SupportedLanguage.PYTHON);
    assertThat(scopeHandler.invoke(null, DefaultApi.class.getMethod("value"), null)).isEqualTo(7);
    verify(span).end();
  }

  @Test
  void interruptedGuestCallRestoresInterruptAndClosesTracingScope() {
    try (Context context = context();
        PyExecutor executor =
            executor(
                context,
                Map.of(
                    "python/default_api",
                    "class DefaultApi:\n def value(self): return interrupt_host.interrupt()\n"))) {
      context.getBindings("python").putMember("interrupt_host", new InterruptHost());
      DefaultApi client = client(executor, DefaultApi.class, tracer, false);
      try {
        assertThatThrownBy(client::value).isInstanceOf(InvocationException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(tracer.currentSpan()).isNull();
        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
      } finally {
        Thread.interrupted();
      }
      executor.close();
      assertThat(executor.isClosed()).isTrue();
    }
  }

  public static final class InterruptHost {
    public int interrupt() throws InterruptedException {
      throw new InterruptedException("sensitive-interrupt-data");
    }
  }

  @Test
  void completionFailuresAreIsolatedAndSpanStillEnds() throws Throwable {
    Tracer selected = mock(Tracer.class);
    Span span = mock(Span.class, Answers.RETURNS_SELF);
    Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
    when(selected.nextSpan()).thenReturn(span);
    when(selected.withSpan(span)).thenReturn(scope);
    doThrow(new IllegalStateException("scope close failure")).when(scope).close();
    doThrow(new IllegalStateException("tag failure")).when(span).tag("polyglot.outcome", "success");
    doThrow(new IllegalStateException("end failure")).when(span).end();
    var handler =
        new PolyglotInvocationTracer(selected).decorate((p, m, a) -> 7, SupportedLanguage.PYTHON);
    assertThat(handler.invoke(null, DefaultApi.class.getMethod("value"), null)).isEqualTo(7);
    verify(span).end();
  }

  @Test
  void attributeFailureDuringStartIsIsolated() throws Throwable {
    Tracer selected = mock(Tracer.class);
    Span span = mock(Span.class, Answers.RETURNS_SELF);
    when(selected.nextSpan()).thenReturn(span);
    doThrow(new IllegalStateException("start failure")).when(span).name("polyglot.invoke");
    var handler =
        new PolyglotInvocationTracer(selected).decorate((p, m, a) -> 7, SupportedLanguage.PYTHON);
    assertThat(handler.invoke(null, DefaultApi.class.getMethod("value"), null)).isEqualTo(7);
    verify(span).end();
  }

  private ApplicationContextRunner runner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PolyglotTracingAutoConfiguration.class));
  }

  private Context context() {
    return PolyglotHelper.newContext(SupportedLanguage.PYTHON);
  }

  private PyExecutor executor(Context context, Map<String, String> scripts) {
    PyExecutor executor = new PyExecutor(context, new InMemoryScriptSource(scripts));
    executor.attachRuntimeObserver(
        () -> new PolyglotRuntimeMetrics(registry, SupportedLanguage.PYTHON));
    return executor;
  }

  private <T> T client(
      PyExecutor executor, Class<T> contract, Tracer selectedTracer, boolean metrics) {
    var beans = new DefaultListableBeanFactory();
    beans.registerSingleton("executors", new PolyglotExecutors(executor, null));
    beans.registerSingleton("tracing", new PolyglotInvocationTracer(selectedTracer));
    if (metrics) beans.registerSingleton("metrics", new PolyglotInvocationMetrics(registry));
    var factory = new PolyglotClientFactoryBean<T>(contract.getName());
    factory.setBeanFactory(beans);
    return factory.getObject();
  }
}
