package io.github.ih0rd.polyglot.annotations;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;

class PolyglotClientAnnotationTest {

  @PolyglotClient
  interface DefaultClient {}

  @PolyglotClient(
      languages = {SupportedLanguage.PYTHON},
      convention = Convention.BY_METHOD_NAME)
  interface ExplicitClient {}

  @Test
  void exposesDefaultAnnotationValues() {
    PolyglotClient annotation = DefaultClient.class.getAnnotation(PolyglotClient.class);

    assertEquals(Convention.DEFAULT, annotation.convention());
    assertArrayEquals(new SupportedLanguage[0], annotation.languages());
  }

  @Test
  void exposesExplicitAnnotationValues() {
    PolyglotClient annotation = ExplicitClient.class.getAnnotation(PolyglotClient.class);

    assertEquals(Convention.BY_METHOD_NAME, annotation.convention());
    assertArrayEquals(new SupportedLanguage[] {SupportedLanguage.PYTHON}, annotation.languages());
  }

  @Test
  void keepsRuntimeTypeMetadata() {
    assertTrue(PolyglotClient.class.isAnnotationPresent(Documented.class));

    Target target = PolyglotClient.class.getAnnotation(Target.class);
    Retention retention = PolyglotClient.class.getAnnotation(Retention.class);

    assertArrayEquals(new ElementType[] {ElementType.TYPE}, target.value());
    assertEquals(RetentionPolicy.RUNTIME, retention.value());
  }
}
