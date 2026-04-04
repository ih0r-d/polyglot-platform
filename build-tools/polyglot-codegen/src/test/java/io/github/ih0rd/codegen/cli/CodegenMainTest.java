package io.github.ih0rd.codegen.cli;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class CodegenMainTest {

  @TempDir Path tempDir;

  @Test
  void mainGeneratesJavaSourcesForPythonContracts() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts"));
    Path outputDirectory = tempDir.resolve("generated");

    Files.writeString(
        inputDirectory.resolve("sample.py"),
        """
        polyglot.export_value("SampleApi", SampleApi)

        class SampleApi:
            def ping(self, value: int) -> int:
                return value
        """);

    CodegenMain.main(
        new String[] {
          inputDirectory.toString(), outputDirectory.toString(), "--package=com.example.generated"
        });

    Path generatedSource = outputDirectory.resolve("com/example/generated/SampleApi.java");
    assertTrue(Files.exists(generatedSource));
    assertTrue(Files.readString(generatedSource).contains("public interface SampleApi"));
  }

  @Test
  void mainRejectsMissingRequiredPackageArgument() {
    assertThrows(
        IllegalArgumentException.class, () -> CodegenMain.main(new String[] {"input", "output"}));
  }

  @Test
  void mainRejectsBlankPackageArgument() {
    assertThrows(
        IllegalArgumentException.class,
        invokeMain(new String[] {"input", "output", "--package=   "}));
  }

  @Test
  void mainRejectsInputPathThatIsNotDirectory() throws Exception {
    Path inputFile = Files.writeString(tempDir.resolve("input.py"), "print('x')");
    Path outputDirectory = tempDir.resolve("generated");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            invokeMain(
                new String[] {
                  inputFile.toString(),
                  outputDirectory.toString(),
                  "--package=com.example.generated"
                }));

    assertTrue(exception.getMessage().contains("Input path is not a directory"));
  }

  @Test
  void mainHonorsOnlyIncludedMethodsFlag() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-included"));
    Path outputDirectory = tempDir.resolve("generated-included");

    Files.writeString(
        inputDirectory.resolve("sample.py"),
        """
        polyglot.export_value("SampleApi", SampleApi)

        class SampleApi:
            @adapter_include
            def ping(self, value: int) -> int:
                return value

            def hidden(self) -> int:
                return 0
        """);

    CodegenMain.main(
        new String[] {
          inputDirectory.toString(),
          outputDirectory.toString(),
          "--package=com.example.generated",
          "--only-included-methods=true"
        });

    Path generatedSource = outputDirectory.resolve("com/example/generated/SampleApi.java");
    String javaSource = Files.readString(generatedSource);
    assertTrue(javaSource.contains(" ping("));
    assertTrue(!javaSource.contains("hidden("));
  }

  @Test
  void mainFailsWhenOutputPathCannotBeCreated() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-invalid-output"));
    Path outputFile = Files.writeString(tempDir.resolve("generated-file"), "occupied");
    String[] arguments = {
      inputDirectory.toString(), outputFile.toString(), "--package=com.example.generated"
    };

    assertThrows(UncheckedIOException.class, invokeMain(arguments));
  }

  @Test
  void mainWrapsScriptProcessingFailures() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-invalid-script"));
    Path outputDirectory = tempDir.resolve("generated-invalid-script");

    Files.writeString(
        inputDirectory.resolve("broken.py"),
        """
        class BrokenApi:
            def broken(self) -> int:
                return 1
        """);

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            invokeMain(
                new String[] {
                  inputDirectory.toString(),
                  outputDirectory.toString(),
                  "--package=com.example.generated"
                }));

    assertTrue(exception.getMessage().contains("Failed to process script"));
  }

  @Test
  void mainFailsInStrictModeWhenUnknownTypesArePresent() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-strict-mode"));
    Path outputDirectory = tempDir.resolve("generated-strict-mode");

    Files.writeString(
        inputDirectory.resolve("strict_unknown.py"),
        """
        polyglot.export_value("StrictApi", StrictApi)

        class StrictApi:
            def ping(self, payload):
                return payload
        """);

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            invokeMain(
                new String[] {
                  inputDirectory.toString(),
                  outputDirectory.toString(),
                  "--package=com.example.generated",
                  "--strict-mode=true"
                }));

    assertTrue(exception.getMessage().contains("Failed to process script"));
    assertTrue(exception.getCause().getMessage().contains("Unknown"));
  }

  private static Executable invokeMain(String[] arguments) {
    return () -> CodegenMain.main(arguments);
  }
}
