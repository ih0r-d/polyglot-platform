package io.github.ih0rd.polyglot.annotations.spring.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.client.PolyglotClientFactoryBean;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

class PolyglotStartupLifecycleTest {

  @Mock private ConfigurableListableBeanFactory beanFactory;
  @Mock private PyExecutor pyExecutor;
  @Mock private JsExecutor jsExecutor;
  @Mock private BeanDefinition clientBeanDefinition;

  private PolyglotRuntimeState runtimeState;
  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    runtimeState = new PolyglotRuntimeState();
    org.mockito.Mockito.when(beanFactory.getBeanDefinitionNames()).thenReturn(new String[0]);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  void startWarmsUpEnabledExecutorsAndMarksLifecycleRunning() {
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(
            enabledProperties(true, true, true), beanFactory, runtimeState, pyExecutor, jsExecutor);

    lifecycle.start();

    verify(pyExecutor).evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    verify(pyExecutor).preloadScript("demo");
    verify(jsExecutor).evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    verify(beanFactory).getBeanDefinitionNames();
    assertEquals(Integer.MAX_VALUE, lifecycle.getPhase());
    assertEquals(true, lifecycle.isRunning());
    assertEquals(2, runtimeState.availableExecutors());
  }

  @Test
  void startSkipsWarmupWhenCoreIsDisabled() {
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(
            enabledProperties(false, true, false), beanFactory, runtimeState, pyExecutor, null);

    lifecycle.start();

    verify(pyExecutor, never()).evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    verifyNoInteractions(beanFactory);
    assertFalse(lifecycle.isRunning());
  }

  @Test
  void startWrapsWarmupFailureWhenFailFastIsEnabled() {
    org.mockito.Mockito.doThrow(new RuntimeException("boom"))
        .when(pyExecutor)
        .evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(
            enabledProperties(true, true, false), beanFactory, runtimeState, pyExecutor, null);

    assertThrows(IllegalStateException.class, lifecycle::start);
  }

  @Test
  void startSuppressesWarmupFailureWhenFailFastIsDisabled() {
    org.mockito.Mockito.doThrow(new RuntimeException("boom"))
        .when(pyExecutor)
        .evaluate(PolyglotWarmupConstants.NOOP_EXPRESSION);
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(
            new PolyglotProperties(
                new PolyglotProperties.CoreProperties(true, false, true, "info"),
                new PolyglotProperties.PythonProperties(
                    true, "classpath:/python", true, true, java.util.List.of()),
                null,
                null,
                null),
            beanFactory,
            runtimeState,
            pyExecutor,
            null);

    assertDoesNotThrow(lifecycle::start);
    assertFalse(lifecycle.isRunning());
  }

  @Test
  void startEagerlyValidatesRegisteredPolyglotClientsWhenFailFastIsEnabled() {
    org.mockito.Mockito.when(beanFactory.getBeanDefinitionNames())
        .thenReturn(new String[] {"demo"});
    org.mockito.Mockito.when(beanFactory.getBeanDefinition("demo"))
        .thenReturn(clientBeanDefinition);
    org.mockito.Mockito.when(clientBeanDefinition.getBeanClassName())
        .thenReturn(PolyglotClientFactoryBean.class.getName());

    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(
            enabledProperties(true, false, false),
            beanFactory,
            runtimeState,
            pyExecutor,
            jsExecutor);

    lifecycle.start();

    verify(beanFactory).getBean("demo");
  }

  @Test
  void startDoesNotEagerlyValidateClientsWhenFailFastIsDisabled() {
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(
            new PolyglotProperties(
                new PolyglotProperties.CoreProperties(true, false, false, "info"),
                new PolyglotProperties.PythonProperties(
                    true, "classpath:/python", true, false, java.util.List.of("demo")),
                new PolyglotProperties.JsProperties(
                    true, "classpath:/js", false, java.util.List.of("bootstrap")),
                null,
                null),
            beanFactory,
            runtimeState,
            pyExecutor,
            jsExecutor);

    lifecycle.start();

    verify(beanFactory, never()).getBean(anyString());
    verifyNoInteractions(beanFactory);
  }

  @Test
  void startSupportsTraceConfiguredLogging() {
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(
            new PolyglotProperties(
                new PolyglotProperties.CoreProperties(true, true, true, "trace"),
                new PolyglotProperties.PythonProperties(
                    true, "classpath:/python", true, false, java.util.List.of()),
                new PolyglotProperties.JsProperties(
                    true, "classpath:/js", false, java.util.List.of()),
                null,
                null),
            beanFactory,
            runtimeState,
            pyExecutor,
            jsExecutor);

    assertDoesNotThrow(lifecycle::start);
  }

  @Test
  void startSupportsWarnConfiguredLogging() {
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(
            new PolyglotProperties(
                new PolyglotProperties.CoreProperties(true, true, true, "warn"),
                new PolyglotProperties.PythonProperties(
                    true, "classpath:/python", true, false, java.util.List.of()),
                null,
                null,
                null),
            beanFactory,
            runtimeState,
            pyExecutor,
            null);

    assertDoesNotThrow(lifecycle::start);
  }

  @Test
  void startSupportsErrorConfiguredLogging() {
    PolyglotStartupLifecycle lifecycle =
        new PolyglotStartupLifecycle(
            new PolyglotProperties(
                new PolyglotProperties.CoreProperties(true, true, true, "error"),
                new PolyglotProperties.PythonProperties(
                    true, "classpath:/python", true, false, java.util.List.of()),
                null,
                null,
                null),
            beanFactory,
            runtimeState,
            pyExecutor,
            null);

    assertDoesNotThrow(lifecycle::start);
  }

  private static PolyglotProperties enabledProperties(
      boolean coreEnabled, boolean pyWarmup, boolean jsWarmup) {
    return new PolyglotProperties(
        new PolyglotProperties.CoreProperties(coreEnabled, true, true, "info"),
        new PolyglotProperties.PythonProperties(
            true, "classpath:/python", true, pyWarmup, java.util.List.of("demo")),
        new PolyglotProperties.JsProperties(true, "classpath:/js", jsWarmup, java.util.List.of()),
        null,
        null);
  }
}
