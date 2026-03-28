package io.github.ih0rd.polyglot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SupportedLanguageTest {

  @Test
  void exposesLanguageIdAndExtension() {
    assertEquals("python", SupportedLanguage.PYTHON.id());
    assertEquals(".py", SupportedLanguage.PYTHON.ext());
    assertEquals("js", SupportedLanguage.JS.id());
    assertEquals(".js", SupportedLanguage.JS.ext());
  }

  @Test
  void resolvesLanguageFromFileName() {
    assertEquals(SupportedLanguage.PYTHON, SupportedLanguage.fromFileName("service.py"));
    assertEquals(SupportedLanguage.JS, SupportedLanguage.fromFileName("service.js"));
  }

  @Test
  void rejectsUnsupportedFileName() {
    assertThrows(
        IllegalArgumentException.class, () -> SupportedLanguage.fromFileName("service.rb"));
  }
}
