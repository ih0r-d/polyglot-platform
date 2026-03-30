package io.github.ih0rd.adapter.utils;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Utility class for transforming strings between {@code snake_case} and {@code camelCase}.
 *
 * <p>Provides static helper methods to convert identifiers between common Java and Python naming
 * styles:
 *
 * <ul>
 *   <li>{@code snake_case -> camelCase}
 *   <li>{@code camelCase -> snake_case}
 * </ul>
 *
 * <p><strong>Example:</strong>
 *
 * <pre>{@code
 * String a = StringCaseConverter.camelToSnake("myTestString"); // "my_test_string"
 * String b = StringCaseConverter.snakeToCamel("example_name"); // "exampleName"
 * }</pre>
 */
public class StringCaseConverter {

  private StringCaseConverter() {}

  /**
   * Converts a string from {@code snake_case} to {@code camelCase}.
   *
   * <p><strong>Example:</strong>
   *
   * <pre>{@code
   * String result = StringCaseConverter.snakeToCamel("hello_world");
   * // result = "helloWorld"
   * }</pre>
   *
   * @param snakeCase input string written in {@code snake_case}
   * @return the converted string in {@code camelCase} form
   */
  public static String snakeToCamel(String snakeCase) {
    if (snakeCase == null || snakeCase.isEmpty()) {
      return snakeCase;
    }
    var parts = snakeCase.split("_");
    return parts[0]
        + Arrays.stream(parts, 1, parts.length)
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .collect(Collectors.joining());
  }

  /**
   * Converts a string from {@code camelCase} to {@code snake_case}.
   *
   * <p><strong>Example:</strong>
   *
   * <pre>{@code
   * String result = StringCaseConverter.camelToSnake("myTestString");
   * // result = "my_test_string"
   * }</pre>
   *
   * @param camelCase input string written in {@code camelCase}
   * @return the converted string in {@code snake_case} form
   */
  public static String camelToSnake(String camelCase) {
    if (camelCase == null || camelCase.isEmpty()) {
      return camelCase;
    }
    StringBuilder result = new StringBuilder();
    result.append(Character.toLowerCase(camelCase.charAt(0)));

    camelCase
        .substring(1)
        .chars()
        .mapToObj(
            c -> Character.isUpperCase(c) ? "_" + Character.toLowerCase((char) c) : "" + (char) c)
        .forEach(result::append);

    return result.toString();
  }
}
