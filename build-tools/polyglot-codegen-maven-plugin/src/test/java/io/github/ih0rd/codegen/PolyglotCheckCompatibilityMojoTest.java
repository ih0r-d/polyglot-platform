package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolyglotCheckCompatibilityMojoTest {
  @TempDir Path directory;

  @Test
  void compatibleEvolutionPasses() throws Exception {
    assertDoesNotThrow(configured("def f(x, y=1): pass")::execute);
  }

  @Test
  void confirmedBreaksFailEvenWhenUnknownIsAllowed() throws Exception {
    var mojo = configured("def renamed(x): pass");
    set(mojo, "failOnUnknown", false);
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute).getMessage().contains("BREAKING"));
    assertTrue(
        assertThrows(MojoFailureException.class, configured("def f(x, y): pass")::execute)
            .getMessage()
            .contains("BREAKING"));
  }

  @Test
  void unknownFailsByDefaultAndCanBeReportedWithoutFailure() throws Exception {
    var mojo = configured("@dynamic\ndef f(x): pass");
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute).getMessage().contains("UNKNOWN"));
    set(mojo, "failOnUnknown", false);
    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void baselineIsAlwaysExplicitAndInvalidRootsFailClearly() throws Exception {
    assertTrue(
        assertThrows(MojoFailureException.class, new PolyglotCheckCompatibilityMojo()::execute)
            .getMessage()
            .contains("explicit baseline"));
    var mojo = configured("def f(x): pass");
    set(mojo, "baseline", directory.resolve("missing").toFile());
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute).getMessage().contains("Baseline"));
    set(mojo, "currentDirectory", directory.resolve("missing").toFile());
    assertTrue(
        assertThrows(MojoFailureException.class, mojo::execute)
            .getMessage()
            .contains("currentDirectory"));
  }

  private PolyglotCheckCompatibilityMojo configured(String source) throws Exception {
    Path baseline = Files.writeString(directory.resolve("api.py"), "def f(x): pass");
    Path current = Files.createDirectories(directory.resolve("current"));
    Files.writeString(current.resolve("api.py"), source);
    var mojo = new PolyglotCheckCompatibilityMojo();
    set(mojo, "baseline", baseline.toFile());
    set(mojo, "currentDirectory", current.toFile());
    return mojo;
  }

  private static void set(Object target, String name, Object value) throws Exception {
    var field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
