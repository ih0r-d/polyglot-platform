package io.github.ih0rd.adapter.spi;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptSource;

/// {@link ScriptSource} implementation that loads scripts from the application classpath.
///
/// <p>Scripts are resolved using the following convention:</p>
/// <pre>
///     /{language}/{scriptName}{extension}
/// </pre>
///
/// <p>Example:</p>
/// <pre>
///     /python/forecast_service.py
///     /js/forecast_service.js
/// </pre>
///
/// <p>This implementation is safe for:
/// - packaged JAR execution
/// - containerized environments
/// - GraalVM native image (with resources configured)</p>
public final class ClasspathScriptSource implements ScriptSource {

  private final ClassLoader classLoader;

  /// Creates a classpath-backed script source using the current thread context class loader.
  public ClasspathScriptSource() {
    this(Thread.currentThread().getContextClassLoader());
  }

  /// Creates a classpath-backed script source using a specific class loader.
  ///
  /// @param classLoader class loader used for resolving script resources
  public ClasspathScriptSource(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  /// Returns {@code true} if a script resource exists on the classpath.
  ///
  /// @param language script language
  /// @param scriptName logical script name without extension
  /// @return {@code true} if resource is present
  @Override
  public boolean exists(SupportedLanguage language, String scriptName) {
    return classLoader.getResource(resolve(language, scriptName)) != null;
  }

  /// Opens a script resource as a UTF-8 {@link Reader}.
  ///
  /// @param language script language
  /// @param scriptName logical script name without extension
  /// @return reader for script content
  /// @throws IllegalArgumentException if the script does not exist
  @Override
  public Reader open(SupportedLanguage language, String scriptName) {
    String path = resolve(language, scriptName);
    InputStream stream = classLoader.getResourceAsStream(path);

    if (stream == null) {
      throw new IllegalArgumentException("Script not found on classpath: " + path);
    }

    return new InputStreamReader(stream, StandardCharsets.UTF_8);
  }

  /// Resolves the internal classpath location for a script.
  ///
  /// @param language script language
  /// @param scriptName logical script name without extension
  /// @return resource path relative to classpath root
  private String resolve(SupportedLanguage language, String scriptName) {
    return language.name().toLowerCase() + "/" + scriptName + language.ext();
  }
}
