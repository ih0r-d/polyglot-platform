package io.github.ih0rd.adapter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.graalvm.polyglot.Context;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraalPyVfsIntegrationTest {
  @TempDir Path directory;

  @Test
  void importsPythonFromUniqueVfsRootInRealResourceJar() throws Exception {
    Path jar = jar("unique.jar", "GRAALPY-VFS/test/unique");
    try (var loader = loader(jar);
        var vfs = vfs(loader, "GRAALPY-VFS/test/unique");
        var context = context(vfs)) {
      assertThat(context.eval("python", "import packaged_helper; packaged_helper.answer()").asInt())
          .isEqualTo(42);
    }
  }

  @Test
  void contextClosureDoesNotCloseCallerVfsAndVfsCloseRemovesExtraction() throws Exception {
    Path extracted;
    try (var loader = loader(jar("extraction.jar", "GRAALPY-VFS/test/extraction"));
        var vfs = vfs(loader, "GRAALPY-VFS/test/extraction")) {
      try (var context = context(vfs)) {
        extracted =
            Path.of(
                context
                    .eval("python", "import os; os.path.realpath('/graalpy_vfs/src/extracted.ttf')")
                    .asString());
        assertThat(extracted).exists();
        assertThat(extracted.toString()).doesNotStartWith("/graalpy_vfs/");
      }
      assertThat(extracted).exists();
    }
    assertThat(extracted).doesNotExist();
  }

  @Test
  void twoJarsWithSameVfsRootAreRejectedWithoutExperimentalMerging() throws Exception {
    Path first = jar("first.jar", "GRAALPY-VFS/test/duplicate");
    Path second = jar("second.jar", "GRAALPY-VFS/test/duplicate");
    try (var loader =
        new URLClassLoader(new URL[] {first.toUri().toURL(), second.toUri().toURL()}, null)) {
      assertThatThrownBy(
              () -> {
                try (var vfs = vfs(loader, "GRAALPY-VFS/test/duplicate");
                    var context = context(vfs)) {
                  context.eval("python", "import packaged_helper");
                }
              })
          .hasStackTraceContaining("multiple embedded virtual filesystem");
    }
  }

  private Context context(VirtualFileSystem vfs) {
    return GraalPyResources.contextBuilder(vfs)
        .option("engine.WarnInterpreterOnly", "false")
        .build();
  }

  private VirtualFileSystem vfs(URLClassLoader loader, String root) throws ClassNotFoundException {
    return VirtualFileSystem.newBuilder()
        .resourceDirectory(root)
        .resourceLoadingClass(loader.loadClass(ResourceAnchor.class.getName()))
        .build();
  }

  private URLClassLoader loader(Path jar) throws IOException {
    return new URLClassLoader(new URL[] {jar.toUri().toURL()}, null);
  }

  private Path jar(String name, String root) throws IOException {
    Path path = directory.resolve(name);
    String anchor = ResourceAnchor.class.getName().replace('.', '/') + ".class";
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (var stream = getClass().getClassLoader().getResourceAsStream(anchor)) {
      if (stream == null) throw new IOException("Missing test anchor class");
      entries.put(anchor, stream.readAllBytes());
    }
    entries.put(
        root + "/fileslist.txt",
        ("/"
                + root
                + "/\n/"
                + root
                + "/src/\n/"
                + root
                + "/src/packaged_helper.py\n/"
                + root
                + "/src/extracted.ttf\n")
            .getBytes(StandardCharsets.UTF_8));
    entries.put(
        root + "/src/packaged_helper.py",
        "def answer(): return 42\n".getBytes(StandardCharsets.UTF_8));
    entries.put(root + "/src/extracted.ttf", new byte[] {1, 2, 3});
    try (var output = new JarOutputStream(Files.newOutputStream(path))) {
      for (var entry : entries.entrySet()) {
        output.putNextEntry(new JarEntry(entry.getKey()));
        output.write(entry.getValue());
        output.closeEntry();
      }
    }
    return path;
  }

  static final class ResourceAnchor {}
}
