package io.github.ih0rd.adapter.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.github.ih0rd.polyglot.SupportedLanguage;

class ClasspathScriptSourceTest {

  @Mock private ClassLoader classLoader;

  private AutoCloseable mocks;
  private ClasspathScriptSource source;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    source = new ClasspathScriptSource(classLoader);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  void existsReturnsTrueWhenResourceIsPresent() throws Exception {
    when(classLoader.getResource("python/demo.py")).thenReturn(new URL("file:/tmp/demo.py"));

    assertTrue(source.exists(SupportedLanguage.PYTHON, "demo"));
  }

  @Test
  void existsReturnsFalseWhenResourceIsMissing() {
    when(classLoader.getResource("js/missing.js")).thenReturn(null);

    assertFalse(source.exists(SupportedLanguage.JS, "missing"));
  }

  @Test
  void defaultConstructorUsesThreadContextClassLoader() throws Exception {
    ClassLoader previous = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      when(classLoader.getResource("python/default_demo.py"))
          .thenReturn(new URL("file:/tmp/default_demo.py"));

      ClasspathScriptSource defaultSource = new ClasspathScriptSource();

      assertTrue(defaultSource.exists(SupportedLanguage.PYTHON, "default_demo"));
    } finally {
      Thread.currentThread().setContextClassLoader(previous);
    }
  }

  @Test
  void openReturnsUtf8ReaderForResolvedResource() throws Exception {
    when(classLoader.getResourceAsStream("python/demo.py"))
        .thenReturn(new ByteArrayInputStream("print('ok')".getBytes(StandardCharsets.UTF_8)));

    try (Reader reader = source.open(SupportedLanguage.PYTHON, "demo")) {
      StringWriter writer = new StringWriter();
      reader.transferTo(writer);
      assertEquals("print('ok')", writer.toString());
    }
  }

  @Test
  void openThrowsWhenResolvedResourceStreamIsMissing() {
    when(classLoader.getResourceAsStream("python/missing.py")).thenReturn(null);

    assertThrows(
        IllegalArgumentException.class, () -> source.open(SupportedLanguage.PYTHON, "missing"));
  }
}
