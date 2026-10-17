package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolyglotValidateContractsMojoTest {
  @TempDir Path directory;

  @Test
  void validContractDoesNotExecutePython() throws Exception {
    Path marker = directory.resolve("executed");
    var mojo =
        configured(
            """
        import polyglot
        class GreetingApi:
            def hello(self):
                open('%s', 'w').write('executed')
                raise AssertionError('must not execute')
        polyglot.export_value('GreetingApi', GreetingApi)
        """
                .formatted(marker));
    assertDoesNotThrow(mojo::execute);
    assertFalse(Files.exists(marker));
  }

  @Test
  void disabledByDefaultEvenWithoutConfiguration() {
    assertDoesNotThrow(new PolyglotValidateContractsMojo()::execute);
  }

  @Test
  void enabledValidationRequiresExplicitContracts() throws Exception {
    var mojo = new PolyglotValidateContractsMojo();
    set(mojo, "validateContracts", true);
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute).getMessage().contains("contracts"));
  }

  @Test
  void missingModuleIncludesContractModuleSymbolAndPath() throws Exception {
    var mojo = configured("class GreetingApi:\n    pass\n");
    Files.delete(directory.resolve("greeting_api.py"));
    String message = assertThrows(MojoFailureException.class, mojo::execute).getMessage();
    for (String part :
        List.of("GreetingApi.java", "greeting_api", "GreetingApi", "greeting_api.py")) {
      assertTrue(message.contains(part), message);
    }
  }

  @Test
  void missingMethodIncludesJavaLocationAndPythonSource() throws Exception {
    var mojo =
        configured(
            """
        class GreetingApi:
            def other(self):
                pass
        polyglot.export_value('GreetingApi', GreetingApi)
        """);
    String message = assertThrows(MojoFailureException.class, mojo::execute).getMessage();
    for (String part :
        List.of("GreetingApi.java:2", "greeting_api", "GreetingApi.hello", "greeting_api.py")) {
      assertTrue(message.contains(part), message);
    }
  }

  @Test
  void missingExportFails() throws Exception {
    var mojo = configured("class GreetingApi:\n    def hello(self):\n        pass\n");
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("No polyglot.export_value"));
  }

  @Test
  void unsupportedDynamicConstructsFailRatherThanPass() throws Exception {
    for (String source :
        List.of(
            "polyglot.export_value('GreetingApi', make_api())\n",
            "class GreetingApi:\n    def __init__(self):\n        self.hello = None\n",
            "class GreetingApi(Base):\n    pass\n",
            "if enabled:\n    class GreetingApi:\n        pass\n",
            "class GreetingApi:\n    @property\n    def hello(self):\n        pass\n",
            "class GreetingApi:\n    def hello(self):\n        def nested():\n            pass\n",
            "text = \"\"\"\nclass GreetingApi:\n    def hello(self):\n        pass\n\"\"\"\n",
            "class GreetingApi:\n    def hello(self):\n        pass\nGreetingApi.hello = None\n")) {
      var mojo = configured(source);
      String message = assertThrows(MojoFailureException.class, mojo::execute).getMessage();
      assertTrue(message.contains("Unsupported static Python contract"), message);
      assertTrue(message.contains("greeting_api.py:"), message);
    }
  }

  @Test
  void unsupportedJavaContractsAndConventionsFail() throws Exception {
    for (String java :
        List.of(
            "interface GreetingApi extends Parent { String hello(); }",
            "@PolyglotClient(languages = SupportedLanguage.PYTHON, convention = Convention.BY_METHOD_NAME) interface GreetingApi { String hello(); }",
            "@PolyglotClient(languages = SupportedLanguage.JS) interface GreetingApi { String hello(); }",
            "@PolyglotClient interface GreetingApi { String hello(); }")) {
      var mojo = configured(validPython());
      Files.writeString(directory.resolve("GreetingApi.java"), java);
      assertTrue(
          assertThrows(MojoFailureException.class, mojo::execute)
              .getMessage()
              .contains("Unsupported"));
    }
  }

  @Test
  void invalidJavaSourceFailsWithLocation() throws Exception {
    var mojo = configured(validPython());
    Files.writeString(directory.resolve("GreetingApi.java"), "interface GreetingApi { not Java");
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("Cannot parse Java contract"));
  }

  @Test
  void wrongLiteralExportDoesNotValidateTheJavaContract() throws Exception {
    var mojo = configured(validPython().replace("'GreetingApi'", "'OtherApi'"));
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("Missing export 'GreetingApi'"));
  }

  @Test
  void underscorePrefixedMethodIsUnsupportedRatherThanReportedMissing() throws Exception {
    var mojo = configured(validPython().replace("hello", "_hello"));
    Files.writeString(
        directory.resolve("GreetingApi.java"), "interface GreetingApi { String _hello(); }");
    String message = assertThrows(MojoFailureException.class, mojo::execute).getMessage();
    assertTrue(message.contains("Unsupported static contract"), message);
    assertFalse(message.contains("Missing Python method"), message);
  }

  @Test
  void publicStaticMethodsRemainRequiredLikeRuntimeValidation() throws Exception {
    var mojo = configured(validPython());
    Files.writeString(
        directory.resolve("GreetingApi.java"),
        "interface GreetingApi { static String missing() { return null; } }");
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("GreetingApi.missing"));
  }

  @Test
  void explicitPythonAnnotationAndInterfaceExportConventionPass() throws Exception {
    var mojo = configured(validPython());
    Files.writeString(
        directory.resolve("GreetingApi.java"),
        """
        @PolyglotClient(languages = {SupportedLanguage.PYTHON}, convention = Convention.BY_INTERFACE_EXPORT)
        interface GreetingApi { String hello(); }
        """);
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void acronymNamingMatchesRuntime() throws Exception {
    var mojo = configured(validPython());
    Files.writeString(
        directory.resolve("GreetingApi.java"), "interface HTTPApi { String hello(); }");
    Files.writeString(
        directory.resolve("h_t_t_p_api.py"), validPython().replace("GreetingApi", "HTTPApi"));
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void matchingRequiredInstanceArgumentsPass() throws Exception {
    var mojo = configured(pythonMethod("self, name: str, count: int"));
    javaMethods("String hello(String name, int count);");
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void defaultsAcceptEveryArityInTheRangeIncludingOverloads() throws Exception {
    var mojo = configured(pythonMethod("self, name: str, count: int = 2, label: str = 'a,b=)'"));
    javaMethods(
        "String hello(String name); String hello(String name, int count); String hello(String name, int count, String label);");
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void tooFewArgumentsReportBothLocationsAndAcceptedRange() throws Exception {
    var mojo = configured(pythonMethod("self, name, count=2"));
    String message = assertThrows(MojoFailureException.class, mojo::execute).getMessage();
    for (String part :
        List.of(
            "GreetingApi.java:2",
            "module 'greeting_api'",
            "GreetingApi.hello",
            "Java supplies 0",
            "Python accepts 1..2",
            "greeting_api.py:2")) {
      assertTrue(message.contains(part), message);
    }
  }

  @Test
  void tooManyArgumentsFail() throws Exception {
    var mojo = configured(pythonMethod("self, name, count=2"));
    javaMethods("String hello(String name, int count, String extra);");
    String message = assertThrows(MojoFailureException.class, mojo::execute).getMessage();
    assertTrue(message.contains("Java supplies 3"), message);
    assertTrue(message.contains("Python accepts 1..2"), message);
  }

  @Test
  void receiverNameAndAnnotationDoNotChangeBoundArgumentCount() throws Exception {
    for (String parameters :
        List.of("self", "self: GreetingApi", "receiver", "receiver: GreetingApi")) {
      var mojo = configured(pythonMethod(parameters));
      assertDoesNotThrow(mojo::execute);
    }
  }

  @Test
  void nestedAnnotationsAndDefaultsDoNotCreateExtraParameters() throws Exception {
    var mojo =
        configured(
            pythonMethod(
                "self, values: dict[str, list[int]] = {'a': [1, 2]}, point = (1, 2), text = 'it\\'s,ok'"));
    assertDoesNotThrow(mojo::execute);
    javaMethods("String hello(Object values, Object point, String text);");
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void javaArrayAndVarargsEachSupplyOneGuestArgument() throws Exception {
    for (String parameter : List.of("String[] values", "String... values")) {
      var mojo = configured(pythonMethod("self, values"));
      javaMethods("String hello(" + parameter + ");");
      assertDoesNotThrow(mojo::execute);
    }
  }

  @Test
  void multipleMethodsAreCheckedIndependently() throws Exception {
    var mojo =
        configured(
            """
        class GreetingApi:
            def hello(self, name):
                raise AssertionError('must not execute')
            def count(self, limit=5):
                return limit
        polyglot.export_value('GreetingApi', GreetingApi)
        """);
    javaMethods("String hello(String name); int count();");
    assertDoesNotThrow(mojo::execute);
    javaMethods("String hello(String name); int count(int limit, int extra);");
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("GreetingApi.count"));
  }

  @Test
  void exportAliasesUseTheTargetClassSignature() throws Exception {
    var mojo =
        configured(
            pythonMethod("self, name")
                .replace("class GreetingApi:", "class Implementation:")
                .replace("'GreetingApi', GreetingApi", "'GreetingApi', Implementation"));
    javaMethods("String hello(String name);");
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void unsupportedSignaturesNeverPass() throws Exception {
    for (String parameters :
        List.of(
            "self, *args",
            "self, **kwargs",
            "self, *, name",
            "self, name, /",
            "",
            "self, first=1, second",
            "self, first, first",
            "self, value=lambda x,y: x",
            "self, value=make_default(1, 2)",
            "self: resolve_receiver()",
            "self, text=f'{1, 2}'",
            "self,, value",
            "self, value=",
            "self, value=[1,2",
            "self, value='unterminated")) {
      var mojo = configured(pythonMethod(parameters));
      String message = assertThrows(MojoFailureException.class, mojo::execute).getMessage();
      assertTrue(message.contains("Unsupported static Python signature"), message);
      assertTrue(message.contains("greeting_api.py:2"), message);
    }
  }

  @Test
  void dynamicReturnAnnotationsDoNotProduceASignatureMatch() throws Exception {
    var mojo =
        configured(
            pythonMethod("self").replace("def hello(self):", "def hello(self) -> mutate():"));
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("Unsupported static Python signature"));
  }

  @Test
  void parserFalsePositiveCannotProduceASignatureMatch() throws Exception {
    var mojo =
        configured(
            """
        class GreetingApi:
            def hello(self):
                default = 1
        polyglot.export_value('GreetingApi', GreetingApi)
        """);
    javaMethods("String ault();");
    String message = assertThrows(MojoFailureException.class, mojo::execute).getMessage();
    assertTrue(message.contains("GreetingApi.ault"), message);
    assertTrue(message.contains("Missing Python method"), message);
  }

  @Test
  void disabledValidationSkipsSignatureFailures() throws Exception {
    var mojo = configured(pythonMethod("self, *args"));
    set(mojo, "validateContracts", false);
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void discoversAnnotatedContractWithoutExplicitList() throws Exception {
    var mojo = discovered(validPython());
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void discoveryIgnoresUnrelatedInterfacesAnnotationsAndJava21Records() throws Exception {
    var mojo = discovered(validPython());
    Files.writeString(
        directory.resolve("sources/Unrelated.java"), "interface Unrelated { String missing(); }");
    Files.writeString(
        directory.resolve("sources/Foreign.java"),
        "import other.PolyglotClient; @PolyglotClient interface Foreign { String missing(); }");
    Files.writeString(directory.resolve("sources/Data.java"), "record Data(String name) {}");
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void discoveryChecksMultipleContractsIncludingFullyQualifiedAnnotation() throws Exception {
    var mojo = discovered(validPython());
    Files.writeString(
        directory.resolve("sources/OtherApi.java"),
        "@io.github.ih0rd.polyglot.annotations.PolyglotClient(languages = io.github.ih0rd.polyglot.SupportedLanguage.PYTHON) interface OtherApi { String hello(); }");
    Files.writeString(
        directory.resolve("other_api.py"), validPython().replace("GreetingApi", "OtherApi"));
    assertDoesNotThrow(mojo::execute);
    Files.delete(directory.resolve("other_api.py"));
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("Missing Python module 'other_api'"));
  }

  @Test
  void discoveryRetainsMissingModuleMethodAndArityDiagnostics() throws Exception {
    var mojo = discovered(validPython());
    Files.delete(directory.resolve("greeting_api.py"));
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("Missing Python module"));
    Files.writeString(
        directory.resolve("greeting_api.py"), validPython().replace("hello", "other"));
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("GreetingApi.hello"));
    Files.writeString(directory.resolve("greeting_api.py"), pythonMethod("self, required"));
    String message = assertThrows(MojoFailureException.class, mojo::execute).getMessage();
    assertTrue(message.contains("GreetingApi.java:3"), message);
    assertTrue(message.contains("Python accepts 1..1"), message);
  }

  @Test
  void explicitContractsTakePrecedenceOverDiscovery() throws Exception {
    var mojo = configured(validPython());
    set(mojo, "discoverContracts", true);
    set(mojo, "sourceDirectory", directory.resolve("does-not-exist").toFile());
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void discoveryIsOptInAndValidationRemainsDisabledByDefault() throws Exception {
    var mojo = new PolyglotValidateContractsMojo();
    set(mojo, "discoverContracts", true);
    assertDoesNotThrow(mojo::execute);
    var explicit = configured(validPython());
    set(explicit, "contracts", List.of());
    set(explicit, "sourceDirectory", directory.toFile());
    assertTrue(
        assertThrows(MojoFailureException.class, explicit::execute)
            .getMessage()
            .contains("Configure contracts"));
  }

  @Test
  void discoveryFailsWhenSourceRootMissingOrNoContractsFound() throws Exception {
    var mojo = discovered(validPython());
    set(mojo, "sourceDirectory", directory.resolve("absent").toFile());
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("sourceDirectory is not a directory"));
    set(mojo, "sourceDirectory", directory.resolve("sources").toFile());
    Files.writeString(directory.resolve("sources/GreetingApi.java"), "interface GreetingApi {}");
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("No @PolyglotClient"));
  }

  @Test
  void discoveredUnsupportedContractsFailRatherThanBeingSkipped() throws Exception {
    for (String source :
        List.of(
            "import io.github.ih0rd.polyglot.annotations.PolyglotClient; @PolyglotClient interface GreetingApi {}",
            "import io.github.ih0rd.polyglot.annotations.PolyglotClient; @PolyglotClient(languages=SupportedLanguage.JS) interface GreetingApi {}",
            "import io.github.ih0rd.polyglot.annotations.PolyglotClient; @PolyglotClient(languages=SupportedLanguage.PYTHON, convention=Convention.BY_METHOD_NAME) interface GreetingApi {}",
            "import io.github.ih0rd.polyglot.annotations.PolyglotClient; @PolyglotClient(languages=SupportedLanguage.PYTHON) class GreetingApi {}",
            "import io.github.ih0rd.polyglot.annotations.PolyglotClient; class Outer { @PolyglotClient(languages=SupportedLanguage.PYTHON) interface GreetingApi {} }",
            "import io.github.ih0rd.polyglot.annotations.PolyglotClient; @PolyglotClient(languages=SupportedLanguage.PYTHON) interface GreetingApi extends Parent {}")) {
      var mojo = discovered(validPython());
      Files.writeString(directory.resolve("sources/GreetingApi.java"), source);
      assertTrue(
          assertThrows(MojoFailureException.class, mojo::execute)
              .getMessage()
              .contains("Unsupported"));
    }
  }

  @Test
  void ambiguousWildcardOrShadowedAnnotationsAreUnsupported() throws Exception {
    for (String source :
        List.of(
            "import io.github.ih0rd.polyglot.annotations.*; @PolyglotClient(languages=SupportedLanguage.PYTHON) interface GreetingApi {}",
            "import io.github.ih0rd.polyglot.annotations.PolyglotClient; @PolyglotClient interface GreetingApi { @interface PolyglotClient {} }")) {
      var mojo = discovered(validPython());
      Files.writeString(directory.resolve("sources/GreetingApi.java"), source);
      assertTrue(
          assertThrows(MojoFailureException.class, mojo::execute)
              .getMessage()
              .contains("Unsupported ambiguous"));
    }
  }

  @Test
  void invalidJavaSourcesFailDiscoveryWithTheirPath() throws Exception {
    var mojo = discovered(validPython());
    Files.writeString(directory.resolve("sources/Broken.java"), "not valid Java");
    String message = assertThrows(MojoFailureException.class, mojo::execute).getMessage();
    assertTrue(message.contains("Broken.java"), message);
    assertTrue(message.contains("Cannot discover"), message);
  }

  private PolyglotValidateContractsMojo discovered(String python) throws Exception {
    var mojo = configured(python);
    Path sources = Files.createDirectories(directory.resolve("sources"));
    Files.writeString(
        sources.resolve("GreetingApi.java"),
        """
        import io.github.ih0rd.polyglot.annotations.PolyglotClient;
        @PolyglotClient(languages = SupportedLanguage.PYTHON)
        interface GreetingApi { String hello(); }
        """);
    set(mojo, "contracts", List.of());
    set(mojo, "discoverContracts", true);
    set(mojo, "sourceDirectory", sources.toFile());
    return mojo;
  }

  private String pythonMethod(String parameters) {
    return "class GreetingApi:\n    def hello("
        + parameters
        + "):\n        raise AssertionError('must not execute')\npolyglot.export_value('GreetingApi', GreetingApi)\n";
  }

  private void javaMethods(String methods) throws Exception {
    Files.writeString(
        directory.resolve("GreetingApi.java"), "interface GreetingApi {\n    " + methods + "\n}\n");
  }

  private String validPython() {
    return "class GreetingApi:\n    def hello(self):\n        pass\npolyglot.export_value('GreetingApi', GreetingApi)\n";
  }

  private PolyglotValidateContractsMojo configured(String python) throws Exception {
    Path java = directory.resolve("GreetingApi.java");
    Files.writeString(java, "interface GreetingApi {\n    String hello();\n}\n");
    Files.writeString(directory.resolve("greeting_api.py"), python);
    var mojo = new PolyglotValidateContractsMojo();
    set(mojo, "validateContracts", true);
    set(mojo, "inputDirectory", directory.toFile());
    set(mojo, "contracts", List.of(java.toFile()));
    return mojo;
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
