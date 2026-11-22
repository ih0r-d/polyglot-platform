package io.github.ih0rd.polyglot.annotations.spring.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.MissingPolyglotClientAnnotationException;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.PolyglotClientBindingException;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.PolyglotClientClassNotFoundException;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.PolyglotClientResolutionException;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotInvocationMetrics;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotInvocationObserver;
import io.github.ih0rd.polyglot.annotations.spring.internal.PolyglotInvocationTracing;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PolyglotClientFactoryBeanTest {

  @PolyglotClient
  interface AutoApi {}

  @PolyglotClient(languages = SupportedLanguage.PYTHON)
  interface PythonApi {}

  @PolyglotClient(languages = SupportedLanguage.PYTHON)
  interface ObservedApi {
    String call() throws IOException;
  }

  @PolyglotClient(languages = SupportedLanguage.PYTHON, convention = Convention.BY_METHOD_NAME)
  interface MethodConventionApi {}

  @PolyglotClient(script = "contracts/greeting", convention = Convention.BY_METHOD_NAME)
  interface MappedApi {
    String greet();
  }

  @PolyglotClient(languages = {SupportedLanguage.PYTHON, SupportedLanguage.JS})
  interface MultiApi {}

  interface PlainApi {}

  @Mock private PyExecutor pyExecutor;
  @Mock private JsExecutor jsExecutor;
  @Mock private PythonApi pythonProxy;
  @Mock private MethodConventionApi methodConventionProxy;
  @Mock private AutoApi autoProxy;
  @Mock private BeanFactory beanFactory;
  @Mock private ObjectProvider<PolyglotInvocationObserver> invocationObserver;
  @Mock private ObjectProvider<PolyglotInvocationTracing> invocationTracing;

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
  void getObjectBindsUsingExplicitPythonExecutor() {
    when(pyExecutor.bind(PythonApi.class, Convention.DEFAULT)).thenReturn(pythonProxy);

    PolyglotClientFactoryBean<PythonApi> factory =
        factory(PythonApi.class, new PolyglotExecutors(pyExecutor, null));

    assertSame(pythonProxy, factory.getObject());
    verify(pyExecutor).validateBinding(PythonApi.class, Convention.DEFAULT);
  }

  @Test
  void getObjectAutoResolvesJsWhenOnlyJsExecutorIsPresent() {
    when(jsExecutor.bind(AutoApi.class, Convention.DEFAULT)).thenReturn(autoProxy);

    PolyglotClientFactoryBean<AutoApi> factory =
        factory(AutoApi.class, new PolyglotExecutors(null, jsExecutor));

    assertSame(autoProxy, factory.getObject());
    verify(jsExecutor).validateBinding(AutoApi.class, Convention.DEFAULT);
  }

  @Test
  void factoryUsesMappedPythonSourceForEagerValidationAndInvocation() {
    try (var executor =
        PyExecutor.createWithContext(
            Context.newBuilder("python").build(),
            new InMemoryScriptSource(
                Map.of("python/contracts/greeting", "def greet(): return 'hello'")))) {
      var client = factory(MappedApi.class, new PolyglotExecutors(executor, null)).getObject();
      assertEquals("hello", client.greet());
    }
  }

  @Test
  void observedClientsUseTheirOwnIdentity() {
    var registry = new SimpleMeterRegistry();
    try (var executor =
        PyExecutor.createWithContext(
            Context.newBuilder("python").build(),
            new InMemoryScriptSource(
                Map.of("python/contracts/greeting", "def greet(): return 'hello'")))) {
      var factory = factory(MappedApi.class, new PolyglotExecutors(executor, null));
      when(invocationObserver.getIfAvailable()).thenReturn(new PolyglotInvocationMetrics(registry));
      var client = factory.getObject();
      var other = factory.getObject();
      assertTrue(client.equals(client));
      assertFalse(client.equals(other));
      assertFalse(other.equals(client));
      assertFalse(client.equals(executor));
      assertEquals(System.identityHashCode(client), client.hashCode());
      assertEquals(executor.toString(), client.toString());
      assertEquals("hello", client.greet());
      assertEquals(
          1,
          registry
              .get("polyglot.client.invocations")
              .tags("language", "python", "outcome", "success")
              .timer()
              .count());
    } finally {
      registry.close();
    }
  }

  @Test
  void mappedClientCannotResolveToJavaScript() {
    var factory = factory(MappedApi.class, new PolyglotExecutors(null, jsExecutor));
    assertTrue(
        assertThrows(PolyglotClientResolutionException.class, factory::getObject)
            .getMessage()
            .contains("requires a Python executor"));
  }

  @Test
  void getObjectPropagatesConventionToRuntimeBindingLayer() {
    when(pyExecutor.bind(MethodConventionApi.class, Convention.BY_METHOD_NAME))
        .thenReturn(methodConventionProxy);

    PolyglotClientFactoryBean<MethodConventionApi> factory =
        factory(MethodConventionApi.class, new PolyglotExecutors(pyExecutor, null));

    assertSame(methodConventionProxy, factory.getObject());
    verify(pyExecutor).validateBinding(MethodConventionApi.class, Convention.BY_METHOD_NAME);
  }

  @Test
  void getObjectFailsWhenAnnotationIsMissing() {
    PolyglotClientFactoryBean<PlainApi> factory =
        factory(PlainApi.class, new PolyglotExecutors(null, null));

    assertThrows(MissingPolyglotClientAnnotationException.class, factory::getObject);
  }

  @Test
  void getObjectFailsWhenAutoResolutionIsAmbiguous() {
    PolyglotClientFactoryBean<AutoApi> factory =
        factory(AutoApi.class, new PolyglotExecutors(pyExecutor, jsExecutor));

    assertThrows(PolyglotClientResolutionException.class, factory::getObject);
  }

  @Test
  void getObjectFailsWhenMultipleLanguagesAreDeclared() {
    PolyglotClientFactoryBean<MultiApi> factory =
        factory(MultiApi.class, new PolyglotExecutors(null, null));

    assertThrows(PolyglotClientResolutionException.class, factory::getObject);
  }

  @Test
  void getObjectWrapsBindingFailures() {
    RuntimeException cause = new RuntimeException("boom");
    when(pyExecutor.bind(PythonApi.class, Convention.DEFAULT)).thenThrow(cause);

    PolyglotClientFactoryBean<PythonApi> factory =
        factory(PythonApi.class, new PolyglotExecutors(pyExecutor, null));

    PolyglotClientBindingException exception =
        assertThrows(PolyglotClientBindingException.class, factory::getObject);

    assertSame(cause, exception.getCause());
  }

  @Test
  void constructorFailsForUnknownClass() {
    assertThrows(
        PolyglotClientClassNotFoundException.class,
        () -> new PolyglotClientFactoryBean<>("missing.Type"));
  }

  @Test
  void getObjectTypeReturnsResolvedClientClass() {
    PolyglotClientFactoryBean<PythonApi> factory =
        factory(PythonApi.class, new PolyglotExecutors(pyExecutor, null));

    assertEquals(PythonApi.class, factory.getObjectType());
  }

  @Test
  void metricsPreserveNullCheckedExceptionsErrorsAndObjectMethods() throws IOException {
    var registry = new SimpleMeterRegistry();
    var failure = new AtomicReference<Throwable>();
    try {
      ObservedApi client = observedClient(registry, failure);
      assertNull(client.call());
      assertEquals("ObservedApi", client.toString());
      IOException checked = new IOException("expected checked failure");
      failure.set(checked);
      assertSame(checked, assertThrows(IOException.class, client::call));
      AssertionError error = new AssertionError("expected error");
      failure.set(error);
      assertSame(error, assertThrows(AssertionError.class, client::call));
      assertEquals(
          1,
          registry
              .get("polyglot.client.invocations")
              .tags("language", "python", "outcome", "success")
              .timer()
              .count());
      assertEquals(
          2,
          registry
              .get("polyglot.client.invocations")
              .tags("language", "python", "outcome", "failure")
              .timer()
              .count());
    } finally {
      registry.close();
    }
  }

  @Test
  void metricsPreserveInterruptStatusOnFailure() {
    var registry = new SimpleMeterRegistry();
    var failure = new AtomicReference<Throwable>(new InterruptedIOException("expected interrupt"));
    try {
      ObservedApi client = observedClient(registry, failure);
      assertSame(failure.get(), assertThrows(InterruptedIOException.class, client::call));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
      registry.close();
    }
  }

  private ObservedApi observedClient(
      SimpleMeterRegistry registry, AtomicReference<Throwable> failure) {
    ObservedApi target =
        (ObservedApi)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {ObservedApi.class},
                (proxy, method, args) -> {
                  if (method.getDeclaringClass() == Object.class) {
                    return "ObservedApi";
                  }
                  Throwable cause = failure.get();
                  if (cause instanceof InterruptedIOException) {
                    Thread.currentThread().interrupt();
                  }
                  if (cause != null) {
                    throw cause;
                  }
                  return null;
                });
    when(pyExecutor.bind(ObservedApi.class, Convention.DEFAULT)).thenReturn(target);
    var factory = factory(ObservedApi.class, new PolyglotExecutors(pyExecutor, null));
    when(invocationObserver.getIfAvailable()).thenReturn(new PolyglotInvocationMetrics(registry));
    return factory.getObject();
  }

  private <T> PolyglotClientFactoryBean<T> factory(Class<T> type, PolyglotExecutors executors) {
    when(beanFactory.getBean(PolyglotExecutors.class)).thenReturn(executors);
    when(beanFactory.getBeanProvider(PolyglotInvocationObserver.class))
        .thenReturn(invocationObserver);
    when(invocationObserver.getIfAvailable()).thenReturn(null);
    when(beanFactory.getBeanProvider(PolyglotInvocationTracing.class))
        .thenReturn(invocationTracing);
    PolyglotClientFactoryBean<T> factory = new PolyglotClientFactoryBean<>(type.getName());
    factory.setBeanFactory(beanFactory);
    return factory;
  }
}
