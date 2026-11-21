package io.github.ih0rd.codegen.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.ih0rd.codegen.compatibility.CompatibilityDiagnostic.Category;
import io.github.ih0rd.codegen.parsers.PythonContractParser;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

class PythonCompatibilityCheckerTest {
  @TempDir Path directory;

  static Stream<Arguments> signatures() {
    return Stream.of(
        Arguments.of("x", "x, y=1", "COMPATIBLE"),
        Arguments.of("x=1", "x=1, y=2", "COMPATIBLE"),
        Arguments.of("x", "x, y", "BREAKING"),
        Arguments.of("x=1", "x", "BREAKING"),
        Arguments.of("x", "x=1", "COMPATIBLE"),
        Arguments.of("x, y=1", "x", "BREAKING"),
        Arguments.of("x, y", "x", "BREAKING"),
        Arguments.of("x", "renamed", "COMPATIBLE"),
        Arguments.of("x, /", "x", "COMPATIBLE"),
        Arguments.of("x", "x, /", "COMPATIBLE"),
        Arguments.of("x", "*, x", "BREAKING"),
        Arguments.of("x=1", "*, x=1", "BREAKING"),
        Arguments.of("x", "x, *, y=1", "COMPATIBLE"),
        Arguments.of("x", "x, *, y", "BREAKING"),
        Arguments.of("*, x", "x", "UNKNOWN"),
        Arguments.of("*, x=1", "", "COMPATIBLE"),
        Arguments.of("*, x=1", "x=1", "COMPATIBLE"),
        Arguments.of("*, x=1", "x=2", "UNKNOWN"),
        Arguments.of("x", "x, **kwargs", "COMPATIBLE"),
        Arguments.of("x, **kwargs", "x", "COMPATIBLE"),
        Arguments.of("x", "**kwargs", "BREAKING"),
        Arguments.of("x", "x, *args", "COMPATIBLE"),
        Arguments.of("x, *args", "x", "BREAKING"),
        Arguments.of("x, y=1", "x, *args", "UNKNOWN"),
        Arguments.of("*args", "x=1, *args", "COMPATIBLE"),
        Arguments.of("*args: int", "*args: str", "BREAKING"),
        Arguments.of("x, y=1", "y=1, x=2", "BREAKING"),
        Arguments.of("x, y=1", "x, z=2, y=1", "BREAKING"),
        Arguments.of("x=1", "x=2", "UNKNOWN"),
        Arguments.of("x=1", "x='text'", "UNKNOWN"),
        Arguments.of("*, x=1", "*, x=2", "UNKNOWN"),
        Arguments.of("x=make_value()", "x=make_value()", "UNKNOWN"),
        Arguments.of("x={'a': [1, 2]}", "x={'a': [1, 2]}", "UNKNOWN"),
        Arguments.of("x='a,b=)'", "x='a,b=)'", "COMPATIBLE"),
        Arguments.of("x: int", "x: str", "BREAKING"),
        Arguments.of("x: int", "x: float", "BREAKING"),
        Arguments.of("x: List[int]", "x: list[int]", "COMPATIBLE"),
        Arguments.of("x: dict[str, list[int]]", "x: Dict[str, List[int]]", "COMPATIBLE"),
        Arguments.of("x: list[int]", "x: list[str]", "BREAKING"),
        Arguments.of("x: Optional[int]", "x: int", "UNKNOWN"),
        Arguments.of("x: Any", "x: Any", "UNKNOWN"),
        Arguments.of("x=01", "x=01", "UNKNOWN"),
        Arguments.of("x", "x: int", "UNKNOWN"),
        Arguments.of("x: int", "x", "UNKNOWN"),
        Arguments.of("x: set[int]", "x: list[int]", "UNKNOWN"),
        Arguments.of("x: tuple[int, str]", "x: tuple[int, str]", "UNKNOWN"),
        Arguments.of("x: list[int, int]", "x: list[int]", "UNKNOWN"),
        Arguments.of("x: dict[int, str, int]", "x: dict[int, str]", "UNKNOWN"));
  }

  @ParameterizedTest
  @MethodSource("signatures")
  void comparesRealParsedSignatures(String baseline, String current, String expected)
      throws Exception {
    var report = compare(function(baseline), function(current));
    assertEquals(
        CompatibilityOutcome.valueOf(expected), report.outcome(), report.diagnostics().toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "def f(x, x): pass",
        "def f(x=1, y): pass",
        "def f(*): pass",
        "def f(/): pass",
        "def f(x, /, /): pass",
        "def f(**kwargs, x): pass",
        "def f(x=): pass",
        "def f(x:): pass",
        "def f(x=[1, 2): pass",
        "def f(x):",
        "def f(x=1,,): pass",
        "def f(x=lambda a,b: a): pass",
        "@decorator\ndef f(x): pass",
        "async def f(x): pass",
        "if flag:\n    def f(x): pass",
        "def f(x): yield x",
        "def f(x):\n    yield x",
        "def f(x): pass\nf = replacement",
        "class Api(Base):\n    pass",
        "text = '''\ndef f(x): pass\n'''",
        "def f(x): pass\ndef f(x,y): pass",
        "polyglot.export_value('Api', make_api())",
        "def f(**kwargs=1): pass",
        "def f(x, *, *args): pass"
      })
  void unsupportedAndMalformedSourcesAreNeverCompatible(String source) throws Exception {
    var report = compare(function("x"), source);
    assertEquals(CompatibilityOutcome.UNKNOWN, report.outcome(), report.diagnostics().toString());
    assertTrue(
        report.diagnostics().stream().anyMatch(d -> d.category() == Category.UNSUPPORTED_SYNTAX));
    assertTrue(
        report.diagnostics().stream().allMatch(d -> d.line() > 0 && d.file().endsWith("api.py")));
  }

  @Test
  void invalidClassAndFunctionNamesAndMissingClassBodyAreUnknown() throws Exception {
    for (String current : List.of("class Api:\n", "class None:\n    pass", "def for(x): pass")) {
      assertEquals(CompatibilityOutcome.UNKNOWN, compare(function("x"), current).outcome());
    }
  }

  @Test
  void returnAnnotationsUseExistingTypeMappingWithoutInferringBodies() throws Exception {
    assertEquals(
        CompatibilityOutcome.BREAKING,
        compare("def f() -> int: return 1", "def f() -> str: return 'a'").outcome());
    assertEquals(
        CompatibilityOutcome.UNKNOWN,
        compare("def f() -> int: return 1", "def f(): return 1").outcome());
    assertEquals(
        CompatibilityOutcome.COMPATIBLE,
        compare("def f(): return 1", "def f(): return 'a'").outcome());
  }

  @Test
  void additionsPassAndRemovalsAndRenamesFail() throws Exception {
    assertEquals(
        CompatibilityOutcome.COMPATIBLE,
        compare(function("x"), function("x") + "\ndef added(): pass").outcome());
    for (String current : List.of("", "# removed\n", "def renamed(x): pass")) {
      var report = compare(function("x"), current);
      assertEquals(CompatibilityOutcome.BREAKING, report.outcome());
      assertEquals(Category.FUNCTION_REMOVED, report.diagnostics().getFirst().category());
      assertEquals("f", report.diagnostics().getFirst().symbol());
      assertTrue(report.diagnostics().getFirst().file().contains("baseline"));
    }
  }

  @Test
  void classAliasesExcludeBoundReceiverRegardlessOfName() throws Exception {
    assertEquals(
        CompatibilityOutcome.COMPATIBLE,
        compare(exportedClass("receiver, x"), exportedClass("self, x, y=1")).outcome());
    var report = compare(exportedClass("receiver, x"), exportedClass("receiver, x, y"));
    assertEquals(CompatibilityOutcome.BREAKING, report.outcome());
    assertTrue(report.diagnostics().stream().allMatch(d -> d.symbol().equals("Api.f")));
    assertEquals(
        CompatibilityOutcome.COMPATIBLE,
        compare(exportedClass("self"), exportedClass("self, /")).outcome());
  }

  @Test
  void dictionaryAliasesReuseGenerationExportResolutionAndHideImplementationNames()
      throws Exception {
    String before = "def internal(x): pass\npolyglot.export_value('Api', {'f': internal})";
    String after = "def renamed(x, y=1): pass\npolyglot.export_value('Api', {'f': renamed})";
    assertEquals(CompatibilityOutcome.COMPATIBLE, compare(before, after).outcome());
    assertEquals(
        CompatibilityOutcome.BREAKING, compare(before, after.replace("'f'", "'g'")).outcome());
    assertEquals(
        CompatibilityOutcome.UNKNOWN,
        compare(before, after.replace("{'f': renamed}", "{'f': renamed, 'f': renamed}")).outcome());
  }

  @Test
  void multilineSignaturesCommentsAndNestedTypesRetainSourceLocation() throws Exception {
    var report =
        compare(
            "def f(\n    x: dict[str, list[int]], # parameter\n):\n    pass\n",
            "# current\ndef f(\n    x: dict[str, list[str]],\n):\n    pass\n");
    assertEquals(CompatibilityOutcome.BREAKING, report.outcome());
    assertEquals(2, report.diagnostics().getFirst().line());
  }

  @Test
  void modelPreservesCallingConventionsAndDefaults() {
    var contract =
        new PythonContractParser()
            .parseCompatibility(
                new ScriptDescriptor(
                    SupportedLanguage.PYTHON,
                    "def f(a, /, b=1, *args, c=2, **kwargs): pass",
                    "api.py"));
    assertTrue(contract.complete());
    assertEquals(
        List.of(
            PythonContract.Kind.POSITIONAL_ONLY,
            PythonContract.Kind.POSITIONAL_OR_KEYWORD,
            PythonContract.Kind.VAR_POSITIONAL,
            PythonContract.Kind.KEYWORD_ONLY,
            PythonContract.Kind.VAR_KEYWORD),
        contract.functions().get("f").parameters().stream()
            .map(PythonContract.Parameter::kind)
            .toList());
    assertEquals("1", contract.functions().get("f").parameters().get(1).defaultValue());
  }

  @Test
  void multipleModulesFunctionsAndStableDiagnosticOrdering() throws Exception {
    Path baseline = Files.createDirectories(directory.resolve("baseline"));
    Path current = Files.createDirectories(directory.resolve("current"));
    Files.createDirectories(baseline.resolve("nested"));
    Files.createDirectories(current.resolve("nested"));
    Files.writeString(baseline.resolve("z.py"), "def z(): pass\ndef a(): pass");
    Files.writeString(current.resolve("z.py"), "def z(x): pass\ndef a(y): pass");
    Files.writeString(baseline.resolve("nested/a.py"), function("x"));
    Files.writeString(current.resolve("nested/a.py"), function("x, y"));
    var checker = new PythonCompatibilityChecker();
    var report = checker.compare(baseline, current);
    assertEquals(3, report.diagnostics().size());
    assertEquals(report, checker.compare(baseline, current));
    assertTrue(report.diagnostics().getFirst().file().endsWith("nested/a.py"));
    Files.delete(current.resolve("nested/a.py"));
    assertTrue(
        checker.compare(baseline, current).diagnostics().stream()
            .anyMatch(d -> d.category() == Category.MODULE_REMOVED));
    Files.writeString(current.resolve("new.py"), "def added(): pass");
    assertEquals(3, checker.compare(baseline, current).diagnostics().size());
  }

  @Test
  void explicitBaselineFileSelectsOnlyItsCounterpart() throws Exception {
    compare(function("x"), function("x"));
    Files.writeString(directory.resolve("current/unrelated.py"), "@dynamic\ndef f(): pass");
    assertEquals(
        CompatibilityOutcome.COMPATIBLE,
        new PythonCompatibilityChecker()
            .compare(directory.resolve("baseline/api.py"), directory.resolve("current"))
            .outcome());
  }

  @Test
  void refusesMissingOrEmptyBaselinesAndInvalidCurrentDirectory() throws Exception {
    var checker = new PythonCompatibilityChecker();
    assertThrows(
        IllegalArgumentException.class,
        () -> checker.compare(directory.resolve("missing"), directory));
    assertThrows(IllegalArgumentException.class, () -> checker.compare(directory, directory));
    Path baseline = Files.writeString(directory.resolve("empty.py"), "# no contract");
    assertThrows(IllegalArgumentException.class, () -> checker.compare(baseline, directory));
    assertThrows(IllegalArgumentException.class, () -> checker.compare(baseline, baseline));
  }

  @Test
  void neverExecutesPythonAndReturnsImmutableReports() throws Exception {
    Path marker = directory.resolve("executed");
    String source =
        "def f(x):\n    open('" + marker + "', 'w').write('bad')\n    raise AssertionError()";
    var report = compare(source, source);
    assertEquals(CompatibilityOutcome.COMPATIBLE, report.outcome());
    assertFalse(Files.exists(marker));
    assertThrows(UnsupportedOperationException.class, () -> report.diagnostics().clear());
  }

  private CompatibilityReport compare(String before, String after) throws Exception {
    Path baseline = Files.createDirectories(directory.resolve("baseline"));
    Path current = Files.createDirectories(directory.resolve("current"));
    Files.writeString(baseline.resolve("api.py"), before);
    Files.writeString(current.resolve("api.py"), after);
    return new PythonCompatibilityChecker().compare(baseline, current);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "def f(x): pass\nclass f:\n    pass",
        "class f:\n    pass\ndef f(x): pass",
        "def Any(x): pass\nfrom typing import Any",
        "from typing import Any\ndef Any(x): pass",
        "class Any:\n    pass\nfrom typing import Any",
        "from typing import Any\nclass Any:\n    pass",
        "def polyglot(x): pass\nimport polyglot"
      })
  void rebindingNeverProducesCompatibleReports(String source) throws Exception {
    var report = compare(source, source);
    assertEquals(CompatibilityOutcome.UNKNOWN, report.outcome());
    assertTrue(
        report.diagnostics().stream().anyMatch(d -> d.category() == Category.UNSUPPORTED_SYNTAX));
    var parser = new PythonContractParser();
    for (var convention : io.github.ih0rd.polyglot.Convention.values()) {
      assertFalse(
          parser
              .parseBindings(
                  new ScriptDescriptor(SupportedLanguage.PYTHON, source, "api.py"), convention)
              .complete());
    }
  }

  private static String function(String parameters) {
    return "def f(" + parameters + "): pass\n";
  }

  private static String exportedClass(String parameters) {
    return "class Implementation:\n    def f("
        + parameters
        + "): pass\npolyglot.export_value('Api', Implementation)";
  }
}
