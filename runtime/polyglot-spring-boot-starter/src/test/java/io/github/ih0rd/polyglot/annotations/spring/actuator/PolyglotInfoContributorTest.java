package io.github.ih0rd.polyglot.annotations.spring.actuator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
    when(pyExecutor.metadata())
        .thenReturn(
            Map.of(
                "executorType",
                PyExecutor.class.getName(),
                "languageId",
                "python",
                "sourceCacheSize",
                2,
                "instanceCacheSize",
                3));

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
    Map<?, ?> executors = (Map<?, ?>) details.get("executors");
    Map<?, ?> metadata = (Map<?, ?>) python.get("metadata");

    assertEquals(1, executors.get("configuredCount"));
    assertEquals(1, executors.get("availableCount"));
    assertTrue(details.containsKey("python"));
    assertFalse(details.containsKey("js"));
    assertEquals(true, python.get("available"));
    assertEquals(true, python.get("safeDefaults"));
    assertEquals(java.util.List.of("demo"), python.get("preloadScripts"));
    assertEquals(2, metadata.get("sourceCacheSize"));
    assertEquals(3, metadata.get("contractCacheSize"));
  }

  @Test
  void contributeAddsJsSectionWhenEnabled() {
    when(jsExecutor.metadata())
        .thenReturn(
            Map.of(
                "executorType",
                JsExecutor.class.getName(),
                "languageId",
                "js",
                "sourceCacheSize",
                1,
                "loadedInterfaces",
                java.util.List.of("client")));

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
    Map<?, ?> executors = (Map<?, ?>) details.get("executors");
    Map<?, ?> metadata = (Map<?, ?>) js.get("metadata");

    assertFalse(details.containsKey("python"));
    assertEquals(1, executors.get("configuredCount"));
    assertEquals(1, executors.get("availableCount"));
    assertEquals(true, js.get("enabled"));
    assertEquals(true, js.get("available"));
    assertEquals(java.util.List.of("client"), js.get("preloadScripts"));
    assertEquals(1, metadata.get("sourceCacheSize"));
    assertEquals(1, metadata.get("contractCacheSize"));
  }
}
