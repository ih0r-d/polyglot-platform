package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        outputDirectory.toAbsolutePath().toString(), project.getCompileSourceRoots().getFirst());
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

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
