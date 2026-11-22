package io.github.ih0rd.codegen.internal.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.ih0rd.codegen.parsers.PythonContractParser;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

class PythonAstSessionTest {
  @TempDir Path directory;

  static Stream<Arguments> corpus() throws Exception {
    var invalid =
        Set.of(
            "missing_colon",
            "unclosed_signature",
            "bad_indent",
            "unclosed_string",
            "bad_parameter_order",
            "bad_star",
            "bad_slash",
            "bad_expression",
            "pep750_not_312",
            "post312_default_typeparam");
    try (var paths = Files.list(Path.of("src/test/resources/python-ast/v1"))) {
      return paths
          .sorted()
          .map(
              path ->
                  Arguments.of(
                      path, !invalid.contains(path.getFileName().toString().replace(".py", ""))))
          .toList()
          .stream();
    }
  }

  @ParameterizedTest
  @MethodSource("corpus")
  void syntaxMatchesVersionedPython312Corpus(Path path, boolean valid) throws Exception {
    try (var session = new PythonAstSession()) {
      String source = Files.readString(path);
      // Keep fixtures LF-normalized in Git while testing actual CRLF input.
      if (path.getFileName().toString().equals("crlf.py")) source = source.replace("\n", "\r\n");
      var result = session.module(source, path.toString());
      assertEquals(valid, result.error() == null, path.toString());
    }
  }

  @Test
  void parsesAllExistingPythonFixturesWithoutImportingTheirDependencies() throws Exception {
    try (var session = new PythonAstSession()) {
      var root = Path.of("..", "..");
      for (String fixture :
          Files.readAllLines(Path.of("src/test/resources/python-ast/fixtures.txt"))) {
        var source = root.resolve(fixture);
        assertTrue(Files.isRegularFile(source), fixture);
        assertFalse(session.module(Files.readString(source), fixture).error() != null, fixture);
      }
    }
  }

  @Test
  void repeatingAStaticImportDoesNotRebindAContract() {
    try (var parser = PythonContractParser.openBatch()) {
      assertTrue(
          parser
              .parseBindings(
                  script("import polyglot\nimport polyglot\ndef hello(): pass"),
                  Convention.BY_METHOD_NAME)
              .complete());
    }
  }

  @Test
  void reportsSyntaxErrorPositionsAndKeepsSessionUsable() {
    try (var session = new PythonAstSession()) {
      var result = session.module("def f(x)\n    return x", "broken.py");
      assertEquals(1, result.error().line());
      assertEquals(9, result.error().column());
      assertTrue(result.error().message().contains(":"));
      assertNull(session.module("def f(x): pass", "valid.py").error());
    }
  }

  @Test
  void extractsNestedScopesAndAliasesWithoutExposingNestedMethods() {
    String source =
        "import polyglot as pg\nfrom typing import List as Sequence\ndef original(x: Sequence[int], /, *args, flag=True, **kwargs) -> int:\n    def nested(): return 1\n    return 1\nalias = original\npg.export_value('Api', {'hello': alias})\n";
    try (var session = new PythonAstSession()) {
      var root = session.module(source, "aliases.py").root();
      assertTrue(
          root.nodes("body").stream()
              .anyMatch(
                  node ->
                      node.is("FunctionDef")
                          && node.nodes("body").stream()
                              .anyMatch(child -> child.is("FunctionDef"))));
      var contract = PythonAstContracts.compatibility(session, script(source));
      assertTrue(contract.complete(), contract.diagnostics().toString());
      assertEquals(Set.of("Api.hello"), contract.functions().keySet());
      var function = contract.functions().get("Api.hello");
      assertEquals(1, function.required());
      assertEquals(Integer.MAX_VALUE, function.maximum());
      assertEquals("Sequence[int]", function.parameters().getFirst().annotation());
    }
  }

  @Test
  void parsesUnicodeAndComplexBodiesButKeepsAsyncAndDecoratorsUnknown() {
    try (var parser = PythonContractParser.openBatch()) {
      var model =
          parser.parse(
              script(
                  "class Api:\n    def café(self, π: int, /, *, flag=True) -> int:\n        match π:\n            case 1: return 1\n            case _: return 0\npolyglot.export_value('Api', Api)"),
              new CodegenConfig(false));
      assertEquals("café", model.classes().getFirst().methods().getFirst().name());
      assertEquals("π", model.classes().getFirst().methods().getFirst().params().getFirst().name());
      for (String source :
          List.of(
              "@unknown\ndef f(): pass", "async def f(): pass", "import unknown\ndef f(): pass")) {
        assertFalse(parser.parseCompatibility(script(source)).complete());
      }
    }
  }

  @Test
  void neverExecutesImportsDecoratorsAnnotationsDefaultsModuleStatementsOrBodies()
      throws Exception {
    Path marker = directory.resolve("executed");
    String expression = "open('" + marker + "', 'w').write('bad')";
    String source =
        "import nonexistent_application_module\n@decorate("
            + expression
            + ")\ndef f(x: "
            + expression
            + " = "
            + expression
            + "):\n    "
            + expression
            + "\n"
            + expression;
    try (var parser = PythonContractParser.openBatch()) {
      assertFalse(parser.parseCompatibility(script(source)).complete());
      assertFalse(parser.parseBindings(script(source), Convention.BY_METHOD_NAME).complete());
      assertThrows(
          IllegalStateException.class,
          () -> parser.parse(script(source), new CodegenConfig(false)));
    }
    assertFalse(Files.exists(marker));
  }

  @Test
  void batchesAreIsolatedAndClosedDeterministically() {
    var parser = PythonContractParser.openBatch();
    assertTrue(parser.parseBindings(script("def f(): pass"), Convention.BY_METHOD_NAME).complete());
    assertFalse(
        parser.parseCompatibility(script("polyglot.export_value('Api', {'f': f})")).complete());
    parser.close();
    parser.close();
    assertThrows(
        IllegalStateException.class, () -> parser.parseCompatibility(script("def f(): pass")));
    try (var other = PythonContractParser.openBatch()) {
      assertFalse(
          other.parseCompatibility(script("polyglot.export_value('Api', {'f': f})")).complete());
    }
    var session = new PythonAstSession();
    session.close();
    assertThrows(IllegalStateException.class, () -> session.literal("1"));
  }

  @Test
  void serializesConcurrentParsingAndAllowsCloseAfterWorkersFinish() throws Exception {
    try (var parser = PythonContractParser.openBatch();
        var workers = Executors.newFixedThreadPool(4)) {
      var tasks = new ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int i = 0; i < 20; i++) {
        int index = i;
        tasks.add(
            workers.submit(
                () ->
                    parser
                        .parseCompatibility(script("def f" + index + "(x): pass"))
                        .functions()
                        .containsKey("f" + index)));
      }
      for (var task : tasks) assertTrue(task.get(30, TimeUnit.SECONDS));
    }
  }

  @Test
  void isolatedInterpreterIgnoresMavenPropertiesAndApplicationPythonModules() throws Exception {
    Path marker = directory.resolve("executed");
    for (String name : List.of("ast.py", "sitecustomize.py"))
      Files.writeString(
          directory.resolve(name),
          "open('" + marker + "', 'w').write('bad')\nraise AssertionError()\n");
    runProbe(classpath(), "isolated");
    assertFalse(Files.exists(marker));
  }

  @Test
  void missingLanguageArtifactsFailWithAnActionableCauseAndSafeCleanup() throws Exception {
    String complete = classpath();
    String withoutPython =
        String.join(
            File.pathSeparator,
            Stream.of(complete.split(File.pathSeparator))
                .filter(
                    entry ->
                        !Path.of(entry).getFileName().toString().startsWith("python-language-"))
                .toList());
    assertNotEquals(complete, withoutPython);
    runProbe(withoutPython, "missing-language");
  }

  private static String classpath() {
    return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
  }

  private void runProbe(String classpath, String mode) throws Exception {
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    // Forked integration checks contribute to the same coverage report as the parent tests.
    ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .filter(arg -> arg.startsWith("-javaagent:") && arg.contains("org.jacoco.agent"))
        .forEach(command::add);
    command.addAll(
        List.of(
            "--enable-native-access=ALL-UNNAMED",
            "-Dpolyglot.codegen.validateContracts=true",
            "-Dpolyglot.python.IsolateFlag=false",
            "-cp",
            classpath,
            Probe.class.getName(),
            mode));
    var builder = new ProcessBuilder(command);
    builder.directory(directory.toFile());
    builder.environment().put("PYTHONPATH", directory.toString());
    Path output = directory.resolve(mode + ".log");
    builder.redirectErrorStream(true).redirectOutput(output.toFile());
    var process = builder.start();
    try {
      assertTrue(process.waitFor(30, TimeUnit.SECONDS));
      assertEquals(0, process.exitValue(), Files.readString(output));
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  public static final class Probe {
    public static void main(String[] args) {
      if (args.length > 0 && args[0].equals("missing-language")) {
        for (var parser : List.of(new PythonContractParser(), PythonContractParser.openBatch())) {
          try (parser) {
            try {
              parser.parseCompatibility(script("def f(): pass"));
              throw new AssertionError("Missing GraalPy must fail analysis");
            } catch (IllegalStateException failure) {
              if (!failure.getMessage().contains("Cannot initialize GraalPy AST analysis")
                  || failure.getCause() == null)
                throw new AssertionError(
                    "Initialization diagnostics must preserve the cause", failure);
            }
          }
        }
        return;
      }
      try (var parser = PythonContractParser.openBatch()) {
        if (!parser.parseCompatibility(script("def f(): pass")).complete())
          throw new AssertionError();
      }
    }
  }

  private static ScriptDescriptor script(String source) {
    return new ScriptDescriptor(SupportedLanguage.PYTHON, source, "contract.py");
  }
}
