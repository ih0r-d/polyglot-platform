package io.github.ih0rd.adapter.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.io.StringWriter;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.polyglot.SupportedLanguage;

class InMemoryScriptSourceTest {

  @Test
  void existsUsesLanguageAndScriptKey() {
    InMemoryScriptSource source =
        new InMemoryScriptSource(Map.of("python/demo", "print('ok')", "js/app", "1 + 1"));

    assertTrue(source.exists(SupportedLanguage.PYTHON, "demo"));
    assertFalse(source.exists(SupportedLanguage.PYTHON, "app"));
  }

  @Test
  void openReturnsReaderForStoredScript() throws Exception {
    InMemoryScriptSource source = new InMemoryScriptSource(Map.of("js/app", "console.log('ok')"));

    try (Reader reader = source.open(SupportedLanguage.JS, "app")) {
      StringWriter writer = new StringWriter();
      reader.transferTo(writer);
      assertEquals("console.log('ok')", writer.toString());
    }
  }

  @Test
  void openThrowsForMissingScript() {
    InMemoryScriptSource source = new InMemoryScriptSource(Map.of());

    assertThrows(IllegalArgumentException.class, () -> source.open(SupportedLanguage.PYTHON, "x"));
  }
}
