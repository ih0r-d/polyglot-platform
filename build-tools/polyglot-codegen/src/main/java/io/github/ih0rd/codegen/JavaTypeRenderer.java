package io.github.ih0rd.codegen;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;

/** Maps portable contract types to JavaParser AST type nodes. */
public final class JavaTypeRenderer {

  /** Creates a fresh renderer. */
  public JavaTypeRenderer() {
    // The renderer is stateless; import collection is explicit for the generated AST.
  }

  /**
   * Renders a portable type as a JavaParser AST type.
   *
   * @param type portable contract type
   * @return Java type representation
   */
  public Type render(PolyType type) {

    if (type instanceof PolyPrimitive p) {
      return switch (p) {
        case INT -> classType(Integer.class);
        case FLOAT -> classType(Double.class);
        case STRING -> classType(String.class);
        case BOOLEAN -> classType(Boolean.class);
      };
    }

    if (type instanceof PolyList list) {
      return classType(List.class).setTypeArguments(render(list.elementType()));
    }

    if (type instanceof PolyMap map) {
      return classType(Map.class).setTypeArguments(render(map.keyType()), render(map.valueType()));
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

  private static ClassOrInterfaceType classType(Class<?> type) {
    return new ClassOrInterfaceType(null, type.getSimpleName());
  }
}
