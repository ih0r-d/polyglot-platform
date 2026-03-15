package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractMethod;
import io.github.ih0rd.polyglot.model.ContractParam;
import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

class JavaInterfaceGeneratorTest {

  private final JavaInterfaceGenerator generator = new JavaInterfaceGenerator();

  @Test
  void generate_ShouldRenderSimpleInterface() {
    ContractMethod m1 = new ContractMethod("foo", List.of(), PolyPrimitive.INT);
    ContractClass clazz = new ContractClass("MyApi", List.of(m1));

    String source = generator.generate(clazz, "com.demo");

    assertTrue(source.contains("public interface MyApi {"));
    assertTrue(source.contains("Integer foo();"));
    assertFalse(source.contains("import java.util.List;"));
  }

  @Test
  void generate_ShouldRenderImportsForCollections() {
    // List<String>
    ContractMethod m1 =
        new ContractMethod("listMethod", List.of(), new PolyList(PolyPrimitive.STRING));
    // Map<String, Integer>
    ContractMethod m2 =
        new ContractMethod(
            "mapMethod", List.of(), new PolyMap(PolyPrimitive.STRING, PolyPrimitive.INT));

    ContractClass clazz = new ContractClass("CollectionApi", List.of(m1, m2));

    String source = generator.generate(clazz, "com.demo");

    assertTrue(source.contains("import java.util.List;"));
    assertTrue(source.contains("import java.util.Map;"));
    assertTrue(source.contains("List<String> listMethod();"));
    assertTrue(source.contains("Map<String, Integer> mapMethod();"));
  }

  @Test
  void generate_ShouldRenderParameters() {
    ContractParam p1 = new ContractParam("a", PolyPrimitive.INT);
    ContractParam p2 = new ContractParam("b", new PolyList(PolyPrimitive.STRING));

    ContractMethod m1 = new ContractMethod("params", List.of(p1, p2), new PolyUnknown());
    ContractClass clazz = new ContractClass("ParamApi", List.of(m1));

    String source = generator.generate(clazz, "com.demo");

    assertTrue(source.contains("Object params(Integer a, List<String> b);"));
  }
}
