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
