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
  void openThrowsWhenScriptIsMissing() {
    FileSystemScriptSource source = new FileSystemScriptSource(tempDir);

    assertThrows(Exception.class, () -> source.open(SupportedLanguage.PYTHON, "missing"));
  }
}
