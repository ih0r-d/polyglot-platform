package io.github.ih0rd.codegen.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

class PythonGenerationRegressionTest {
  private ContractClass parse(String body) {
    return new PythonContractParser()
        .parse(
            new ScriptDescriptor(
                SupportedLanguage.PYTHON, body + "\npolyglot.export_value('Api', Api)\n", "api.py"),
            new CodegenConfig(false))
        .classes()
        .getFirst();
  }

  @Test
  void statementsNestedScopesCommentsAndStringsDoNotBecomeMembers() {
    var contract =
        parse(
            """
        class Api:
            '''
            def from_string(self):
                pass
            '''
            # def from_comment(self): pass
            def hello(self):
                default = 1
                text = 'def from_text(self): pass'
                def nested():
                    return 42
                class Nested:
                    def member(self): pass
                return default
            class Inner:
                def from_inner(self): pass
            def goodbye(self):
                pass
        """);
    assertEquals(
        List.of("hello", "goodbye"), contract.methods().stream().map(m -> m.name()).toList());
  }

  static Stream<Arguments> positionalSignatures() {
    return Stream.of(
        Arguments.of("self, name: str", List.of("name")),
        Arguments.of("self, name: str = '#'", List.of("name")),
        Arguments.of("self, name: str, /, count: int = 1", List.of("name", "count")),
        Arguments.of("self, name: str, *, count: int = 1", List.of("name")),
        Arguments.of("self, name: str, *args, **kwargs", List.of("name")),
        Arguments.of("self, *args, optional=1, **kwargs", List.of()));
  }

  @ParameterizedTest
  @MethodSource("positionalSignatures")
  void generatesOnlyCallablePositionalParameters(String parameters, List<String> expected) {
    var method =
        parse("class Api:\n    def hello(" + parameters + ") -> str:\n        return 'ok'\n")
            .methods()
            .getFirst();
    assertEquals(expected, method.params().stream().map(p -> p.name()).toList());
  }

  @Test
  void rejectsRequiredKeywordOnlyParametersWithLocation() {
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                parse(
                    "class Api:\n    def hello(self, *, name: str) -> str:\n        return name\n"));
    assertTrue(failure.getMessage().contains("api.py"));
    assertTrue(failure.getMessage().contains("line 2"));
    assertTrue(failure.getMessage().contains("keyword-only"));
  }

  @Test
  void dictionaryFunctionsKeepExplicitSelfParameter() {
    var model =
        new PythonContractParser()
            .parse(
                new ScriptDescriptor(
                    SupportedLanguage.PYTHON,
                    "def hello(self: str, /, *, count=1): pass\npolyglot.export_value('Api', {'hello': hello})",
                    "api.py"),
                new CodegenConfig(false));
    assertEquals(
        "self", model.classes().getFirst().methods().getFirst().params().getFirst().name());
  }

  @Test
  void multilineSignaturePreservesHashInsideDefaultString() {
    var method =
        parse(
                "class Api:\n    def hello(\n        self,\n        name: str = '#,)', # comment\n        count: int = 1,\n    ) -> str:\n        return name\n")
            .methods()
            .getFirst();
    assertEquals(List.of("name", "count"), method.params().stream().map(p -> p.name()).toList());
  }

  @ParameterizedTest
  @ValueSource(strings = {"implementation", "make_api()"})
  void unsupportedExportTargetsFailInsteadOfGeneratingEmptyInterfaces(String target) {
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PythonContractParser()
                    .parse(
                        new ScriptDescriptor(
                            SupportedLanguage.PYTHON,
                            "def implementation(): pass\npolyglot.export_value('Api', "
                                + target
                                + ")",
                            "api.py"),
                        new CodegenConfig(false)));
    assertTrue(failure.getMessage().contains("api.py"));
    assertTrue(failure.getMessage().contains("non-interface export"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"def hello:", "def hello(self:", "def hello(self, x=):", "def hello(self, x, x):"})
  void malformedDeclarationsFailInsteadOfGeneratingEmptyMethods(String declaration) {
    assertThrows(
        IllegalArgumentException.class,
        () -> parse("class Api:\n    " + declaration + "\n        pass\n"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"set", "set[int]", "list[set[int]]", "dict[str, set[int]]"})
  void unsafeSetReturnMappingsFailInsteadOfChangingGeneratedTypes(String annotation) {
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                parse(
                    "class Api:\n    def hello(self) -> "
                        + annotation
                        + ":\n        return {1}\n"));
    assertTrue(failure.getMessage().contains("Python set"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{1, 2}", "set([1, 2])", "set()", "{x for x in range(3)}"})
  void unsafeInferredSetMappingsAlsoFail(String expression) {
    assertThrows(
        IllegalArgumentException.class,
        () -> parse("class Api:\n    def hello(self):\n        return " + expression + "\n"));
  }
}
