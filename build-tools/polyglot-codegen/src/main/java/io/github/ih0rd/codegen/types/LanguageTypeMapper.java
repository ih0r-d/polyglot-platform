package io.github.ih0rd.codegen.types;

import io.github.ih0rd.polyglot.model.types.PolyType;

/// # LanguageTypeMapper
///
/// Language-specific type mapping contract.
///
/// Responsibilities:
/// - Convert language-native type annotations into canonical {@link PolyType}
///
/// Design notes:
/// - Does NOT perform rendering
/// - Does NOT contain Java-specific logic
/// - Must remain deterministic and side effect free
///
public interface LanguageTypeMapper {

  /// Maps primitive language type to canonical model.
  ///
  /// @param languageType language-specific type representation
  /// @return canonical {@link PolyType}
  ///
  PolyType mapPrimitive(String languageType);
}
