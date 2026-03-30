package io.github.ih0rd.codegen.types;

import io.github.ih0rd.polyglot.model.types.PolyType;

/**
 * Language-specific type mapping contract.
 *
 * <p><strong>Responsibilities:</strong>
 *
 * <ul>
 *   <li>Convert language-native type annotations into canonical {@link PolyType}
 * </ul>
 *
 * <p><strong>Design notes:</strong>
 *
 * <ul>
 *   <li>Does not perform rendering
 *   <li>Does not contain Java-specific logic
 *   <li>Must remain deterministic and side effect free
 * </ul>
 */
public interface LanguageTypeMapper {

  /**
   * Maps a primitive language type to the canonical model.
   *
   * @param languageType language-specific type representation
   * @return canonical {@link PolyType}
   */
  PolyType mapPrimitive(String languageType);
}
