package io.github.ih0rd.polyglot.annotations.spring.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PolyglotPropertiesTest {

  @Test
  void constructorAppliesNestedDefaults() {
    PolyglotProperties properties = new PolyglotProperties(null, null, null, null, null);

    assertTrue(properties.core().enabled());
    assertEquals("classpath:python", properties.python().resourcesPath());
    assertEquals("classpath:js", properties.js().resourcesPath());
    assertTrue(properties.metrics().enabled());
  }

  @Test
  void nestedPropertiesNormalizeBlankOrNullValues() {
    PolyglotProperties.CoreProperties core =
        new PolyglotProperties.CoreProperties(true, true, true, " ");
    PolyglotProperties.PythonProperties python =
        new PolyglotProperties.PythonProperties(true, "classpath:/python", true, false, null);
    PolyglotProperties.JsProperties js =
        new PolyglotProperties.JsProperties(true, "classpath:/js", false, null);

    assertEquals("debug", core.logLevel());
    assertEquals(List.of(), python.preloadScripts());
    assertEquals(List.of(), js.preloadScripts());
  }
}
