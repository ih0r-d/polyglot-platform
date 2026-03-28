package io.github.ih0rd.polyglot.annotations.spring.actuator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.actuate.info.Info;

import io.github.ih0rd.adapter.context.JsExecutor;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

class PolyglotInfoContributorTest {

  @Mock private PyExecutor pyExecutor;
  @Mock private JsExecutor jsExecutor;

  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  void contributeAddsOnlyEnabledLanguageSections() {
    PolyglotProperties properties =
        new PolyglotProperties(
            null,
            new PolyglotProperties.PythonProperties(
                true, "classpath:/python", true, true, java.util.List.of("demo")),
            new PolyglotProperties.JsProperties(false, "classpath:/js", false, java.util.List.of()),
            null,
            null);

    PolyglotInfoContributor contributor =
        new PolyglotInfoContributor(new PolyglotExecutors(pyExecutor, null), properties);
    Info.Builder builder = new Info.Builder();

    contributor.contribute(builder);
    Map<String, Object> details = builder.build().getDetails();
    Map<?, ?> python = (Map<?, ?>) details.get("python");

    assertTrue(details.containsKey("python"));
    assertFalse(details.containsKey("js"));
    assertEquals(true, python.get("available"));
    assertEquals(true, python.get("safeDefaults"));
    assertEquals(java.util.List.of("demo"), python.get("preloadScripts"));
  }

  @Test
  void contributeAddsJsSectionWhenEnabled() {
    PolyglotProperties properties =
        new PolyglotProperties(
            null,
            new PolyglotProperties.PythonProperties(
                false, "classpath:/python", true, true, java.util.List.of()),
            new PolyglotProperties.JsProperties(
                true, "classpath:/js", true, java.util.List.of("client")),
            null,
            null);

    PolyglotInfoContributor contributor =
        new PolyglotInfoContributor(new PolyglotExecutors(null, jsExecutor), properties);
    Info.Builder builder = new Info.Builder();

    contributor.contribute(builder);
    Map<String, Object> details = builder.build().getDetails();
    Map<?, ?> js = (Map<?, ?>) details.get("js");

    assertFalse(details.containsKey("python"));
    assertEquals(true, js.get("enabled"));
    assertEquals(true, js.get("available"));
    assertEquals(java.util.List.of("client"), js.get("preloadScripts"));
  }
}
