package io.github.ih0rd.polyglot.annotations.spring.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class PolyglotPropertiesTest {

  @Test
  void constructorAppliesNestedDefaults() {
    PolyglotProperties properties = new PolyglotProperties(null, null, null, null, null);

    assertTrue(properties.core().enabled());
    assertEquals("classpath:python", properties.python().resourcesPath());
    assertEquals("classpath:js", properties.js().resourcesPath());
    assertTrue(properties.actuator().enabled());
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

  @Test
  void partialCoreBindingPreservesUnspecifiedDefaults() {
    PolyglotProperties properties = bind(Map.of("polyglot.core.fail-fast", "false"));

    assertTrue(properties.core().enabled());
    assertTrue(!properties.core().failFast());
    assertTrue(properties.core().logMetadataOnStartup());
    assertEquals("DEBUG", properties.core().logLevel());
  }

  @Test
  void partialPythonBindingPreservesUnspecifiedDefaults() {
    PolyglotProperties properties = bind(Map.of("polyglot.python.enabled", "true"));

    assertTrue(properties.python().enabled());
    assertEquals("classpath:python", properties.python().resourcesPath());
    assertTrue(properties.python().safeDefaults());
    assertTrue(!properties.python().warmupOnStartup());
    assertEquals(List.of(), properties.python().preloadScripts());
  }

  @Test
  void partialActuatorBindingPreservesUnspecifiedDefaults() {
    PolyglotProperties properties = bind(Map.of("polyglot.actuator.info.enabled", "false"));

    assertTrue(properties.actuator().enabled());
    assertTrue(!properties.actuator().info().enabled());
    assertTrue(properties.actuator().health().enabled());
  }

  @Test
  void invalidStartupLogLevelIsRejectedWithPropertyName() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> bind(Map.of("polyglot.core.log-level", "verbose")))
        .hasStackTraceContaining("polyglot.core.log-level")
        .hasStackTraceContaining("TRACE, DEBUG, INFO, WARN, ERROR");
  }

  private static PolyglotProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bind("polyglot", Bindable.of(PolyglotProperties.class))
        .orElseThrow(() -> new AssertionError("Polyglot properties were not bound"));
  }
}
