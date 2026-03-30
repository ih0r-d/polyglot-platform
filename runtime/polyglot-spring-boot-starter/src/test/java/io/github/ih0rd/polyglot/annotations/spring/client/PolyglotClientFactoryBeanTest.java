package io.github.ih0rd.polyglot.annotations.spring.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.BeanFactory;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;
import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.MissingPolyglotClientAnnotationException;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.PolyglotClientBindingException;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.PolyglotClientClassNotFoundException;
import io.github.ih0rd.polyglot.annotations.spring.client.exceptions.PolyglotClientResolutionException;

class PolyglotClientFactoryBeanTest {

  @PolyglotClient
  interface AutoApi {}

  @PolyglotClient(languages = SupportedLanguage.PYTHON)
  interface PythonApi {}

  @PolyglotClient(languages = SupportedLanguage.PYTHON, convention = Convention.BY_METHOD_NAME)
  interface MethodConventionApi {}

  @PolyglotClient(languages = {SupportedLanguage.PYTHON, SupportedLanguage.JS})
  interface MultiApi {}

  interface PlainApi {}

  @Mock private PyExecutor pyExecutor;
  @Mock private JsExecutor jsExecutor;
  @Mock private PythonApi pythonProxy;
  @Mock private MethodConventionApi methodConventionProxy;
  @Mock private AutoApi autoProxy;
  @Mock private BeanFactory beanFactory;

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

  private <T> PolyglotClientFactoryBean<T> factory(Class<T> type, PolyglotExecutors executors) {
    when(beanFactory.getBean(PolyglotExecutors.class)).thenReturn(executors);
    PolyglotClientFactoryBean<T> factory = new PolyglotClientFactoryBean<>(type.getName());
    factory.setBeanFactory(beanFactory);
    return factory;
  }
}
