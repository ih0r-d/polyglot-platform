package io.github.ih0rd.codegen.parsers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractMethod;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.LanguageParser;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;
import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

class PythonParsingBackendTest {

  private final LanguageParser parser = new PythonContractParser();

  private ContractModel parse(String source, boolean onlyIncludedMethods) {
    ScriptDescriptor descriptor = new ScriptDescriptor(SupportedLanguage.PYTHON, source, "test.py");
    CodegenConfig config = new CodegenConfig(onlyIncludedMethods);
    return parser.parse(descriptor, config);
  }

  private ContractModel parse(String source) {
    return parse(source, false);
  }

  @Test
  void parse_ShouldFailWithoutExport() {
    String source = "class Foo:\n    pass";
    assertThrows(IllegalStateException.class, () -> parse(source));
  }

  @Test
  void parse_ShouldHandleClassExport() {
    String source =
"""
polyglot.export_value("MyApi", MyClass)

class MyClass:
    def foo(self):
        return 1
""";

    ContractModel model = parse(source);
    assertEquals(1, model.classes().size());
    ContractClass clazz = model.classes().get(0);
    assertEquals("MyApi", clazz.name());
    assertEquals(1, clazz.methods().size());
    assertEquals("foo", clazz.methods().get(0).name());
    assertEquals(PolyPrimitive.INT, clazz.methods().get(0).returnType());
  }

  @Test
  void parse_ShouldHandleDictExport() {
    String source =
"""
def my_func():
    return "hello"

polyglot.export_value("MyApi", {
    "exported_func": my_func
})
""";

    ContractModel model = parse(source);
    assertEquals(1, model.classes().size());
    ContractClass clazz = model.classes().get(0);
    assertEquals("MyApi", clazz.name());

    assertEquals(1, clazz.methods().size());
    ContractMethod method = clazz.methods().get(0);
    assertEquals("exported_func", method.name()); // Exported name
    assertEquals(PolyPrimitive.STRING, method.returnType());
  }

  @Test
  void parse_ShouldRespectOnlyIncludedMethods_Class() {
    String source =
"""
polyglot.export_value("Api", C)
class C:
    @adapter_include
    def included(self):
        pass

    def excluded(self):
        pass
""";

    ContractModel model = parse(source, true);
    assertEquals(1, model.classes().get(0).methods().size());
    assertEquals("included", model.classes().get(0).methods().get(0).name());

    model = parse(source, false);
    assertEquals(2, model.classes().get(0).methods().size());
  }

  @Test
  void parse_ShouldRespectOnlyIncludedMethods_Dict() {
    String source =
"""
@adapter_include
def f1(): pass

def f2(): pass

polyglot.export_value("Api", {"m1": f1, "m2": f2})
""";

    ContractModel model = parse(source, true);
    assertEquals(1, model.classes().get(0).methods().size());
    assertEquals("m1", model.classes().get(0).methods().get(0).name());

    model = parse(source, false);
    assertEquals(2, model.classes().get(0).methods().size());
  }

  @Test
  void parse_ShouldIgnorePrivateMethods() {
    String source =
"""
polyglot.export_value("Api", C)
class C:
    def _private(self): pass
    def __init__(self): pass
    def public(self): pass
""";

    ContractModel model = parse(source);
    List<ContractMethod> methods = model.classes().get(0).methods();
    assertEquals(1, methods.size());
    assertEquals("public", methods.get(0).name());
  }

  @Test
  void parse_ShouldParseParamsWithTypes() {
    String source =
"""
polyglot.export_value("Api", C)
class C:
    def method(self, a: int, b: str = "default", c = 1):
        return 1
""";

    ContractMethod method = parse(source).classes().get(0).methods().get(0);
    assertEquals(3, method.params().size());

    assertEquals("a", method.params().get(0).name());
    assertEquals(PolyPrimitive.INT, method.params().get(0).type());

    assertEquals("b", method.params().get(1).name());
    assertEquals(PolyPrimitive.STRING, method.params().get(1).type());

    assertEquals("c", method.params().get(2).name());
    assertInstanceOf(PolyUnknown.class, method.params().get(2).type());
  }

  @Test
  void parse_ShouldInferReturnTypes_Literals() {
    assertReturnType("return 1", PolyPrimitive.INT);
    assertReturnType("return 1.5", PolyPrimitive.FLOAT);
    assertReturnType("return \"s\"", PolyPrimitive.STRING);
    assertReturnType("return True", PolyPrimitive.BOOLEAN);
    assertReturnType("return False", PolyPrimitive.BOOLEAN);
  }

  @Test
  void parse_ShouldInferReturnTypes_Collections() {
    // List[int]
    PolyType t = getReturnType("return [1, 2]");
    assertInstanceOf(PolyList.class, t);
    assertEquals(PolyPrimitive.INT, ((PolyList) t).elementType());

    // List[str]
    t = getReturnType("return ['a', 'b']");
    assertInstanceOf(PolyList.class, t);
    assertEquals(PolyPrimitive.STRING, ((PolyList) t).elementType());

    // Map<String, int>
    t = getReturnType("return {'a': 1}");
    assertInstanceOf(PolyMap.class, t);
    assertEquals(PolyPrimitive.STRING, ((PolyMap) t).keyType());
    assertEquals(PolyPrimitive.INT, ((PolyMap) t).valueType());
  }

  @Test
  void parse_ShouldInferReturnTypes_Wrappers() {
    // list([1,2]) -> List<int>
    PolyType t = getReturnType("return list([1, 2])");
    assertInstanceOf(PolyList.class, t);
    assertEquals(PolyPrimitive.INT, ((PolyList) t).elementType());

    // dict(a=1) -> Map<String, int>
    t = getReturnType("return dict(a=1, b=2)");
    assertInstanceOf(PolyMap.class, t);
    assertEquals(PolyPrimitive.INT, ((PolyMap) t).valueType());

    // tuple([1,2]) -> List<int>
    t = getReturnType("return tuple([1, 2])");
    assertInstanceOf(PolyList.class, t);
    assertEquals(PolyPrimitive.INT, ((PolyList) t).elementType());
  }

  @Test
  void parse_ShouldInferReturnTypes_Comprehensions() {
    // [x for x in range(10)] -> List<Unknown> (logic: unify nothing -> unknown)
    PolyType t = getReturnType("return [x for x in range(10)]");
    assertInstanceOf(PolyList.class, t);
    assertInstanceOf(PolyUnknown.class, ((PolyList) t).elementType());

    // [{'a':1} for x in ...] -> List<Map<String, int>>
    t = getReturnType("return [{'a': 1} for x in range(10)]");
    assertInstanceOf(PolyList.class, t);
    PolyType elem = ((PolyList) t).elementType();
    assertInstanceOf(PolyMap.class, elem);
    assertEquals(PolyPrimitive.INT, ((PolyMap) elem).valueType());
  }

  @Test
  void parse_ShouldHandleExplicitReturnAnnotation() {
    String source =
"""
polyglot.export_value("Api", C)
class C:
    def m(self) -> int:
        return "not int" // implementation ignored, signature trusted
""";
    assertEquals(PolyPrimitive.INT, parse(source).classes().get(0).methods().get(0).returnType());
  }

  @Test
  void parse_ShouldHandleMixedTypes() {
    // [1, "s"] -> List<Unknown>
    PolyType t = getReturnType("return [1, 's']");
    assertInstanceOf(PolyList.class, t);
    assertInstanceOf(PolyUnknown.class, ((PolyList) t).elementType());

    // {'a': 1, 'b': 's'} -> Map<String, Unknown>
    t = getReturnType("return {'a': 1, 'b': 's'}");
    assertInstanceOf(PolyMap.class, t);
    assertInstanceOf(PolyUnknown.class, ((PolyMap) t).valueType());
  }

  @Test
  void parseParam_ShouldRejectBlankAndInvalidIdentifiers() throws Exception {
    PythonContractParser pythonContractParser = new PythonContractParser();
    var parseParam = PythonContractParser.class.getDeclaredMethod("parseParam", String.class);
    parseParam.setAccessible(true);
    var isIdentifier = PythonContractParser.class.getDeclaredMethod("isIdentifier", String.class);
    isIdentifier.setAccessible(true);

    assertNull(parseParam.invoke(pythonContractParser, "   "));
    assertNull(parseParam.invoke(pythonContractParser, "1abc: int"));
    assertNull(parseParam.invoke(pythonContractParser, "ab-c: int"));
    assertEquals(false, isIdentifier.invoke(parser, ""));
    assertEquals(false, isIdentifier.invoke(parser, "1abc"));
    assertEquals(false, isIdentifier.invoke(parser, "ab-c"));
  }

  private PolyType getReturnType(String returnStmt) {
    String source =
"""
polyglot.export_value("Api", C)
class C:
    def m(self):
        %s
"""
            .formatted(returnStmt);
    return parse(source).classes().getFirst().methods().getFirst().returnType();
  }

  private void assertReturnType(String stmt, PolyType expected) {
    assertEquals(expected, getReturnType(stmt));
  }
}
