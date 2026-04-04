package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolyglotCodegenCheckMojoTest {

  @TempDir Path tempDir;

  @Test
  void checkPassesWhenGeneratedSourcesAreUpToDate() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-ok"));
    Path outputDirectory = tempDir.resolve("generated-ok");
    Path script = inputDirectory.resolve("check_api.py");
    Files.writeString(
        script,
        """
        polyglot.export_value("CheckApi", CheckApi)

        class CheckApi:
            def ping(self) -> int:
                return 1
        """);

    PolyglotCodegenMojo generator = new PolyglotCodegenMojo();
    setField(generator, "inputDirectory", inputDirectory.toFile());
    setField(generator, "outputDirectory", outputDirectory.toFile());
    setField(generator, "basePackage", "com.example.polyglot");
    setField(generator, "projectGroupId", "com.example");
    setField(generator, "project", new org.apache.maven.project.MavenProject());
    generator.execute();

    PolyglotCodegenCheckMojo check = new PolyglotCodegenCheckMojo();
    setField(check, "inputDirectory", inputDirectory.toFile());
    setField(check, "outputDirectory", outputDirectory.toFile());
    setField(check, "basePackage", "com.example.polyglot");
    setField(check, "projectGroupId", "com.example");

    assertDoesNotThrow(check::execute);
  }

  @Test
  void checkFailsWhenGeneratedSourcesAreDrifted() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-drift"));
    Path outputDirectory = tempDir.resolve("generated-drift");
    Path script = inputDirectory.resolve("check_api.py");
    Files.writeString(
        script,
        """
        polyglot.export_value("CheckApi", CheckApi)

        class CheckApi:
            def ping(self) -> int:
                return 1
        """);

    PolyglotCodegenMojo generator = new PolyglotCodegenMojo();
    setField(generator, "inputDirectory", inputDirectory.toFile());
    setField(generator, "outputDirectory", outputDirectory.toFile());
    setField(generator, "basePackage", "com.example.polyglot");
    setField(generator, "projectGroupId", "com.example");
    setField(generator, "project", new org.apache.maven.project.MavenProject());
    generator.execute();

    Path generatedSource = outputDirectory.resolve("com/example/polyglot/CheckApi.java");
    Files.writeString(generatedSource, Files.readString(generatedSource) + "\n// drift");

    PolyglotCodegenCheckMojo check = new PolyglotCodegenCheckMojo();
    setField(check, "inputDirectory", inputDirectory.toFile());
    setField(check, "outputDirectory", outputDirectory.toFile());
    setField(check, "basePackage", "com.example.polyglot");
    setField(check, "projectGroupId", "com.example");

    assertThrows(MojoExecutionException.class, check::execute);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
