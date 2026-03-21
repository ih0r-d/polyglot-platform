package io.github.ih0rd.codegen;

import java.util.HashSet;
import java.util.Set;

import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;

/** Maps portable contract types to Java source types and collects required imports. */
public final class JavaTypeRenderer {

  private final Set<String> imports = new HashSet<>();

  /**
   * Renders a portable type as a Java source type.
   *
   * @param type portable contract type
   * @return Java source representation
   */
  public String render(PolyType type) {

    if (type instanceof PolyPrimitive p) {
      return switch (p) {
        case INT -> "Integer";
        case FLOAT -> "Double";
        case STRING -> "String";
        case BOOLEAN -> "Boolean";
      };
    }

    if (type instanceof PolyList(PolyType elementType)) {
      imports.add("java.util.List");
      return "List<" + render(elementType) + ">";
    }

    if (type instanceof PolyMap(PolyType keyType, PolyType valueType)) {
      imports.add("java.util.Map");
      return "Map<" + render(keyType) + ", " + render(valueType) + ">";
    }

    return "Object";
  }

  /** Returns the imports collected while rendering the current interface. */
  public Set<String> getImports() {
    return Set.copyOf(imports);
  }

  /** Clears collected imports before rendering the next contract. */
  public void reset() {
    imports.clear();
  }
}
