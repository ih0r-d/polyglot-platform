package io.github.ih0rd.adapter.spi;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Objects;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/// {@link ScriptSource} implementation that delegates resolution
/// to multiple underlying sources.
///
/// <p>Resolution strategy:</p>
/// <ul>
///     <li>Sources are evaluated in order</li>
///     <li>The first source reporting {@code exists(...)} is used</li>
/// </ul>
///
/// <p>Typical usage:</p>
/// <pre>
///     InMemory → FileSystem → Classpath
/// </pre>
///
/// <p>This allows layered overrides and fallback chains.</p>
public final class CompositeScriptSource implements ScriptSource {

  private final List<ScriptSource> delegates;

  /// Creates a composite script source.
  ///
  /// @param delegates ordered list of script sources
  public CompositeScriptSource(List<ScriptSource> delegates) {
    this.delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates must not be null"));
  }

  /// Returns {@code true} if any delegate contains the script.
  ///
  /// @param language script language
  /// @param scriptName logical script name without extension
  /// @return {@code true} if at least one delegate provides the script
  @Override
  public boolean exists(SupportedLanguage language, String scriptName) {
    return delegates.stream().anyMatch(d -> d.exists(language, scriptName));
  }

  /// Opens the script from the first delegate that provides it.
  ///
  /// @param language script language
  /// @param scriptName logical script name without extension
  /// @return reader for script content
  /// @throws IllegalArgumentException if no delegate provides the script
  @Override
  public Reader open(SupportedLanguage language, String scriptName) throws IOException {
    return delegates.stream()
        .filter(d -> d.exists(language, scriptName))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Script not found in any source: " + language + "/" + scriptName))
        .open(language, scriptName);
  }
}
