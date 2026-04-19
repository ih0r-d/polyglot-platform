package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolyglotCodegenDoctorMojoTest {

  @TempDir Path tempDir;

  @Test
  void doctorPassesWhenGeneratedSourcesAreUpToDate() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-ok"));
    Path outputDirectory = tempDir.resolve("generated-ok");
    Path script = inputDirectory.resolve("doctor_api.py");
    Files.writeString(
        script,
        """
        polyglot.export_value("DoctorApi", DoctorApi)

        class DoctorApi:
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

    PolyglotCodegenDoctorMojo doctor = new PolyglotCodegenDoctorMojo();
    setField(doctor, "inputDirectory", inputDirectory.toFile());
    setField(doctor, "outputDirectory", outputDirectory.toFile());
    setField(doctor, "basePackage", "com.example.polyglot");
    setField(doctor, "projectGroupId", "com.example");

    assertDoesNotThrow(doctor::execute);
  }

  @Test
  void doctorFailsWhenGeneratedSourcesAreDrifted() throws Exception {
    Path inputDirectory = Files.createDirectories(tempDir.resolve("scripts-drift"));
    Path outputDirectory = tempDir.resolve("generated-drift");
    Path script = inputDirectory.resolve("doctor_api.py");
    Files.writeString(
        script,
        """
        polyglot.export_value("DoctorApi", DoctorApi)

        class DoctorApi:
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

    Path generatedSource = outputDirectory.resolve("com/example/polyglot/DoctorApi.java");
    Files.writeString(generatedSource, Files.readString(generatedSource) + "\n// drift");

    PolyglotCodegenDoctorMojo doctor = new PolyglotCodegenDoctorMojo();
    setField(doctor, "inputDirectory", inputDirectory.toFile());
    setField(doctor, "outputDirectory", outputDirectory.toFile());
    setField(doctor, "basePackage", "com.example.polyglot");
    setField(doctor, "projectGroupId", "com.example");

    assertThrows(MojoExecutionException.class, doctor::execute);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Class<?> type = target.getClass();

    while (type != null) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
        return;
      } catch (NoSuchFieldException ignored) {
        type = type.getSuperclass();
      }
    }

    throw new NoSuchFieldException(name);
  }
}
