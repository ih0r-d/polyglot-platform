package io.github.ih0rd.codegen.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

class PythonBindingMetadataTest {
  @Test
  void methodNameModeRetainsTopLevelFunctionsInModulesWithDictionaryExports() {
    var script =
        script("def hello(self, count=1): pass\npolyglot.export_value('Api', {'hello': hello})");
    var parser = new PythonContractParser();
    var binding = parser.parseBindings(script, Convention.BY_METHOD_NAME);
    assertTrue(binding.complete());
    assertEquals(1, binding.functions().get("hello").required());
    assertEquals(2, binding.functions().get("hello").maximum());
    assertEquals(
        java.util.Set.of("Api.hello"), parser.parseCompatibility(script).functions().keySet());
  }

  @Test
  void executablePolyglotAliasesWinOverLanguageFunctionsEvenAfterLaterDefinitions() {
    var script =
        script(
            "def implementation(x, y): pass\npolyglot.export_value('hello', implementation)\ndef hello(): pass");
    var parser = new PythonContractParser();
    var binding = parser.parseBindings(script, Convention.BY_METHOD_NAME);
    assertTrue(binding.complete());
    assertEquals(2, binding.functions().get("hello").required());
    assertFalse(parser.parseCompatibility(script).complete());
  }

  @Test
  void officialAstSupportsEscapedDefaultsAndRejectsDynamicSignatureExpressions() {
    var script = script("def hello(value='it\\'s,ok'): pass");
    var parser = new PythonContractParser();
    assertTrue(parser.parseBindings(script, Convention.BY_METHOD_NAME).complete());
    assertTrue(parser.parseCompatibility(script).complete());
    assertFalse(
        parser
            .parseBindings(script("def hello(x=make_value()): pass"), Convention.BY_METHOD_NAME)
            .complete());
  }

  @Test
  void nonCollidingImportsAfterDeclarationsPreserveDefaultValidation() {
    var script =
        script(
            "class Api:\n    def hello(self): pass\nimport polyglot\npolyglot.export_value('Api', Api)");
    var binding = new PythonContractParser().parseBindings(script, Convention.DEFAULT);
    assertTrue(binding.complete());
    assertTrue(binding.functions().containsKey("Api.hello"));
  }

  @Test
  void ambiguousGlobalRebindingsStayUnknown() {
    for (String source :
        java.util.List.of(
            "def hello(): pass\nclass hello:\n    pass",
            "class hello:\n    pass\ndef hello(): pass",
            "def Any(): pass\nfrom typing import Any",
            "from typing import Any\ndef Any(): pass",
            "from typing import List\nclass List:\n    pass",
            "def polyglot(): pass\nimport polyglot")) {
      var parser = new PythonContractParser();
      assertFalse(parser.parseCompatibility(script(source)).complete(), source);
      for (Convention convention : Convention.values()) {
        assertFalse(parser.parseBindings(script(source), convention).complete(), source);
      }
    }
  }

  private static ScriptDescriptor script(String source) {
    return new ScriptDescriptor(SupportedLanguage.PYTHON, source, "api.py");
  }
}
