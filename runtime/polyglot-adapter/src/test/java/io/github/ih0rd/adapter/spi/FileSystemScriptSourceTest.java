package io.github.ih0rd.adapter.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ih0rd.polyglot.SupportedLanguage;

class FileSystemScriptSourceTest {

  @TempDir Path tempDir;

  @Test
  void existsReturnsTrueForExistingScript() throws Exception {
    Path pythonDir = Files.createDirectories(tempDir.resolve("python"));
    Files.writeString(pythonDir.resolve("demo.py"), "print('ok')");

    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    assertTrue(source.exists(SupportedLanguage.PYTHON, "demo"));
  }

  @Test
  void existsReturnsFalseForMissingScript() {
    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    assertFalse(source.exists(SupportedLanguage.JS, "missing"));
  }

  @Test
  void openReadsScriptUsingLanguageDirectoryAndExtension() throws Exception {
    Path jsDir = Files.createDirectories(tempDir.resolve("js"));
    Files.writeString(jsDir.resolve("demo.js"), "export const value = 1;");

    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    try (Reader reader = source.open(SupportedLanguage.JS, "demo")) {
      StringWriter writer = new StringWriter();
      reader.transferTo(writer);
      assertEquals("export const value = 1;", writer.toString());
    }
  }

  @Test
  void openReadsNestedScriptInsideLanguageDirectory() throws Exception {
    Path nestedDir = Files.createDirectories(tempDir.resolve("python/scripts/utils"));
    Files.writeString(nestedDir.resolve("helper.py"), "print('nested')");

    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    try (Reader reader = source.open(SupportedLanguage.PYTHON, "scripts/utils/helper")) {
      StringWriter writer = new StringWriter();
      reader.transferTo(writer);
      assertEquals("print('nested')", writer.toString());
    }
  }

  @Test
  void openThrowsWhenScriptIsMissing() {
    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    assertThrows(Exception.class, () -> source.open(SupportedLanguage.PYTHON, "missing"));
  }

  @Test
  void rejectsTraversalAndAbsoluteScriptNames() {
    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    assertInvalid(source, "../outside", "'../outside'");
    assertInvalid(source, "/tmp/outside", "'/tmp/outside'");
    assertInvalid(source, "scripts/../outside", "'scripts/../outside'");
  }

  @Test
  void rejectsWindowsPathsMixedSeparatorsAndResourcePrefixes() {
    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    assertInvalid(source, "C:/tmp/outside", "'C:/tmp/outside'");
    assertInvalid(source, "C:\\tmp\\outside", "'C:\\tmp\\outside'");
    assertInvalid(source, "\\\\server\\share\\script", "'\\\\server\\share\\script'");
    assertInvalid(source, "scripts\\..\\outside", "'scripts\\..\\outside'");
    assertInvalid(source, "file:/tmp/outside", "'file:/tmp/outside'");
    assertInvalid(source, "classpath:python/demo", "'classpath:python/demo'");
  }

  @Test
  void rejectsScriptSymlinkEscapingLanguageDirectory() throws Exception {
    Path pythonDir = Files.createDirectories(tempDir.resolve("python"));
    Path externalDir = Files.createDirectories(tempDir.resolve("external"));
    Path externalScript = Files.writeString(externalDir.resolve("secret.py"), "print('secret')");
    Files.createSymbolicLink(pythonDir.resolve("escape.py"), externalScript);

    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> source.exists(SupportedLanguage.PYTHON, "escape"));

    assertTrue(exception.getMessage().contains("Invalid logical script name 'escape'"));
    assertTrue(exception.getMessage().contains("escapes language directory"));
  }

  @Test
  void rejectsDirectorySymlinkEscapingLanguageDirectory() throws Exception {
    Path pythonDir = Files.createDirectories(tempDir.resolve("python"));
    Path externalDir = Files.createDirectories(tempDir.resolve("external"));
    Files.writeString(externalDir.resolve("secret.py"), "print('secret')");
    Files.createSymbolicLink(pythonDir.resolve("linked"), externalDir);

    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> source.open(SupportedLanguage.PYTHON, "linked/secret"));

    assertTrue(exception.getMessage().contains("Invalid logical script name 'linked/secret'"));
    assertTrue(exception.getMessage().contains("escapes language directory"));
  }

  @Test
  void rejectsLanguageDirectorySymlinkEscapingBaseDirectory() throws Exception {
    Path baseDir = Files.createDirectory(tempDir.resolve("base"));
    Path externalDir = Files.createDirectory(tempDir.resolve("external-python"));
    Files.writeString(externalDir.resolve("demo.py"), "print('secret')");
    Files.createSymbolicLink(baseDir.resolve("python"), externalDir);

    FileSystemScriptSource source = new FileSystemScriptSource(baseDir);

    IllegalArgumentException missingException =
        assertThrows(
            IllegalArgumentException.class,
            () -> source.exists(SupportedLanguage.PYTHON, "missing"));
    assertTrue(missingException.getMessage().contains("Invalid logical script name 'missing'"));
    assertTrue(missingException.getMessage().contains("language directory escapes base directory"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> source.exists(SupportedLanguage.PYTHON, "demo"));

    assertTrue(exception.getMessage().contains("Invalid logical script name 'demo'"));
    assertTrue(exception.getMessage().contains("language directory escapes base directory"));
  }

  @Test
  void rejectsSymlinkToAnotherLanguageDirectory() throws Exception {
    Path pythonDir = Files.createDirectories(tempDir.resolve("python"));
    Path jsDir = Files.createDirectories(tempDir.resolve("js"));
    Path pythonScript = Files.writeString(pythonDir.resolve("demo.py"), "print('python')");
    Files.createSymbolicLink(jsDir.resolve("demo.js"), pythonScript);

    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> source.open(SupportedLanguage.JS, "demo"));

    assertTrue(exception.getMessage().contains("Invalid logical script name 'demo'"));
    assertTrue(exception.getMessage().contains("escapes language directory"));
  }

  private static void assertInvalid(
      FileSystemScriptSource source, String scriptName, String expectedValue) {

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> source.exists(SupportedLanguage.PYTHON, scriptName));

    assertTrue(exception.getMessage().contains("Invalid logical script name " + expectedValue));
  }
}
