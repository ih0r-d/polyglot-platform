package io.github.ih0rd.polyglot.model.config;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import io.github.ih0rd.polyglot.SupportedLanguage;

/**
 * Validation utilities for logical script names accepted by {@link ScriptSource} implementations.
 */
public final class ScriptNames {

  private ScriptNames() {}

  /**
   * Selects an explicit Python script or derives its name from the Java contract's simple name.
   *
   * @param contractName Java contract's simple name
   * @param script explicit logical name, or the empty string to use the naming convention
   * @return logical Python script name
   * @throws IllegalArgumentException if the selected name is invalid
   */
  public static String forPythonContract(String contractName, String script) {
    String name = "".equals(script) ? fromContractName(contractName) : script;
    requireValid(name);
    if (name.endsWith(SupportedLanguage.PYTHON.ext())) {
      throw invalid(name, "omit the .py extension");
    }
    return name;
  }

  /**
   * Converts a Java contract name to the conventional script name, separating each uppercase letter
   * after the first. Null and empty input are returned unchanged.
   *
   * @param contractName Java contract name
   * @return conventional snake-case script name
   */
  public static String fromContractName(String contractName) {
    if (contractName == null || contractName.isEmpty()) return contractName;
    StringBuilder name = new StringBuilder(contractName.length());
    for (int i = 0; i < contractName.length(); i++) {
      char c = contractName.charAt(i);
      if (i > 0 && Character.isUpperCase(c)) name.append('_');
      name.append(Character.toLowerCase(c));
    }
    return name.toString();
  }

  /**
   * Validates and returns a logical script name safe for filesystem and resource resolution.
   *
   * <p>A logical script name is a relative slash-separated name without a language-specific file
   * extension, for example {@code recommendation_service} or {@code scripts/utils/helper}. It must
   * not contain path traversal, platform-specific absolute paths, resource prefixes, or backslash
   * separators.
   *
   * @param scriptName logical script name
   * @return the validated script name
   * @throws IllegalArgumentException if the script name is invalid
   */
  public static String requireValid(String scriptName) {
    if (scriptName == null) {
      throw invalid(null, "must not be null");
    }

    if (scriptName.isBlank()) {
      throw invalid(scriptName, "must not be blank");
    }

    if (!scriptName.equals(scriptName.strip())) {
      throw invalid(scriptName, "must not have leading or trailing whitespace");
    }

    if (scriptName.indexOf('\\') >= 0) {
      throw invalid(scriptName, "must use '/' separators only");
    }

    if (scriptName.indexOf(':') >= 0) {
      throw invalid(scriptName, "must not contain URI/resource prefixes or drive letters");
    }

    if (scriptName.startsWith("/")) {
      throw invalid(scriptName, "must be relative");
    }

    if (scriptName.startsWith("//")) {
      throw invalid(scriptName, "must not be a UNC path");
    }

    String[] segments = scriptName.split("/", -1);
    for (String segment : segments) {
      validateSegment(scriptName, segment);
    }

    try {
      Path path = Path.of(scriptName);
      if (path.isAbsolute()) {
        throw invalid(scriptName, "must be relative");
      }
      if (!path.normalize().toString().equals(path.toString())) {
        throw invalid(scriptName, "must not contain path traversal");
      }
    } catch (InvalidPathException e) {
      throw invalid(scriptName, "must be a valid path name", e);
    }

    return scriptName;
  }

  private static void validateSegment(String scriptName, String segment) {
    if (segment.isEmpty()) {
      throw invalid(scriptName, "must not contain empty path segments");
    }
    if (".".equals(segment) || "..".equals(segment)) {
      throw invalid(scriptName, "must not contain '.' or '..' path segments");
    }
  }

  private static IllegalArgumentException invalid(String scriptName, String reason) {
    return invalid(scriptName, reason, null);
  }

  private static IllegalArgumentException invalid(
      String scriptName, String reason, Throwable cause) {
    String value = scriptName == null ? "<null>" : "'" + scriptName + "'";
    return new IllegalArgumentException(
        "Invalid logical script name " + value + ": " + reason, cause);
  }
}
