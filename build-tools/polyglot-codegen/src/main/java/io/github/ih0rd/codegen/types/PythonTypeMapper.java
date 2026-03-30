package io.github.ih0rd.codegen.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

/**
 * Maps Python type annotations to canonical {@link PolyType}.
 *
 * <p><strong>Responsibilities:</strong>
 *
 * <ul>
 *   <li>Convert Python primitive type names to the canonical type model
 *   <li>Parse generic type hints such as {@code List[int]} and {@code Dict[String, int]}
 *   <li>Provide a safe fallback for unsupported or unknown types
 * </ul>
 *
 * <p><strong>Design notes:</strong>
 *
 * <ul>
 *   <li>This class handles primitive and generic mappings
 *   <li>Container types such as lists and dictionaries are handled recursively
 *   <li>No Java-specific logic is allowed here
 *   <li>No rendering concerns, only canonical type mapping
 * </ul>
 */
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

  /** Creates a mapper for Python primitive and generic type hints. */
  public PythonTypeMapper() {
    // Mapping tables are static; the instance remains stateless.
  }

  /**
   * Maps a Python primitive type name or type hint to {@link PolyType}.
   *
   * @param languageType Python type name, for example {@code int} or {@code List[String]}
   * @return canonical {@link PolyType}, or {@link PolyUnknown} if unsupported
   */
  @Override
  public PolyType mapPrimitive(String languageType) {
    if (languageType == null || languageType.isBlank()) {
      return new PolyUnknown();
    }

    return parseType(languageType.trim());
  }

  private PolyType parseType(String typeStr) {
    PolyType primitive = PRIMITIVES.get(typeStr);
    if (primitive != null) {
      return primitive;
    }

    int openBracket = typeStr.indexOf('[');
    if (openBracket <= 0 || !typeStr.endsWith("]")) {
      return new PolyUnknown();
    }

    return parseGenericType(typeStr, openBracket);
  }

  private PolyType parseGenericType(String typeStr, int openBracket) {
    String base = typeStr.substring(0, openBracket).trim();
    String inner = typeStr.substring(openBracket + 1, typeStr.length() - 1).trim();
    List<String> args = splitGenericArgs(inner);
    String normalizedBase = base.toLowerCase();

    if (isCollectionType(normalizedBase)) {
      return mapCollectionType(args);
    }
    if (normalizedBase.equals("dict")) {
      return mapDictType(args);
    }
    if (base.equals("Optional")) {
      return mapOptionalType(args);
    }
    if (base.equals("Union")) {
      return new PolyUnknown();
    }
    return new PolyUnknown();
  }

  private boolean isCollectionType(String normalizedBase) {
    return normalizedBase.equals("list")
        || normalizedBase.equals("set")
        || normalizedBase.equals("tuple");
  }

  private PolyType mapCollectionType(List<String> args) {
    if (args.isEmpty()) {
      return new PolyList(new PolyUnknown());
    }
    return new PolyList(unifyTypes(args));
  }

  private PolyType mapDictType(List<String> args) {
    if (args.size() < 2) {
      return new PolyMap(PolyPrimitive.STRING, new PolyUnknown());
    }
    PolyType keyType = parseType(args.get(0));
    PolyType valueType = parseType(args.get(1));
    return new PolyMap(keyType, valueType);
  }

  private PolyType mapOptionalType(List<String> args) {
    if (args.isEmpty()) {
      return new PolyUnknown();
    }
    return parseType(args.getFirst());
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
