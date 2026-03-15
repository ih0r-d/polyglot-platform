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

import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.polyglot.annotations.spring.PolyglotExecutors;
import io.github.ih0rd.polyglot.annotations.spring.properties.PolyglotProperties;

class PolyglotInfoContributorTest {

  @Mock private PyExecutor pyExecutor;

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

    assertTrue(details.containsKey("python"));
    assertFalse(details.containsKey("js"));
    assertEquals(true, ((Map<?, ?>) details.get("python")).get("available"));
  }
}
