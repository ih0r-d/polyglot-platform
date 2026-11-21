package io.github.ih0rd.codegen.parsers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;
import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

class PythonAstSemanticRegressionTest {
  @Test
  void importedExportAliasesResolveClassAliasesAndVariadicReceivers() {
    var script =
        script(
            """
        from polyglot import export_value as publish
        class Implementation:
            def hello(*args, enabled=True): pass
        Alias = Implementation
        publish('Api', Alias)
        """);
    try (var parser = PythonContractParser.openBatch()) {
      var binding = parser.parseBindings(script, Convention.DEFAULT);
      assertTrue(binding.complete(), binding.diagnostics().toString());
      assertEquals(Set.of("Api.hello"), binding.functions().keySet());
      assertEquals(0, binding.functions().get("Api.hello").required());
      assertEquals(Integer.MAX_VALUE, binding.functions().get("Api.hello").maximum());
      assertTrue(
          parser
              .parse(script, new CodegenConfig(false))
              .classes()
              .getFirst()
              .methods()
              .getFirst()
              .params()
              .isEmpty());
    }
  }

  @Test
  void unsupportedExportsImportsAndClassDefinitionsStayUnknown() {
    String prefix = "class Api:\n    def hello(self): pass\ndef hello(): pass\n";
    List<String> sources =
        List.of(
            prefix + "polyglot.export_value(name='Api', value=Api)",
            prefix + "polyglot.export_value(123, Api)",
            prefix + "polyglot.export_value(b'Api', Api)",
            prefix + "polyglot.export_value('Api')",
            prefix + "polyglot.export_value('Api', {**mapping})",
            prefix + "polyglot.export_value('Api', {1: hello})",
            prefix + "polyglot.export_value('Api', {'hello': instance.hello})",
            prefix + "polyglot.export_value('Api', {'hello': missing})",
            prefix + "polyglot.export_value('Api', {'hello': hello, 'hello': hello})",
            "from typing import *\ndef hello(): pass",
            "from .polyglot import export_value\ndef hello(): pass",
            "class Api:\n    def hello(): pass\npolyglot.export_value('Api', Api)",
            "class Api:\n    def hello(*, value): pass\npolyglot.export_value('Api', Api)",
            "class Api:\n    def hello(self): pass\n    def hello(self, x): pass\npolyglot.export_value('Api', Api)",
            "class Api[T]:\n    def hello(self): pass\npolyglot.export_value('Api', Api)",
            "type Values[T] = list[T]\ndef hello(): pass",
            "def hello(value=(lambda: 1)): pass",
            "def hello(value=(x := 1)): pass",
            "def hello(value=f'{1}'): pass",
            "def hello():\n    yield from []");
    try (var parser = PythonContractParser.openBatch()) {
      for (String source : sources) {
        var contract = parser.parseCompatibility(script(source));
        assertFalse(contract.complete(), source);
        assertFalse(contract.diagnostics().isEmpty(), source);
        assertTrue(
            contract.diagnostics().stream().allMatch(d -> d.file().equals("api.py")), source);
        assertFalse(parser.parseBindings(script(source), Convention.DEFAULT).complete(), source);
      }
    }
  }

  @Test
  void collectionInferenceRetainsSupportedMappingsAndUnknownFallbacks() {
    PolyType unknown = new PolyUnknown();
    List<Map.Entry<String, PolyType>> expressions =
        List.of(
            Map.entry("None", unknown),
            Map.entry("b'bytes'", unknown),
            Map.entry("1j", unknown),
            Map.entry("...", unknown),
            Map.entry("-1", PolyPrimitive.INT),
            Map.entry("list([1, 2])", new PolyList(PolyPrimitive.INT)),
            Map.entry("tuple([1, 2])", new PolyList(PolyPrimitive.INT)),
            Map.entry("list(values)", unknown),
            Map.entry("list()", unknown),
            Map.entry("dict(one=1, two=2)", new PolyMap(PolyPrimitive.STRING, PolyPrimitive.INT)),
            Map.entry("dict({'one': 1})", new PolyMap(PolyPrimitive.STRING, PolyPrimitive.INT)),
            Map.entry("dict(values)", new PolyMap(PolyPrimitive.STRING, unknown)),
            Map.entry("{x: 1 for x in ()}", new PolyMap(PolyPrimitive.STRING, unknown)),
            Map.entry("[x for x in ()]", new PolyList(unknown)),
            Map.entry("other()", unknown),
            Map.entry("module.list([1])", unknown));
    try (var parser = PythonContractParser.openBatch()) {
      for (var example : expressions) {
        var model =
            parser.parse(
                script(
                    "class Api:\n    def value(self):\n        return "
                        + example.getKey()
                        + "\npolyglot.export_value('Api', Api)"),
                new CodegenConfig(false));
        assertEquals(
            example.getValue(),
            model.classes().getFirst().methods().getFirst().returnType(),
            example.getKey());
      }
      for (String expression : List.of("{1, 2}", "{x for x in ()}", "set()")) {
        var failure =
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    parser.parse(
                        script(
                            "class Api:\n    def value(self):\n        return "
                                + expression
                                + "\npolyglot.export_value('Api', Api)"),
                        new CodegenConfig(false)),
                expression);
        assertTrue(failure.getMessage().contains("Python set"));
        assertTrue(failure.getMessage().contains("api.py"));
      }
    }
  }

  private static ScriptDescriptor script(String source) {
    return new ScriptDescriptor(SupportedLanguage.PYTHON, source, "api.py");
  }
}
