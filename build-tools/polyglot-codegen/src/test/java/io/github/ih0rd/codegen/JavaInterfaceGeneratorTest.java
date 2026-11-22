package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.javaparser.ast.CompilationUnit;

import io.github.ih0rd.codegen.parsers.PythonContractParser;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractMethod;
import io.github.ih0rd.polyglot.model.ContractParam;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;
import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

class JavaInterfaceGeneratorTest {

  private final JavaInterfaceGenerator generator = new JavaInterfaceGenerator();

  @Test
  void compilesWhenPythonExportNamesShadowJavaTypes(@TempDir Path directory) throws Exception {
    var script =
        new ScriptDescriptor(
            SupportedLanguage.PYTHON,
            """
        def items(value: list[str]) -> list[int]: pass
        def label() -> str: pass
        polyglot.export_value('List', {'items': items})
        polyglot.export_value('String', {'label': label})
        polyglot.export_value('Api', {'label': label})
        """,
            "api.py");
    try (var parser = PythonContractParser.openBatch()) {
      var contracts = parser.parse(script, new CodegenConfig(false)).classes();
      var compilerArgs = new ArrayList<>(List.of("--release", "21", "-d", directory.toString()));
      assertTrue(
          generator.generate(contracts.get(0), "example").contains("java.util.List<Integer>"));
      assertTrue(generator.generate(contracts.get(1), "example").contains("java.lang.String"));
      for (var contract : contracts) {
        String generated =
            generator.generate(contract, "example", null, Set.of("List", "String", "Api"));
        assertFalse(generated.contains("import java.util.List;"));
        compilerArgs.add(
            Files.writeString(directory.resolve(contract.name() + ".java"), generated).toString());
      }
      var errors = new ByteArrayOutputStream();
      assertEquals(
          0,
          ToolProvider.getSystemJavaCompiler()
              .run(null, null, errors, compilerArgs.toArray(String[]::new)),
          errors.toString(StandardCharsets.UTF_8));
      try (var loader = new URLClassLoader(new URL[] {directory.toUri().toURL()})) {
        var items = loader.loadClass("example.List").getMethod("items", List.class);
        assertEquals(
            "java.util.List<java.lang.Integer>", items.getGenericReturnType().getTypeName());
        assertEquals(
            "java.util.List<java.lang.String>", items.getGenericParameterTypes()[0].getTypeName());
        assertEquals(
            String.class, loader.loadClass("example.String").getMethod("label").getReturnType());
        assertEquals(
            String.class, loader.loadClass("example.Api").getMethod("label").getReturnType());
      }
    }
  }

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

  @Test
  void generate_ShouldRenderUnicodeJavaIdentifiers() {
    ContractParam param = new ContractParam("значення", PolyPrimitive.INT);
    ContractMethod method = new ContractMethod("привіт", List.of(param), PolyPrimitive.STRING);
    ContractClass clazz = new ContractClass("СервісApi", List.of(method));

    String source = generator.generate(clazz, "com.demo");

    assertTrue(source.contains("public interface СервісApi {"));
    assertTrue(source.contains("String привіт(Integer значення);"));
  }

  @Test
  void generateAst_ShouldExposeInspectableJavaAst() {
    ContractMethod method = new ContractMethod("ping", List.of(), PolyPrimitive.STRING);
    ContractClass clazz = new ContractClass("DebugApi", List.of(method));

    CompilationUnit ast = generator.generateAst(clazz, "com.demo");

    assertEquals("com.demo", ast.getPackageDeclaration().orElseThrow().getNameAsString());
    assertTrue(ast.getInterfaceByName("DebugApi").isPresent());
    assertTrue(
        ast.getInterfaceByName("DebugApi").orElseThrow().getMethodsByName("ping").size() == 1);
  }

  @Test
  void generatesAnnotatedSnapshotWithExplicitPythonSource() throws Exception {
    ContractClass contract =
        new ContractClass(
            "PricingApi",
            List.of(
                new ContractMethod(
                    "quote",
                    List.of(new ContractParam("sku", PolyPrimitive.STRING)),
                    PolyPrimitive.INT)));
    try (var golden = getClass().getResourceAsStream("/java-golden/PricingApi.java.expected")) {
      assertEquals(
          new String(golden.readAllBytes(), StandardCharsets.UTF_8),
          generator.generate(contract, "consumer.generated", "contracts/pricing"));
    }
    assertFalse(generator.generate(contract, "consumer.generated").contains("@PolyglotClient"));
    var declaration =
        generator
            .generateAst(contract, "consumer.generated", "")
            .getInterfaceByName("PricingApi")
            .orElseThrow();
    assertEquals(
        "languages",
        declaration.getAnnotation(0).asNormalAnnotationExpr().getPairs().get(0).getNameAsString());
    assertEquals(1, declaration.getAnnotation(0).asNormalAnnotationExpr().getPairs().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> generator.generate(contract, "consumer.generated", "../pricing"));
  }

  @ParameterizedTest
  @MethodSource("v040Contracts")
  void generate_ShouldPreserveV040SourceAndRemainDeterministic(
      ContractClass contract, String expected) {
    assertEquals(expected, generator.generate(contract, "com.demo"));

    // Interleave another contract to catch import or formatting state leaking between calls.
    generator.generate(
        new ContractClass(
            "OtherApi",
            List.of(new ContractMethod("values", List.of(), new PolyList(PolyPrimitive.INT)))),
        "other.package_name");

    assertEquals(expected, generator.generate(contract, "com.demo"));
    assertEquals(expected, new JavaInterfaceGenerator().generate(contract, "com.demo"));
  }

  // Golden sources captured from v0.4.0 on an LF platform, including checksum and final newline.
  private static Stream<Arguments> v040Contracts() {
    return Stream.of(
        Arguments.of(
            new ContractClass("EmptyApi", List.of()),
            """
            /**
             * This file was generated by polyglot-codegen.
             *
             * SHA-256 checksum: d2bb2b6c388699cda6ec8b75b838748a181be6bda6141c5b542682933f49dcf5
             *
             * Any manual changes will be overwritten.
             */
            package com.demo;

            public interface EmptyApi {

            }
            """),
        Arguments.of(
            new ContractClass(
                "MultiApi",
                List.of(
                    new ContractMethod("count", List.of(), PolyPrimitive.INT),
                    new ContractMethod(
                        "name",
                        List.of(new ContractParam("key", PolyPrimitive.STRING)),
                        PolyPrimitive.STRING))),
            """
            /**
             * This file was generated by polyglot-codegen.
             *
             * SHA-256 checksum: 3d803ff95927ab15972acfa7aa3851944cad55db3fc3af23fe23878c266b9f05
             *
             * Any manual changes will be overwritten.
             */
            package com.demo;

            public interface MultiApi {

                Integer count();
                String name(String key);
            }
            """),
        Arguments.of(
            new ContractClass(
                "NestedApi",
                List.of(
                    new ContractMethod(
                        "transform",
                        List.of(
                            new ContractParam(
                                "data",
                                new PolyList(
                                    new PolyMap(PolyPrimitive.STRING, PolyPrimitive.FLOAT)))),
                        new PolyMap(PolyPrimitive.STRING, new PolyList(PolyPrimitive.BOOLEAN))))),
            """
            /**
             * This file was generated by polyglot-codegen.
             *
             * SHA-256 checksum: 6a20d957ed48de07a00d7932259746c0ebd7b762623c04cbe16e389af371d71e
             *
             * Any manual changes will be overwritten.
             */
            package com.demo;

            import java.util.List;
            import java.util.Map;

            public interface NestedApi {

                Map<String, List<Boolean>> transform(List<Map<String, Double>> data);
            }
            """));
  }
}
