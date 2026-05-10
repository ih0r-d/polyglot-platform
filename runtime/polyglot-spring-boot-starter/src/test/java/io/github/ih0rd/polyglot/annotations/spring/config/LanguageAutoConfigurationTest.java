package io.github.ih0rd.polyglot.annotations.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.spring.context.SpringPolyglotContextFactory;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

class LanguageAutoConfigurationTest {

  @Test
  void pythonAutoConfigurationCreatesScriptSourceAndExecutor() {
    PolyglotPythonAutoConfiguration configuration = new PolyglotPythonAutoConfiguration();
    PolyglotProperties properties =
        new PolyglotProperties(
            null,
            new PolyglotProperties.PythonProperties(
                true, "classpath:python", true, true, java.util.List.of()),
            null,
            null,
            null);
    ScriptSource scriptSource =
        configuration.pyScriptSource(new DefaultResourceLoader(), properties);
    SpringPolyglotContextFactory contextFactory = mock(SpringPolyglotContextFactory.class);
    Context context = mock(Context.class);
    when(contextFactory.create(SupportedLanguage.PYTHON)).thenReturn(context);

    PyExecutor executor = configuration.pyExecutor(contextFactory, scriptSource);

    assertTrue(!scriptSource.exists(SupportedLanguage.PYTHON, "missing"));
    assertEquals("python", executor.metadata().get("languageId"));
  }

  @Test
  void jsAutoConfigurationCreatesScriptSourceAndExecutor() {
    PolyglotJsAutoConfiguration configuration = new PolyglotJsAutoConfiguration();
    PolyglotProperties properties =
        new PolyglotProperties(
            null,
            null,
            new PolyglotProperties.JsProperties(true, "classpath:js", false, java.util.List.of()),
            null,
            null);
    ScriptSource scriptSource =
        configuration.jsScriptSource(new DefaultResourceLoader(), properties);
    SpringPolyglotContextFactory contextFactory = mock(SpringPolyglotContextFactory.class);
    Context context = mock(Context.class);
    when(contextFactory.create(SupportedLanguage.JS)).thenReturn(context);

    JsExecutor executor = configuration.jsExecutor(contextFactory, scriptSource);

    assertTrue(!scriptSource.exists(SupportedLanguage.JS, "missing"));
    assertEquals("js", executor.metadata().get("languageId"));
  }

  @Test
  void pyExecutorBeanDeclaresDestroyMethodClose() throws NoSuchMethodException {
    Method method =
        PolyglotPythonAutoConfiguration.class.getDeclaredMethod(
            "pyExecutor", SpringPolyglotContextFactory.class, ScriptSource.class);
    Bean beanAnnotation = method.getAnnotation(Bean.class);
    assertNotNull(beanAnnotation);
    assertEquals("close", beanAnnotation.destroyMethod());
  }

  @Test
  void jsExecutorBeanDeclaresDestroyMethodClose() throws NoSuchMethodException {
    Method method =
        PolyglotJsAutoConfiguration.class.getDeclaredMethod(
            "jsExecutor", SpringPolyglotContextFactory.class, ScriptSource.class);
    Bean beanAnnotation = method.getAnnotation(Bean.class);
    assertNotNull(beanAnnotation);
    assertEquals("close", beanAnnotation.destroyMethod());
  }

  @Test
  void pyExecutorIsClosedOnContextShutdown() {
    Context mockContext = mock(Context.class);
    SpringPolyglotContextFactory factory = mock(SpringPolyglotContextFactory.class);
    when(factory.create(SupportedLanguage.PYTHON)).thenReturn(mockContext);

    AtomicReference<PyExecutor> executorRef = new AtomicReference<>();
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotAutoConfiguration.class, PolyglotPythonAutoConfiguration.class))
        .withPropertyValues(
            "polyglot.core.enabled=true",
            "polyglot.python.enabled=true",
            "polyglot.python.resources-path=classpath:python")
        .withBean(SpringPolyglotContextFactory.class, () -> factory)
        .run(ctx -> executorRef.set(ctx.getBean(PyExecutor.class)));

    assertThat(executorRef.get().metadata()).containsEntry("closed", true);
  }

  @Test
  void jsExecutorIsClosedOnContextShutdown() {
    Context mockContext = mock(Context.class);
    SpringPolyglotContextFactory factory = mock(SpringPolyglotContextFactory.class);
    when(factory.create(SupportedLanguage.JS)).thenReturn(mockContext);

    AtomicReference<JsExecutor> executorRef = new AtomicReference<>();
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                PolyglotAutoConfiguration.class, PolyglotJsAutoConfiguration.class))
        .withPropertyValues(
            "polyglot.core.enabled=true",
            "polyglot.js.enabled=true",
            "polyglot.js.resources-path=classpath:js")
        .withBean(SpringPolyglotContextFactory.class, () -> factory)
        .run(ctx -> executorRef.set(ctx.getBean(JsExecutor.class)));

    assertThat(executorRef.get().metadata()).containsEntry("closed", true);
  }
}
