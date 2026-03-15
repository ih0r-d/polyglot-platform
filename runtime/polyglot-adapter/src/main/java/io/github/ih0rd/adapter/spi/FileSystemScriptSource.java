package io.github.ih0rd.adapter.spi;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/// {@link ScriptSource} implementation that loads scripts from the local filesystem.
///
/// <p>Expected directory structure:</p>
/// <pre>
///     {baseDir}/{language}/{scriptName}{extension}
/// </pre>
///
/// <p>This implementation is suitable for:</p>
/// - local development
/// - hot-reload scenarios
/// - external script overrides
///
/// <p>It is not recommended for packaged JAR-only environments.</p>
public final class FileSystemScriptSource implements ScriptSource {

  private final Path baseDir;

  /// Creates a filesystem-backed script source.
  ///
  /// @param baseDir base directory containing language subfolders
  public FileSystemScriptSource(Path baseDir) {
    this.baseDir = baseDir;
  }

  /// Returns {@code true} if the script file exists on disk.
  ///
  /// @param language script language
  /// @param scriptName logical script name without extension
  /// @return {@code true} if file exists
  @Override
  public boolean exists(SupportedLanguage language, String scriptName) {
    return Files.exists(resolve(language, scriptName));
  }

  /// Opens a script file from disk as a UTF-8 {@link Reader}.
  ///
  /// @param language script language
  /// @param scriptName logical script name without extension
  /// @return reader for file content
  /// @throws IOException if file access fails
  @Override
  public Reader open(SupportedLanguage language, String scriptName) throws IOException {
    return Files.newBufferedReader(resolve(language, scriptName), StandardCharsets.UTF_8);
  }

  /// Resolves the full filesystem path for a script.
  ///
  /// @param language script language
  /// @param scriptName logical script name
  /// @return resolved file path
  private Path resolve(SupportedLanguage language, String scriptName) {
    return baseDir.resolve(language.name().toLowerCase()).resolve(scriptName + language.ext());
  }
}
