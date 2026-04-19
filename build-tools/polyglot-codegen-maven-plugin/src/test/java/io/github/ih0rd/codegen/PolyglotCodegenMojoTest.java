package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolyglotCodegenMojoTest {

  @TempDir Path tempDir;

  @Test
  void executeGeneratesJavaSourceAndRegistersCompileRoot() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts"));
    Path outputDirectory = tempDir.resolve("generated");
    Path script = inputDirectory.resolve("my_api.py");
    Files.writeString(
        script,
        """
        polyglot.export_value("MyApi", MyApi)

        class MyApi:
            def add(self, a: int, b: int) -> int:
                return a + b
        """);

    PolyglotCodegenMojo mojo = new PolyglotCodegenMojo();
    MavenProject project = new MavenProject();

    setField(mojo, "inputDirectory", inputDirectory.toFile());
    setField(mojo, "outputDirectory", outputDirectory.toFile());
    setField(mojo, "basePackage", "com.example.polyglot");
    setField(mojo, "project", project);
    setField(mojo, "projectGroupId", "com.example");

    mojo.execute();

    Path generatedSource = outputDirectory.resolve("com/example/polyglot/MyApi.java");
    assertTrue(Files.exists(generatedSource));
    assertTrue(Files.readString(generatedSource).contains("public interface MyApi"));
    assertEquals(
        outputDirectory.toAbsolutePath().toString(), project.getCompileSourceRoots().get(0));
  }

  @Test
  void executeFailsWhenInputDirectoryDoesNotExist() throws Exception {
    PolyglotCodegenMojo mojo = new PolyglotCodegenMojo();
    Path missing = tempDir.resolve("missing");

    setField(mojo, "inputDirectory", missing.toFile());
    setField(mojo, "outputDirectory", tempDir.resolve("generated").toFile());
    setField(mojo, "project", new MavenProject());
    setField(mojo, "projectGroupId", "com.example");

    MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);

    assertTrue(exception.getMessage().contains("Input directory does not exist"));
  }

  @Test
  void executeUsesDefaultBasePackageWhenBlank() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-default"));
    Path outputDirectory = tempDir.resolve("generated-default");
    Path script = inputDirectory.resolve("simple_api.py");
    Files.writeString(
        script,
        """
        polyglot.export_value("SimpleApi", SimpleApi)

        class SimpleApi:
            def ping(self) -> int:
                return 1
        """);

    PolyglotCodegenMojo mojo = new PolyglotCodegenMojo();
    MavenProject project = new MavenProject();

    setField(mojo, "inputDirectory", inputDirectory.toFile());
    setField(mojo, "outputDirectory", outputDirectory.toFile());
    setField(mojo, "basePackage", "   ");
    setField(mojo, "project", project);
    setField(mojo, "projectGroupId", "io.demo");

    mojo.execute();

    Path generatedSource = outputDirectory.resolve("io/demo/polyglot/SimpleApi.java");
    assertTrue(Files.exists(generatedSource));
  }

  @Test
  void executeFailsWhenScriptProcessingThrows() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-invalid"));
    Path outputDirectory = tempDir.resolve("generated-invalid");
    Path script = inputDirectory.resolve("broken.py");
    Files.writeString(
        script,
        """
        class BrokenApi:
            def broken(self) -> int:
                return 1
        """);

    PolyglotCodegenMojo mojo = new PolyglotCodegenMojo();
    MavenProject project = new MavenProject();

    setField(mojo, "inputDirectory", inputDirectory.toFile());
    setField(mojo, "outputDirectory", outputDirectory.toFile());
    setField(mojo, "basePackage", "com.example.polyglot");
    setField(mojo, "project", project);
    setField(mojo, "projectGroupId", "com.example");

    assertThrows(MojoExecutionException.class, mojo::execute);
  }

  @Test
  void executeRespectsOnlyIncludedMethodsFlag() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-only-included"));
    Path outputDirectory = tempDir.resolve("generated-only-included");
    Path script = inputDirectory.resolve("include_api.py");
    Files.writeString(
        script,
        """
        polyglot.export_value("IncludeApi", IncludeApi)

        class IncludeApi:
            @adapter_include
            def included(self) -> int:
                return 1

            def excluded(self) -> int:
                return 2
        """);

    PolyglotCodegenMojo mojo = new PolyglotCodegenMojo();
    MavenProject project = new MavenProject();

    setField(mojo, "inputDirectory", inputDirectory.toFile());
    setField(mojo, "outputDirectory", outputDirectory.toFile());
    setField(mojo, "basePackage", "com.example.polyglot");
    setField(mojo, "onlyIncludedMethods", true);
    setField(mojo, "project", project);
    setField(mojo, "projectGroupId", "com.example");

    mojo.execute();

    Path generatedSource = outputDirectory.resolve("com/example/polyglot/IncludeApi.java");
    String generated = Files.readString(generatedSource);
    assertTrue(generated.contains("included("));
    assertFalse(generated.contains("excluded("));
  }

  @Test
  void executeFailsWhenFailOnNoContractsIsEnabledAndNothingGenerated() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-empty"));
    Path outputDirectory = tempDir.resolve("generated-empty");

    PolyglotCodegenMojo mojo = new PolyglotCodegenMojo();
    MavenProject project = new MavenProject();

    setField(mojo, "inputDirectory", inputDirectory.toFile());
    setField(mojo, "outputDirectory", outputDirectory.toFile());
    setField(mojo, "failOnNoContracts", true);
    setField(mojo, "project", project);
    setField(mojo, "projectGroupId", "com.example");

    MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);
    assertTrue(exception.getMessage().contains("No contracts generated"));
  }

  @Test
  void executeSkipsWritingWhenGeneratedContentIsUnchanged() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-unchanged"));
    Path outputDirectory = tempDir.resolve("generated-unchanged");
    Path script = inputDirectory.resolve("stable_api.py");
    Files.writeString(
        script,
        """
        polyglot.export_value("StableApi", StableApi)

        class StableApi:
            def ping(self) -> int:
                return 1
        """);

    PolyglotCodegenMojo mojo = new PolyglotCodegenMojo();
    MavenProject project = new MavenProject();

    setField(mojo, "inputDirectory", inputDirectory.toFile());
    setField(mojo, "outputDirectory", outputDirectory.toFile());
    setField(mojo, "basePackage", "com.example.polyglot");
    setField(mojo, "skipUnchanged", true);
    setField(mojo, "project", project);
    setField(mojo, "projectGroupId", "com.example");

    mojo.execute();
    Path generatedSource = outputDirectory.resolve("com/example/polyglot/StableApi.java");
    long firstModified = Files.getLastModifiedTime(generatedSource).toMillis();

    mojo.execute();

    long secondModified = Files.getLastModifiedTime(generatedSource).toMillis();
    assertEquals(firstModified, secondModified);
  }

  @Test
  void executeFailsInStrictModeWhenUnknownTypesExist() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-strict"));
    Path outputDirectory = tempDir.resolve("generated-strict");
    Path script = inputDirectory.resolve("strict_api.py");
    Files.writeString(
        script,
        """
        polyglot.export_value("StrictApi", StrictApi)

        class StrictApi:
            def hello(self, payload):
                return payload
        """);

    PolyglotCodegenMojo mojo = new PolyglotCodegenMojo();
    MavenProject project = new MavenProject();

    setField(mojo, "inputDirectory", inputDirectory.toFile());
    setField(mojo, "outputDirectory", outputDirectory.toFile());
    setField(mojo, "basePackage", "com.example.polyglot");
    setField(mojo, "strictMode", true);
    setField(mojo, "project", project);
    setField(mojo, "projectGroupId", "com.example");

    MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);
    assertTrue(exception.getMessage().contains("Failed processing script"));
    assertTrue(exception.getCause().getMessage().contains("Unknown"));
  }

  @Test
  void executeFailsWhenFailOnContractDriftIsEnabled() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-drift"));
    Path outputDirectory = tempDir.resolve("generated-drift");
    Path script = inputDirectory.resolve("drift_api.py");
    Files.writeString(
        script,
        """
        polyglot.export_value("DriftApi", DriftApi)

        class DriftApi:
            def ping(self) -> int:
                return 1
        """);

    PolyglotCodegenMojo mojo = new PolyglotCodegenMojo();
    MavenProject project = new MavenProject();

    setField(mojo, "inputDirectory", inputDirectory.toFile());
    setField(mojo, "outputDirectory", outputDirectory.toFile());
    setField(mojo, "basePackage", "com.example.polyglot");
    setField(mojo, "project", project);
    setField(mojo, "projectGroupId", "com.example");
    mojo.execute();

    Path generatedSource = outputDirectory.resolve("com/example/polyglot/DriftApi.java");
    Files.writeString(generatedSource, Files.readString(generatedSource) + "\n// drift");

    setField(mojo, "failOnContractDrift", true);
    MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);
    assertTrue(exception.getMessage().contains("drift"));
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
