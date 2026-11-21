package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.codegen.parsers.JsContractParser;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

class DefaultContractGeneratorTest {
  private static final CodegenConfig CONFIG = new CodegenConfig(false);
  private static final ScriptDescriptor SCRIPT =
      new ScriptDescriptor(
          SupportedLanguage.PYTHON,
          "class Api:\n    def hello(self): pass\npolyglot.export_value('Api', Api)",
          "api.py");

  @Test
  void closesOwnedBatchesIdempotentlyAndRejectsLaterUse() {
    var batch = DefaultContractGenerator.openBatch();
    assertEquals("Api", batch.generate(SCRIPT, CONFIG).classes().getFirst().name());
    batch.close();
    batch.close();
    assertThrows(IllegalStateException.class, () -> batch.generate(SCRIPT, CONFIG));
  }

  @Test
  void closingAOneShotGeneratorDoesNotCloseOtherGenerators() {
    var first = new DefaultContractGenerator();
    first.close();
    try (var second = new DefaultContractGenerator()) {
      assertEquals("Api", second.generate(SCRIPT, CONFIG).classes().getFirst().name());
    }
  }

  @Test
  void missingAndUnsupportedLanguagesRetainActionableErrors() {
    try (var generator =
        new DefaultContractGenerator(Map.of(SupportedLanguage.JS, new JsContractParser()))) {
      var missing =
          assertThrows(IllegalStateException.class, () -> generator.generate(SCRIPT, CONFIG));
      assertTrue(missing.getMessage().contains("No parser registered"));
      assertThrows(
          UnsupportedOperationException.class,
          () ->
              generator.generate(new ScriptDescriptor(SupportedLanguage.JS, "", "api.js"), CONFIG));
    }
  }
}
