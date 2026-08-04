package io.github.ih0rd.polyglot.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScriptNamesTest {

  @Test
  void acceptsSimpleAndNestedLogicalNames() {
    assertEquals("demo", ScriptNames.requireValid("demo"));
    assertEquals("scripts/utils/helper", ScriptNames.requireValid("scripts/utils/helper"));
  }

  @Test
  void rejectsNullAndBlankNames() {
    assertInvalid(null, "<null>");
    assertInvalid("", "''");
    assertInvalid("  ", "'  '");
  }

  @Test
  void rejectsAbsoluteAndTraversalNames() {
    assertInvalid("/etc/passwd", "'/etc/passwd'");
    assertInvalid("../secrets", "'../secrets'");
    assertInvalid("scripts/../secrets", "'scripts/../secrets'");
    assertInvalid("scripts/./helper", "'scripts/./helper'");
  }

  @Test
  void rejectsWindowsAbsoluteUncAndMixedSeparators() {
    assertInvalid("C:/Windows/win", "'C:/Windows/win'");
    assertInvalid("C:\\Windows\\win", "'C:\\Windows\\win'");
    assertInvalid("\\\\server\\share\\script", "'\\\\server\\share\\script'");
    assertInvalid("scripts\\..\\secret", "'scripts\\..\\secret'");
  }

  @Test
  void rejectsResourcePrefixesAndEmptySegments() {
    assertInvalid("file:/tmp/script", "'file:/tmp/script'");
    assertInvalid("classpath:python/demo", "'classpath:python/demo'");
    assertInvalid("scripts//helper", "'scripts//helper'");
    assertInvalid("scripts/", "'scripts/'");
  }

  private static void assertInvalid(String scriptName, String expectedValue) {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> ScriptNames.requireValid(scriptName));

    assertTrue(exception.getMessage().contains("Invalid logical script name " + expectedValue));
  }
}
