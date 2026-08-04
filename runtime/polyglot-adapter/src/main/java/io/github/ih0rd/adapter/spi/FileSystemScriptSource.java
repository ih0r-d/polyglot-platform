package io.github.ih0rd.adapter.spi;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptNames;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/**
 * {@link ScriptSource} implementation that loads scripts from the local filesystem.
 *
 * <p>The expected directory structure is {@code {baseDir}/{language}/{scriptName}{extension}}, for
 * example {@code scripts/python/forecast_service.py}.
 *
 * <p>This implementation is useful for local development, hot-reload scenarios, and external script
 * overrides. It is generally not suitable for packaged JAR-only deployments.
 */
public final class FileSystemScriptSource implements ScriptSource {

  private final Path baseDir;

  /**
   * Creates a filesystem-backed script source.
   *
   * @param baseDir base directory containing language subfolders
   */
  public FileSystemScriptSource(Path baseDir) {
    this.baseDir =
        Objects.requireNonNull(baseDir, "baseDir must not be null").toAbsolutePath().normalize();
  }

  /**
   * Returns {@code true} if the script file exists on disk.
   *
   * @param language script language
   * @param scriptName logical script name without extension
   * @return {@code true} if file exists
   */
  @Override
  public boolean exists(SupportedLanguage language, String scriptName) {
    Path resolved = resolve(language, scriptName);
    try {
      Path languageReal = languageDirectoryRealPath(language, scriptName);
      if (!Files.exists(resolved)) {
        return false;
      }
      Path resolvedReal = resolved.toRealPath();
      if (!resolvedReal.startsWith(languageReal)) {
        throw new IllegalArgumentException(
            "Invalid logical script name '"
                + scriptName
                + "': resolved path escapes language directory");
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Opens a script file from disk as a UTF-8 {@link Reader}.
   *
   * @param language script language
   * @param scriptName logical script name without extension
   * @return reader for file content
   * @throws IOException if file access fails
   */
  @Override
  public Reader open(SupportedLanguage language, String scriptName) throws IOException {
    Path resolved = resolve(language, scriptName);
    Path realPath = resolveRealPath(language, scriptName, resolved);
    return Files.newBufferedReader(realPath, StandardCharsets.UTF_8);
  }

  /**
   * Resolves the full filesystem path for a script.
   *
   * @param language script language
   * @param scriptName logical script name
   * @return resolved file path
   */
  private Path resolve(SupportedLanguage language, String scriptName) {
    String logicalName = ScriptNames.requireValid(scriptName);
    Path languageDir = languageDirectory(language);
    Path resolved = languageDir.resolve(logicalName + language.ext()).normalize();
    if (!resolved.startsWith(languageDir)) {
      throw new IllegalArgumentException(
          "Invalid logical script name '"
              + scriptName
              + "': resolved path escapes language directory");
    }
    return resolved;
  }

  private Path resolveRealPath(SupportedLanguage language, String scriptName, Path resolved)
      throws IOException {

    Path languageReal = languageDirectoryRealPath(language, scriptName);
    Path resolvedReal = resolved.toRealPath();
    if (!resolvedReal.startsWith(languageReal)) {
      throw new IllegalArgumentException(
          "Invalid logical script name '"
              + scriptName
              + "': resolved path escapes language directory");
    }
    return resolvedReal;
  }

  private Path languageDirectoryRealPath(SupportedLanguage language, String scriptName)
      throws IOException {

    Path baseReal = baseDir.toRealPath();
    Path languageReal = languageDirectory(language).toRealPath();
    if (!languageReal.startsWith(baseReal)) {
      throw new IllegalArgumentException(
          "Invalid logical script name '"
              + scriptName
              + "': language directory escapes base directory");
    }
    return languageReal;
  }

  private Path languageDirectory(SupportedLanguage language) {
    return baseDir.resolve(language.name().toLowerCase()).normalize();
  }
}
