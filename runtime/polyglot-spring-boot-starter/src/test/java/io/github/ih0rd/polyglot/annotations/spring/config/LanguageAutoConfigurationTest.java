package io.github.ih0rd.polyglot.annotations.spring.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
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
}
