package io.github.ih0rd.codegen.types;

import java.util.HashMap;
import java.util.Map;

import io.github.ih0rd.codegen.internal.python.PythonAstSession;
import io.github.ih0rd.codegen.internal.python.PythonAstTypes;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

/** Maps official AST annotation metadata to portable types without a separate type-hint grammar. */
public final class PythonTypeMapper implements LanguageTypeMapper {
  private final Map<String, PolyType> cache = new HashMap<>();

  /** Creates a type mapper retaining the released constructor. */
  public PythonTypeMapper() {}

  /**
   * Parses a type expression through the same isolated AST backend used for contracts.
   *
   * @return canonical mapping, or PolyUnknown for unsupported syntax/types
   * @throws IllegalArgumentException for unsafe Python set-to-List conversion
   */
  @Override
  public synchronized PolyType mapPrimitive(String languageType) {
    if (languageType == null || languageType.isBlank()) return new PolyUnknown();
    return cache.computeIfAbsent(
        languageType.strip(),
        annotation -> {
          try (var session = new PythonAstSession()) {
            return PythonAstTypes.annotation(session, annotation).type();
          }
        });
  }
}
