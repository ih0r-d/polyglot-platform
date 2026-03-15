package io.github.ih0rd.adapter.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StringCaseConverterTest {

  @Test
  void snakeToCamel_basicAndEdgeCases() {
    assertNull(StringCaseConverter.snakeToCamel(null));
    assertEquals("", StringCaseConverter.snakeToCamel(""));
    assertEquals("a", StringCaseConverter.snakeToCamel("a"));
    assertEquals("helloWorld", StringCaseConverter.snakeToCamel("hello_world"));
    assertEquals("exampleName", StringCaseConverter.snakeToCamel("example_name"));
    assertEquals("alreadyCamel", StringCaseConverter.snakeToCamel("alreadyCamel"));
  }

  @Test
  void camelToSnake_basicAndEdgeCases() {
    assertNull(StringCaseConverter.camelToSnake(null));
    assertEquals("", StringCaseConverter.camelToSnake(""));
    assertEquals("a", StringCaseConverter.camelToSnake("A"));
    assertEquals("my_test_string", StringCaseConverter.camelToSnake("myTestString"));
    assertEquals("example_name", StringCaseConverter.camelToSnake("exampleName"));
    assertEquals("already_snake", StringCaseConverter.camelToSnake("already_snake"));
  }
}
