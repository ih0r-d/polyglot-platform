package io.github.ih0rd.polyglot;

import java.util.Arrays;

import io.github.ih0rd.polyglot.annotations.ExperimentalApi;

/**
 * Supported guest languages for both runtime execution and build-time tooling.
 *
 * <p>Status notes:
 *
 * <ul>
 *   <li>Python is the primary stabilization target for the current release line
 *   <li>JavaScript support exists, but is currently treated as experimental and bounded
 * </ul>
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
  /** JavaScript guest-language support. Currently treated as experimental. */
  @ExperimentalApi
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
