package io.github.ih0rd.adapter.spi;

import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.Objects;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/// {@link ScriptSource} implementation backed by in-memory script content.
///
/// <p>This implementation is primarily intended for:</p>
/// - unit testing
/// - runtime overrides
/// - dynamically generated scripts
///
/// <p>Scripts are stored using the key format:</p>
/// <pre>
///     {language}/{scriptName}
/// </pre>
///
/// <p>No file system or classpath access is performed.</p>
public final class InMemoryScriptSource implements ScriptSource {

  private final Map<String, String> scripts;

  /// Creates an in-memory script source.
  ///
  /// @param scripts map containing script content
  ///                using keys in the format {language}/{scriptName}
  public InMemoryScriptSource(Map<String, String> scripts) {
    this.scripts = Objects.requireNonNull(scripts, "scripts must not be null");
  }

  /// Returns {@code true} if a script exists in memory.
  ///
  /// @param language script language
  /// @param scriptName logical script name without extension
  /// @return {@code true} if present in the backing map
  @Override
  public boolean exists(SupportedLanguage language, String scriptName) {
    return scripts.containsKey(key(language, scriptName));
  }

  /// Opens a script from memory as a {@link Reader}.
  ///
  /// @param language script language
  /// @param scriptName logical script name without extension
  /// @return reader over script content
  /// @throws IllegalArgumentException if script does not exist
  @Override
  public Reader open(SupportedLanguage language, String scriptName) {
    String content = scripts.get(key(language, scriptName));

    if (content == null) {
      throw new IllegalArgumentException(
          "Script not found in memory: " + key(language, scriptName));
    }

    return new StringReader(content);
  }

  /// Resolves the internal key used for storing scripts.
  ///
  /// @param language script language
  /// @param scriptName logical script name
  /// @return composite key
  private String key(SupportedLanguage language, String scriptName) {
    return language.name().toLowerCase() + "/" + scriptName;
  }
}
