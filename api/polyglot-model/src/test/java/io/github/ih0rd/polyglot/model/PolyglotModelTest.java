package io.github.ih0rd.polyglot.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.config.ScriptSource;
import io.github.ih0rd.polyglot.model.parser.LanguageParser;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;
import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyObject;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnion;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

class PolyglotModelTest {

  @Test
  void copiesListsAndMapsDefensively() {
    ContractParam param = new ContractParam("name", PolyPrimitive.STRING);
    List<ContractParam> params = new java.util.ArrayList<>(List.of(param));
    ContractMethod method = new ContractMethod("hello", params, PolyPrimitive.STRING);
    params.add(new ContractParam("ignored", PolyPrimitive.INT));
    assertEquals(1, method.params().size());
    List<ContractParam> copiedParams = method.params();
    assertThrows(UnsupportedOperationException.class, () -> copiedParams.add(param));

    List<ContractMethod> methods = new java.util.ArrayList<>(List.of(method));
    ContractClass contractClass = new ContractClass("GreetingService", methods);
    methods.clear();
    assertEquals(1, contractClass.methods().size());
    List<ContractMethod> copiedMethods = contractClass.methods();
    assertThrows(UnsupportedOperationException.class, copiedMethods::clear);

    List<ContractClass> classes = new java.util.ArrayList<>(List.of(contractClass));
    ContractModel model = new ContractModel(classes);
    classes.clear();
    assertEquals(1, model.classes().size());
    List<ContractClass> copiedClasses = model.classes();
    assertThrows(UnsupportedOperationException.class, copiedClasses::clear);

    Map<String, PolyType> fields = new java.util.HashMap<>();
    fields.put("id", PolyPrimitive.INT);
    PolyObject object = new PolyObject(fields);
    fields.put("name", PolyPrimitive.STRING);
    assertEquals(1, object.fields().size());
    Map<String, PolyType> copiedFields = object.fields();
    assertThrows(
        UnsupportedOperationException.class, () -> copiedFields.put("x", PolyPrimitive.BOOLEAN));

    List<PolyType> variants =
        new java.util.ArrayList<>(List.of(PolyPrimitive.INT, new PolyUnknown()));
    PolyUnion union = new PolyUnion(variants);
    variants.clear();
    assertEquals(2, union.variants().size());
    List<PolyType> copiedVariants = union.variants();
    assertThrows(
        UnsupportedOperationException.class, () -> copiedVariants.add(PolyPrimitive.STRING));
  }

  @Test
  void exposesRecordAndEnumValues() {
    ContractParam param = new ContractParam("count", PolyPrimitive.INT);
    ContractMethod method = new ContractMethod("sum", List.of(param), PolyPrimitive.INT);
    ContractClass contractClass = new ContractClass("Calculator", List.of(method));
    ContractModel model = new ContractModel(List.of(contractClass));
    ScriptDescriptor descriptor =
        new ScriptDescriptor(SupportedLanguage.PYTHON, "print('x')", "demo.py");
    CodegenConfig config = new CodegenConfig(true);
    PolyList list = new PolyList(PolyPrimitive.STRING);
    PolyMap map = new PolyMap(PolyPrimitive.STRING, PolyPrimitive.INT);

    assertEquals("count", param.name());
    assertEquals(PolyPrimitive.INT, param.type());
    assertEquals("sum", method.name());
    assertEquals(PolyPrimitive.INT, method.returnType());
    assertEquals("Calculator", contractClass.name());
    assertEquals(contractClass, model.classes().get(0));
    assertEquals(SupportedLanguage.PYTHON, descriptor.language());
    assertEquals("print('x')", descriptor.source());
    assertEquals("demo.py", descriptor.fileName());
    assertEquals(true, config.onlyIncludedMethods());
    assertEquals(false, config.strictMode());
    assertEquals(PolyPrimitive.STRING, list.elementType());
    assertEquals(PolyPrimitive.STRING, map.keyType());
    assertEquals(PolyPrimitive.INT, map.valueType());
    assertEquals(
        List.of("INT", "FLOAT", "STRING", "BOOLEAN"),
        List.of(PolyPrimitive.values()).stream().map(Enum::name).toList());
    assertInstanceOf(PolyType.class, list);
    assertInstanceOf(PolyType.class, map);
    assertInstanceOf(PolyType.class, PolyPrimitive.BOOLEAN);
    assertInstanceOf(PolyType.class, new PolyUnknown());
  }

  @Test
  void supportsSimpleSpiContracts() throws Exception {
    ScriptSource source =
        new ScriptSource() {
          @Override
          public boolean exists(SupportedLanguage language, String scriptName) {
            return language == SupportedLanguage.PYTHON && "demo".equals(scriptName);
          }

          @Override
          public Reader open(SupportedLanguage language, String scriptName) throws IOException {
            return new StringReader(language.id() + ":" + scriptName);
          }
        };

    LanguageParser parser =
        new LanguageParser() {
          @Override
          public SupportedLanguage language() {
            return SupportedLanguage.JS;
          }

          @Override
          public ContractModel parse(ScriptDescriptor script, CodegenConfig config) {
            return new ContractModel(List.of());
          }
        };

    assertEquals(true, source.exists(SupportedLanguage.PYTHON, "demo"));
    try (BufferedReader reader =
        new BufferedReader(source.open(SupportedLanguage.PYTHON, "demo"))) {
      assertEquals("python:demo", reader.readLine());
    }
    assertEquals(SupportedLanguage.JS, parser.language());
    assertEquals(
        0,
        parser
            .parse(
                new ScriptDescriptor(SupportedLanguage.JS, "", "demo.js"), new CodegenConfig(false))
            .classes()
            .size());
  }
}
