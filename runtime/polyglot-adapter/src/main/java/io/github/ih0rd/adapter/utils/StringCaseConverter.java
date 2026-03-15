package io.github.ih0rd.adapter.utils;

import java.util.Arrays;
import java.util.stream.Collectors;

/// # StringCaseConverter
/// Utility class for transforming strings between `snake_case` and `camelCase`.
///
/// ---
/// ### Description
/// Provides static helper methods to convert identifiers between common Java and Python naming
// styles:
/// - `snake_case` → `camelCase`
/// - `camelCase` → `snake_case`
///
/// ---
/// ### Example
/// ```java
/// String a = StringCaseConverter.camelToSnake("myTestString"); // "my_test_string"
/// String b = StringCaseConverter.snakeToCamel("example_name"); // "exampleName"
/// ```
public class StringCaseConverter {

  private StringCaseConverter() {}

  /// ### snakeToCamel
  /// Converts a string from `snake_case` to `camelCase`.
  ///
  /// ---
  /// #### Parameters
  /// - `snakeCase` — input string written in `snake_case`.
  ///
  /// ---
  /// #### Returns
  /// The converted string in `camelCase` form.
  ///
  /// ---
  /// #### Example
  /// ```java
  /// String result = StringCaseConverter.snakeToCamel("hello_world");
  /// // result = "helloWorld"
  /// ```
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

  /// ### camelToSnake
  /// Converts a string from `camelCase` to `snake_case`.
  ///
  /// ---
  /// #### Parameters
  /// - `camelCase` — input string written in `camelCase`.
  ///
  /// ---
  /// #### Returns
  /// The converted string in `snake_case` form.
  ///
  /// ---
  /// #### Example
  /// ```java
  /// String result = StringCaseConverter.camelToSnake("myTestString");
  /// // result = "my_test_string"
  /// ```
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
