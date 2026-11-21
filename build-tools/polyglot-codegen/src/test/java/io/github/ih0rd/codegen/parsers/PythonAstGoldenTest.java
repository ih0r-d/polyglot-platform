package io.github.ih0rd.codegen.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.codegen.JavaInterfaceGenerator;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

class PythonAstGoldenTest {
  @Test
  void preservesPreMigrationGeneratedSourcesIncludingActualConsumerContracts() throws Exception {
    Path directory = Path.of("src/test/resources/python-ast/golden");
    try (var parser = PythonContractParser.openBatch();
        var paths = Files.list(directory)) {
      for (Path source : paths.filter(p -> p.toString().endsWith(".py")).sorted().toList()) {
        var descriptor =
            new ScriptDescriptor(
                SupportedLanguage.PYTHON, Files.readString(source), source.toString());
        var model =
            parser.parse(
                descriptor,
                new CodegenConfig(source.getFileName().toString().equals("included.py")));
        for (var type : model.classes()) {
          String expected =
              Files.readString(
                  directory.resolve(source.getFileName() + "." + type.name() + ".java.expected"));
          String actual = new JavaInterfaceGenerator().generate(type, "golden.contracts");
          assertEquals(expected, actual, source.toString());
          assertEquals(
              actual,
              new JavaInterfaceGenerator()
                  .generate(
                      parser
                          .parse(
                              descriptor,
                              new CodegenConfig(
                                  source.getFileName().toString().equals("included.py")))
                          .classes()
                          .stream()
                          .filter(c -> c.name().equals(type.name()))
                          .findFirst()
                          .orElseThrow(),
                      "golden.contracts"));
        }
      }
    }
  }
}
