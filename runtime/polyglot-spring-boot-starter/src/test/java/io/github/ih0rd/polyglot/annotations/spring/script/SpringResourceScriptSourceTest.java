package io.github.ih0rd.polyglot.annotations.spring.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import io.github.ih0rd.polyglot.SupportedLanguage;

class SpringResourceScriptSourceTest {

  @Mock private ResourceLoader resourceLoader;
  @Mock private Resource resource;

  private AutoCloseable mocks;
  private SpringResourceScriptSource source;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    source =
        new SpringResourceScriptSource(
            resourceLoader, SupportedLanguage.PYTHON, "classpath:/python");
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  void existsRequiresMatchingLanguageAndExistingResource() {
    when(resourceLoader.getResource("classpath:/python/demo.py")).thenReturn(resource);
    when(resource.exists()).thenReturn(true);

    assertTrue(source.exists(SupportedLanguage.PYTHON, "demo"));
    assertFalse(source.exists(SupportedLanguage.JS, "demo"));
  }

  @Test
  void openNormalizesBasePathAndReadsUtf8Content() throws Exception {
    source = new SpringResourceScriptSource(resourceLoader, SupportedLanguage.JS, "classpath:/js");
    when(resourceLoader.getResource("classpath:/js/demo.js")).thenReturn(resource);
    when(resource.getInputStream())
        .thenReturn(new ByteArrayInputStream("console.log('ok')".getBytes(StandardCharsets.UTF_8)));

    try (Reader reader = source.open(SupportedLanguage.JS, "demo")) {
      StringWriter writer = new StringWriter();
      reader.transferTo(writer);
      assertEquals("console.log('ok')", writer.toString());
    }
  }

  @Test
  void openFailsWhenLanguageDoesNotMatchBoundSource() {
    SpringResourceScriptSource scriptSource =
        new SpringResourceScriptSource(
            resourceLoader, SupportedLanguage.PYTHON, "classpath:/python/");

    assertThrows(
        IllegalArgumentException.class, () -> scriptSource.open(SupportedLanguage.JS, "demo"));
  }

  @Test
  void constructorRejectsBlankBasePath() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SpringResourceScriptSource(resourceLoader, SupportedLanguage.PYTHON, "  "));
  }
}
