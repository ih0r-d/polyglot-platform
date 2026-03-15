package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

class JavaTypeRendererTest {

  private final JavaTypeRenderer renderer = new JavaTypeRenderer();

  @Test
  void render_ShouldHandlePrimitives() {
    assertEquals("Integer", renderer.render(PolyPrimitive.INT));
    assertEquals("Double", renderer.render(PolyPrimitive.FLOAT));
    assertEquals("String", renderer.render(PolyPrimitive.STRING));
    assertEquals("Boolean", renderer.render(PolyPrimitive.BOOLEAN));
  }

  @Test
  void render_ShouldHandleUnknown() {
    assertEquals("Object", renderer.render(new PolyUnknown()));
  }

  @Test
  void render_ShouldHandleList() {
    // List<String>
    PolyList listType = new PolyList(PolyPrimitive.STRING);
    assertEquals("List<String>", renderer.render(listType));
    assertTrue(renderer.getImports().contains("java.util.List"));

    // List<Unknown> -> List<Object>
    PolyList unknownList = new PolyList(new PolyUnknown());
    assertEquals("List<Object>", renderer.render(unknownList));
  }

  @Test
  void render_ShouldHandleMap() {
    // Map<String, Integer>
    PolyMap mapType = new PolyMap(PolyPrimitive.STRING, PolyPrimitive.INT);
    assertEquals("Map<String, Integer>", renderer.render(mapType));
    assertTrue(renderer.getImports().contains("java.util.Map"));

    // Map<Unknown, Unknown> -> Map<Object, Object>
    PolyMap unknownMap = new PolyMap(new PolyUnknown(), new PolyUnknown());
    assertEquals("Map<Object, Object>", renderer.render(unknownMap));
  }
}
