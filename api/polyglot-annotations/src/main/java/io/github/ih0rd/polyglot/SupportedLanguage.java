package io.github.ih0rd.polyglot;

import java.util.Arrays;

/**
 * Supported guest languages for both runtime execution and build-time tooling.
 *
 * <p>Each enum value exposes:
 *
 * <ul>
 *   <li>the GraalVM language id used when creating a {@code Context}
 *   <li>the default script file extension used by the repository conventions
 * </ul>
 */
public enum SupportedLanguage {
  /** Python guest-language support. */
  PYTHON("python", ".py"),
  /** JavaScript guest-language support. */
  JS("js", ".js");

  private final String id;
  private final String ext;

  SupportedLanguage(String id, String ext) {
    this.id = id;
    this.ext = ext;
  }

  /**
   * Returns the GraalVM language id, for example {@code python} or {@code js}.
   *
   * @return GraalVM language identifier
   */
  public String id() {
    return id;
  }

  /**
   * Returns the default file extension for scripts in this language.
   *
   * @return default script file extension including the leading dot
   */
  public String ext() {
    return ext;
  }

  /**
   * Infers the supported language from a script file name.
   *
   * @param fileName script file name
   * @return matching supported language
   * @throws IllegalArgumentException if the file extension is not supported
   */
  public static SupportedLanguage fromFileName(String fileName) {
    return Arrays.stream(values())
        .filter(l -> fileName.endsWith(l.ext()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported file: " + fileName));
  }
}
