package io.github.ih0rd.codegen;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;

/** Maps portable contract types to Java source types. */
public final class JavaTypeRenderer {

  private final Set<String> imports = new HashSet<>();

  /** Creates a fresh renderer with an empty import collection. */
  public JavaTypeRenderer() {
    // The import set starts empty and is populated by the legacy string-rendering API.
  }

  /**
   * Renders a portable type as Java source text and tracks its imports.
   *
   * @param type portable contract type
   * @return Java source type
   */
  public String render(PolyType type) {
    Set<Class<?>> renderedImports = new HashSet<>();
    collectImports(type, renderedImports);
    renderedImports.forEach(importType -> imports.add(importType.getName()));
    return renderAst(type).toString();
  }

  /**
   * Renders a portable type as a JavaParser AST type for source generation.
   *
   * @param type portable contract type
   * @return JavaParser type node
   */
  Type renderAst(PolyType type) {
    if (type instanceof PolyPrimitive primitive) {
      return switch (primitive) {
        case INT -> classType(Integer.class);
        case FLOAT -> classType(Double.class);
        case STRING -> classType(String.class);
        case BOOLEAN -> classType(Boolean.class);
      };
    }

    if (type instanceof PolyList list) {
      return classType(List.class).setTypeArguments(renderAst(list.elementType()));
    }

    if (type instanceof PolyMap map) {
      return classType(Map.class)
          .setTypeArguments(renderAst(map.keyType()), renderAst(map.valueType()));
    }

    return classType(Object.class);
  }

  /**
   * Collects Java classes that should be imported for a portable type.
   *
   * @param type portable contract type
   * @param imports target import set
   */
  public void collectImports(PolyType type, Set<Class<?>> imports) {
    if (type instanceof PolyList list) {
      imports.add(List.class);
      collectImports(list.elementType(), imports);
    }

    if (type instanceof PolyMap map) {
      imports.add(Map.class);
      collectImports(map.keyType(), imports);
      collectImports(map.valueType(), imports);
    }
  }

  /**
   * Returns imports collected by calls to {@link #render(PolyType)}.
   *
   * @return immutable copy of imports collected during rendering
   */
  public Set<String> getImports() {
    return Set.copyOf(imports);
  }

  /** Clears imports collected by calls to {@link #render(PolyType)}. */
  public void reset() {
    imports.clear();
  }

  private static ClassOrInterfaceType classType(Class<?> type) {
    return new ClassOrInterfaceType(null, type.getSimpleName());
  }
}
