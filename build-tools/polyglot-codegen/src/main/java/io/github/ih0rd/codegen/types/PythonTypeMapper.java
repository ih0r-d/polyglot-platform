package io.github.ih0rd.codegen.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

/// # PythonTypeMapper
///
/// Maps Python type annotations to canonical {@link PolyType}.
///
/// Responsibilities:
/// - Convert Python primitive type names to canonical type model
/// - Parse generic type hints (e.g., List[int], Dict[String, int])
/// - Provide safe fallback for unsupported or unknown types
///
/// Design notes:
/// - This class handles primitive and generic mappings
/// - Container types (list, dict) are handled recursively
/// - No Java-specific logic is allowed here
/// - No rendering concerns — only canonical type mapping
///
public final class PythonTypeMapper implements LanguageTypeMapper {

  private static final Map<String, PolyType> PRIMITIVES =
      Map.of(
          "int", PolyPrimitive.INT,
          "float", PolyPrimitive.FLOAT,
          "str", PolyPrimitive.STRING,
          "bool", PolyPrimitive.BOOLEAN,
          "list", new PolyList(new PolyUnknown()),
          "dict", new PolyMap(PolyPrimitive.STRING, new PolyUnknown()),
          "set", new PolyList(new PolyUnknown()),
          "tuple", new PolyList(new PolyUnknown()),
          "Any", new PolyUnknown());

  /// ### mapPrimitive
  ///
  /// Maps a Python primitive type name or type hint to {@link PolyType}.
  ///
  /// @param languageType Python type name (e.g. "int", "List[String]")
  /// @return canonical {@link PolyType}, or {@link PolyUnknown} if unsupported
  ///
  @Override
  public PolyType mapPrimitive(String languageType) {
    if (languageType == null || languageType.isBlank()) {
      return new PolyUnknown();
    }

    return parseType(languageType.trim());
  }

  private PolyType parseType(String typeStr) {
    // Handle explicit primitives first
    if (PRIMITIVES.containsKey(typeStr)) {
      return PRIMITIVES.get(typeStr);
    }

    // Handle generic types: Base[Args]
    int openBracket = typeStr.indexOf('[');
    if (openBracket > 0 && typeStr.endsWith("]")) {
      String base = typeStr.substring(0, openBracket).trim();
      String inner = typeStr.substring(openBracket + 1, typeStr.length() - 1).trim();

      // Normalize base type case (e.g. List vs list)
      String normalizedBase = base.toLowerCase();

      List<String> args = splitGenericArgs(inner);

      if (normalizedBase.equals("list")
          || normalizedBase.equals("set")
          || normalizedBase.equals("tuple")) {
        // List[T], Set[T], Tuple[T, ...]
        // For Tuple, we unify or take the first type if uniform.
        // Fallback to Unknown if empty.
        if (args.isEmpty()) {
          return new PolyList(new PolyUnknown());
        }
        // Determine element type:
        // For list/set, usually 1 arg.
        // For tuple, multiple args. We map to List<Unify<Args>>.
        PolyType unified = unifyTypes(args);
        return new PolyList(unified);
      }

      if (normalizedBase.equals("dict")) {
        // Dict[K, V]
        if (args.size() >= 2) {
          PolyType keyType = parseType(args.get(0));
          PolyType valueType = parseType(args.get(1));

          // We only support String keys for now in contract model?
          // PolyMap supports PolyType key.
          // But if key is not String/Int, Java Map key usage is complex.
          // For now, let's allow it.
          return new PolyMap(keyType, valueType);
        }
        return new PolyMap(PolyPrimitive.STRING, new PolyUnknown());
      }

      if (base.equals("Optional")) {
        if (args.isEmpty()) return new PolyUnknown();
        return parseType(args.getFirst());
      }

      if (base.equals("Union")) {
        // Fallback for Union
        return new PolyUnknown();
      }
    }

    return new PolyUnknown();
  }

  private List<String> splitGenericArgs(String inner) {
    List<String> args = new ArrayList<>();
    int balance = 0;
    StringBuilder current = new StringBuilder();

    for (int i = 0; i < inner.length(); i++) {
      char c = inner.charAt(i);
      if (c == '[') balance++;
      else if (c == ']') balance--;

      if (c == ',' && balance == 0) {
        args.add(current.toString().trim());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    if (!current.isEmpty()) {
      args.add(current.toString().trim());
    }
    return args;
  }

  private PolyType unifyTypes(List<String> typeStrings) {
    PolyType result = null;
    for (String ts : typeStrings) {
      PolyType t = parseType(ts);
      if (result == null) {
        result = t;
      } else {
        result = unify(result, t);
      }
    }
    return result != null ? result : new PolyUnknown();
  }

  private PolyType unify(PolyType a, PolyType b) {
    // Simple unification for type hints
    if (a instanceof PolyPrimitive pa && b instanceof PolyPrimitive pb && pa == pb) {
      return pa;
    }
    if (a instanceof PolyUnknown || b instanceof PolyUnknown) {
      return new PolyUnknown();
    }
    // If both are lists, unify elements
    if (a instanceof PolyList(PolyType elementType) && b instanceof PolyList(PolyType type)) {
      return new PolyList(unify(elementType, type));
    }
    // Fallback to Unknown for mixed types
    return new PolyUnknown();
  }
}
