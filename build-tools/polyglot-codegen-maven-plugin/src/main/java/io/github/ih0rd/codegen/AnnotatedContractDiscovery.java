package io.github.ih0rd.codegen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

/** Source-only discovery of the framework-neutral client annotation, without class loading. */
final class AnnotatedContractDiscovery {
  private static final String ANNOTATION = "io.github.ih0rd.polyglot.annotations.PolyglotClient";
  private static final String ANNOTATION_PACKAGE = "io.github.ih0rd.polyglot.annotations";

  private AnnotatedContractDiscovery() {}

  static List<File> discover(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException(
          "Contract sourceDirectory is not a directory: " + directory);
    }
    List<File> contracts = new ArrayList<>();
    JavaParser parser =
        new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    try (var paths = Files.walk(directory)) {
      for (Path path :
          paths
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(".java"))
              .sorted()
              .toList()) {
        var result = parser.parse(path);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
          throw new IllegalArgumentException(
              path + ": Cannot discover contracts in invalid Java source: " + result.getProblems());
        }
        CompilationUnit unit = result.getResult().orElseThrow();
        boolean found = false;
        for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
          if (!isClientAnnotation(unit, annotation, path)) continue;
          if (!(annotation.getParentNode().orElseThrow() instanceof TypeDeclaration<?>)) {
            throw new IllegalArgumentException(
                path
                    + ":"
                    + annotation.getBegin().orElseThrow().line
                    + ": Unsupported @PolyglotClient target: select a top-level interface.");
          }
          found = true;
        }
        if (found) contracts.add(path.toFile());
      }
    }
    return contracts;
  }

  private static boolean isClientAnnotation(
      CompilationUnit unit, AnnotationExpr annotation, Path path) {
    String name = annotation.getNameAsString();
    if (name.equals(ANNOTATION)) return true;
    if (!name.equals("PolyglotClient")) return false;
    boolean localType =
        unit.findAll(TypeDeclaration.class).stream()
            .anyMatch(t -> t.getNameAsString().equals("PolyglotClient"));
    var namedImports =
        unit.getImports().stream()
            .filter(i -> !i.isStatic() && !i.isAsterisk())
            .map(i -> i.getNameAsString())
            .filter(i -> i.endsWith(".PolyglotClient"))
            .toList();
    if (!namedImports.isEmpty()) {
      if (namedImports.size() != 1) {
        throw new IllegalArgumentException(
            path + ": Ambiguous PolyglotClient imports; use " + ANNOTATION + ".");
      }
      if (!namedImports.get(0).equals(ANNOTATION)) return false;
      if (localType)
        throw new IllegalArgumentException(
            path
                + ": Unsupported ambiguous @PolyglotClient resolution: a local type shadows the imported annotation; use its fully qualified name.");
      return true;
    }
    boolean wildcard =
        unit.getImports().stream()
            .anyMatch(
                i ->
                    !i.isStatic()
                        && i.isAsterisk()
                        && i.getNameAsString().equals(ANNOTATION_PACKAGE));
    boolean samePackage =
        unit.getPackageDeclaration()
            .map(p -> p.getNameAsString().equals(ANNOTATION_PACKAGE))
            .orElse(false);
    if (wildcard || samePackage) {
      throw new IllegalArgumentException(
          path
              + ":"
              + annotation.getBegin().orElseThrow().line
              + ": Unsupported ambiguous @PolyglotClient resolution; use an explicit import of "
              + ANNOTATION
              + " or its fully qualified annotation name.");
    }
    return false;
  }
}
