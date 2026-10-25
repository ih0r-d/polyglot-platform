package io.github.ih0rd.polyglot.annotations.spring.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.jayway.jsonpath.JsonPath;

class PolyglotConfigurationMetadataTest {

  @Test
  void generatedMetadataMatchesPropertyTypesAndRuntimeDefaults() throws IOException {
    Map<String, Object> expected = defaults();
    List<Map<?, ?>> properties = entries(metadata(), "properties");

    assertThat(properties)
        .extracting(property -> (String) property.get("name"))
        .containsExactlyInAnyOrderElementsOf(expected.keySet());
    for (Map<?, ?> property : properties) {
      String name = (String) property.get("name");
      Object defaultValue = expected.get(name);
      String type =
          defaultValue instanceof List<?>
              ? "java.util.List<java.lang.String>"
              : defaultValue.getClass().getName();
      assertThat(property.get("type")).as("%s type", name).isEqualTo(type);
      assertThat(property.get("defaultValue")).as("%s default", name).isEqualTo(defaultValue);
      assertThat((String) property.get("description"))
          .as("%s description", name)
          .isNotBlank()
          .endsWith(".")
          .doesNotContain("{@", "<p>");
      if (name.startsWith("polyglot.js.")) {
        assertThat((String) property.get("description")).containsIgnoringCase("experimental");
      }
    }
  }

  @Test
  void logLevelHintsMatchTheSupportedBindingValues() throws IOException {
    List<Map<?, ?>> hints = entries(metadata(), "hints");
    assertThat(hints).hasSize(1);
    assertThat(hints.getFirst().get("name")).isEqualTo("polyglot.core.log-level");
    List<Map<?, ?>> values = entries(hints.getFirst(), "values");
    assertThat(values)
        .extracting(value -> (String) value.get("value"))
        .containsExactly("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
    for (Map<?, ?> value : values) {
      String level = (String) value.get("value");
      PolyglotProperties properties =
          new Binder(new MapConfigurationPropertySource(Map.of("polyglot.core.log-level", level)))
              .bind("polyglot", Bindable.of(PolyglotProperties.class))
              .orElseThrow(() -> new AssertionError("Hinted log level was not bound"));
      assertThat(properties.core().logLevel()).isEqualTo(level);
    }
  }

  private static Map<String, Object> metadata() throws IOException {
    try (InputStream stream =
        PolyglotProperties.class.getResourceAsStream(
            "/META-INF/spring-configuration-metadata.json")) {
      assertThat(stream).as("Generated starter configuration metadata").isNotNull();
      return JsonPath.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).read("$");
    }
  }

  private static List<Map<?, ?>> entries(Map<?, ?> object, String key) {
    assertThat(object.get(key)).as(key).isInstanceOf(List.class);
    return ((List<?>) object.get(key)).stream().<Map<?, ?>>map(item -> (Map<?, ?>) item).toList();
  }

  private static Map<String, Object> defaults() {
    PolyglotProperties p = new PolyglotProperties(null, null, null, null, null);
    return Map.ofEntries(
        Map.entry("polyglot.core.enabled", p.core().enabled()),
        Map.entry("polyglot.core.fail-fast", p.core().failFast()),
        Map.entry("polyglot.core.log-metadata-on-startup", p.core().logMetadataOnStartup()),
        Map.entry("polyglot.core.log-level", p.core().logLevel()),
        Map.entry("polyglot.python.enabled", p.python().enabled()),
        Map.entry("polyglot.python.resources-path", p.python().resourcesPath()),
        Map.entry("polyglot.python.safe-defaults", p.python().safeDefaults()),
        Map.entry("polyglot.python.warmup-on-startup", p.python().warmupOnStartup()),
        Map.entry("polyglot.python.preload-scripts", p.python().preloadScripts()),
        Map.entry("polyglot.js.enabled", p.js().enabled()),
        Map.entry("polyglot.js.resources-path", p.js().resourcesPath()),
        Map.entry("polyglot.js.warmup-on-startup", p.js().warmupOnStartup()),
        Map.entry("polyglot.js.preload-scripts", p.js().preloadScripts()),
        Map.entry("polyglot.actuator.enabled", p.actuator().enabled()),
        Map.entry("polyglot.actuator.info.enabled", p.actuator().info().enabled()),
        Map.entry("polyglot.actuator.health.enabled", p.actuator().health().enabled()),
        Map.entry("polyglot.metrics.enabled", p.metrics().enabled()));
  }
}
