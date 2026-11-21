package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

class MethodNameContractValidationTest {
  @TempDir Path directory;

  static Stream<Arguments> signatures() {
    return Stream.of(
        Arguments.of("", "String hello();", true),
        Arguments.of("self", "String hello();", false),
        Arguments.of("self", "String hello(String value);", true),
        Arguments.of("x, y=1", "String hello(String x); String hello(String x, int y);", true),
        Arguments.of("x, y=1", "String hello();", false),
        Arguments.of("x, y=1", "String hello(String x, int y, int z);", false),
        Arguments.of("x, /, y=1", "String hello(String x);", true),
        Arguments.of("x, /, y=1", "String hello(String x, int y);", true),
        Arguments.of("x, *, option=1", "String hello(String x);", true),
        Arguments.of("x, *, option=1", "String hello(String x, int option);", false),
        Arguments.of("x, *, option", "String hello(String x);", false),
        Arguments.of("*args", "String hello(); String hello(String a, String b);", true),
        Arguments.of("x, *args, option=1", "String hello(String x, int a, int b);", true),
        Arguments.of("x, *args, option=1", "String hello();", false),
        Arguments.of("**kwargs", "String hello();", true),
        Arguments.of("**kwargs", "String hello(String x);", false),
        Arguments.of("x, **kwargs", "String hello(String x);", true),
        Arguments.of("x, **kwargs", "String hello(String x, int extra);", false),
        Arguments.of("x", "String hello(String... x);", true),
        Arguments.of("x, y", "String hello(String... x);", false),
        Arguments.of(
            "x: dict[str, list[int]]={'a': [1, 2]}, point=(1, 2)",
            "String hello(); String hello(Object x, Object p);",
            true),
        Arguments.of("text='a,b=#)'", "String hello();", true));
  }

  @ParameterizedTest
  @MethodSource("signatures")
  void matchesPositionalCallsForMethodNameAndBoundDefault(
      String parameters, String methods, boolean valid) throws Exception {
    for (boolean bound : List.of(false, true)) {
      String source =
          bound
              ? "class GreetingApi:\n    def hello(self"
                  + (parameters.isEmpty() ? "" : ", " + parameters.replaceFirst("^self$", "value"))
                  + "):\n        pass\npolyglot.export_value('GreetingApi', GreetingApi)"
              : "def hello(" + parameters + "):\n    pass\n";
      configured(source, methods, bound);
      if (valid) assertDoesNotThrow(this::validate);
      else
        assertTrue(
            assertThrows(IllegalArgumentException.class, this::validate)
                .getMessage()
                .contains("INCOMPATIBLE"));
    }
  }

  @Test
  void languageFunctionsNeedNoExportAndMissingFunctionsAreConfirmed() throws Exception {
    configured("def other(): pass", "String hello();", false);
    String message = assertThrows(IllegalArgumentException.class, this::validate).getMessage();
    for (String expected :
        List.of(
            "GreetingApi.java:5",
            "GreetingApi.hello",
            "greeting_api.py:1",
            "INCOMPATIBLE: MISSING_FUNCTION",
            "BY_METHOD_NAME")) {
      assertTrue(message.contains(expected), message);
    }
  }

  @Test
  void namedExportAliasHasRuntimePrecedenceOverLanguageBinding() throws Exception {
    configured(
        "def implementation(x, y): pass\npolyglot.export_value('hello', implementation)\ndef hello(): pass",
        "String hello(String a, String b);",
        false);
    assertDoesNotThrow(this::validate);
    configured(
        "def implementation(x, y): pass\npolyglot.export_value('hello', implementation)\ndef hello(): pass",
        "String hello();",
        false);
    String message = assertThrows(IllegalArgumentException.class, this::validate).getMessage();
    assertTrue(message.contains("Python accepts 2..2"), message);
    assertTrue(message.contains("greeting_api.py:1"), message);
  }

  @Test
  void dictionaryExportsPreserveFullFunctionArityWithoutAnImplicitReceiver() throws Exception {
    configured(
        "def implementation(self, count=1): pass\npolyglot.export_value('GreetingApi', {'hello': implementation})",
        "String hello(String self);",
        true);
    assertDoesNotThrow(this::validate);
  }

  @Test
  void unsupportedSourcesAreUnknownAndNeverExecutePython() throws Exception {
    for (String source :
        List.of(
            "@decorator\ndef hello(): pass",
            "async def hello(): pass",
            "def hello(x=make_value()): pass",
            "def hello(x='text'()): pass",
            "def hello(x=lambda y: y): pass",
            "def hello(x, x): pass",
            "def hello(x=1, y): pass",
            "if flag:\n    def hello(): pass",
            "def hello(): pass\nhello = other",
            "polyglot.export_value('hello', factory())",
            "def hello(x=f'{1}'): pass",
            "def hello() -> resolve_type(): pass",
            "def hello(x):\n    def nested(): pass")) {
      configured(source, "String hello();", false);
      String message = assertThrows(IllegalArgumentException.class, this::validate).getMessage();
      for (String expected : List.of("UNKNOWN", "greeting_api.py:", "GreetingApi.java:")) {
        assertTrue(message.contains(expected), message);
      }
    }
    Path marker = directory.resolve("executed");
    configured(
        "def hello():\n    open('" + marker + "', 'w').write('bad')\n    raise AssertionError()",
        "String hello();",
        false);
    assertDoesNotThrow(this::validate);
    assertFalse(Files.exists(marker));
  }

  @Test
  void diagnosticsAreStableAndPointToMultilineDeclaration() throws Exception {
    configured(
        "# source\ndef hello(\n    x, # required\n    y=1,\n):\n    pass",
        "String hello();",
        false);
    String first = assertThrows(IllegalArgumentException.class, this::validate).getMessage();
    assertEquals(first, assertThrows(IllegalArgumentException.class, this::validate).getMessage());
    assertTrue(first.contains("greeting_api.py:2"), first);
    assertTrue(first.contains("Python accepts 1..2"), first);
  }

  @Test
  void absentDefaultExportIsUnknownRatherThanProofThatRuntimeFallbackFails() throws Exception {
    configured("class GreetingApi:\n    def hello(self):\n        pass", "String hello();", true);
    String message = assertThrows(IllegalArgumentException.class, this::validate).getMessage();
    assertTrue(message.contains("UNKNOWN: EXPORT_RESOLUTION"), message);
    assertTrue(message.contains("Missing export 'GreetingApi'"), message);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void explicitScriptReplacesOnlyModuleResolution(boolean bound) throws Exception {
    configured(
        bound
            ? "class GreetingApi:\n    def hello(self): pass\npolyglot.export_value('GreetingApi', GreetingApi)"
            : "def hello(): pass",
        "String hello();",
        bound);
    Path source = directory.resolve("GreetingApi.java");
    Files.writeString(
        source,
        Files.readString(source)
            .replace("languages=SupportedLanguage.PYTHON, ", "script=\"contracts/other_name\", "));
    Path script = Files.createDirectory(directory.resolve("contracts")).resolve("other_name.py");
    Files.move(directory.resolve("greeting_api.py"), script);
    assertDoesNotThrow(this::validate);

    Files.delete(script);
    String missing = assertThrows(IllegalArgumentException.class, this::validate).getMessage();
    assertTrue(missing.contains("contracts/other_name.py"), missing);
    assertTrue(missing.contains("INCOMPATIBLE: MISSING_MODULE"), missing);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"../unsafe\"", "\"contracts/pricing.py\"", "SCRIPT"})
  void unsafeOrUnresolvedScriptMappingsAreSourceLocated(String value) throws Exception {
    configured("def hello(): pass", "String hello();", false);
    Path source = directory.resolve("GreetingApi.java");
    Files.writeString(
        source, Files.readString(source).replace("languages=", "script=" + value + ", languages="));
    String message = assertThrows(IllegalArgumentException.class, this::validate).getMessage();
    assertTrue(message.contains("GreetingApi.java:4:"), message);
    assertTrue(
        message.contains(value.equals("SCRIPT") ? "UNKNOWN: SCRIPT" : "INVALID: SCRIPT"), message);
  }

  private void configured(String python, String methods, boolean bound) throws Exception {
    Files.writeString(
        directory.resolve("GreetingApi.java"),
        "import io.github.ih0rd.polyglot.annotations.PolyglotClient;\n"
            + "import io.github.ih0rd.polyglot.SupportedLanguage;\nimport io.github.ih0rd.polyglot.Convention;\n"
            + "@PolyglotClient(languages=SupportedLanguage.PYTHON, convention=Convention."
            + (bound ? "DEFAULT" : "BY_METHOD_NAME")
            + ")\ninterface GreetingApi { "
            + methods
            + " }\n");
    Files.writeString(directory.resolve("greeting_api.py"), python);
  }

  private void validate() throws Exception {
    PythonBindingValidator.validate(directory.resolve("GreetingApi.java"), directory);
  }
}
