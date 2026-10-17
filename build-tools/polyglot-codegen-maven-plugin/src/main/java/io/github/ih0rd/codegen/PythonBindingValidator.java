package io.github.ih0rd.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;

import io.github.ih0rd.codegen.parsers.PythonContractParser;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

/** Internal static checks; deliberately narrower than runtime Python binding. */
final class PythonBindingValidator {
  private static final Pattern CLASS = Pattern.compile("class [A-Za-z_][A-Za-z_0-9]*\\s*:");
  private static final Pattern METHOD =
      Pattern.compile("def [A-Za-z_][A-Za-z_0-9]*\\([^\\n]*\\)(?:\\s*->[^:]+)?\\s*:");
  private static final Pattern EXPORT =
      Pattern.compile(
          "polyglot\\.export_value\\(\\s*(['\"])([A-Za-z_][A-Za-z_0-9]*)\\1\\s*,\\s*([A-Za-z_][A-Za-z_0-9]*)\\s*\\)");

  private PythonBindingValidator() {}

  static void validate(Path javaSource, Path pythonDirectory) throws IOException {
    var result = new JavaParser().parse(javaSource);
    if (!result.isSuccessful() || result.getResult().isEmpty()) {
      throw new IllegalArgumentException(
          javaSource + ": Cannot parse Java contract: " + result.getProblems());
    }
    var unit = result.getResult().orElseThrow();
    if (unit.getTypes().size() != 1
        || !(unit.getType(0) instanceof ClassOrInterfaceDeclaration type)
        || !type.isInterface()
        || !type.getExtendedTypes().isEmpty()) {
      throw new IllegalArgumentException(
          javaSource
              + ": Unsupported contract: select one top-level interface without inheritance.");
    }
    checkAnnotation(type, javaSource);
    String name = type.getNameAsString();
    // Match the runtime's per-uppercase-character convention, including acronym names.
    StringBuilder module = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (i > 0 && Character.isUpperCase(c)) module.append('_');
      module.append(Character.toLowerCase(c));
    }
    Path python = pythonDirectory.resolve(module + ".py");
    if (!Files.isRegularFile(python)) {
      throw new IllegalArgumentException(
          javaSource
              + ": Missing Python module '"
              + module
              + "' for symbol '"
              + name
              + "': expected "
              + python);
    }
    String source = Files.readString(python);
    ContractClass target;
    Map<String, Map<String, PythonPositionalSignature>> signatures;
    try {
      signatures = checkStaticSubset(source, python);
      target =
          new PythonContractParser()
                  .parse(
                      new ScriptDescriptor(SupportedLanguage.PYTHON, source, python.toString()),
                      new CodegenConfig(false))
                  .classes()
                  .stream()
                  .filter(c -> c.name().equals(name))
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("Missing export '" + name + "'"));
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          python + ": module '" + module + "', symbol '" + name + "': " + ex.getMessage(), ex);
    }
    Set<String> available = new HashSet<>();
    target.methods().forEach(m -> available.add(m.name()));
    for (MethodDeclaration method : type.getMethods()) {
      if (method.isPrivate()) continue;
      String symbol = method.getNameAsString();
      if (symbol.startsWith("_")) {
        throw new IllegalArgumentException(
            javaSource
                + ":"
                + method.getBegin().orElseThrow().line
                + ": module '"
                + module
                + "', symbol '"
                + name
                + "."
                + symbol
                + "': Unsupported static contract: the codegen parser excludes underscore-prefixed methods.");
      }
      if (!available.contains(symbol) || !signatures.get(name).containsKey(symbol)) {
        throw new IllegalArgumentException(
            javaSource
                + ":"
                + method.getBegin().orElseThrow().line
                + ": module '"
                + module
                + "', symbol '"
                + name
                + "."
                + symbol
                + "': Missing Python method in "
                + python);
      }
      PythonPositionalSignature signature = signatures.get(name).get(symbol);
      int arguments = method.getParameters().size();
      if (arguments < signature.required() || arguments > signature.maximum()) {
        throw new IllegalArgumentException(
            javaSource
                + ":"
                + method.getBegin().orElseThrow().line
                + ": module '"
                + module
                + "', symbol '"
                + name
                + "."
                + symbol
                + "': Java supplies "
                + arguments
                + " positional argument(s); Python accepts "
                + signature.required()
                + ".."
                + signature.maximum()
                + " (receiver excluded), declared at "
                + python
                + ":"
                + signature.line());
      }
    }
  }

  private static void checkAnnotation(ClassOrInterfaceDeclaration type, Path file) {
    for (AnnotationExpr annotation : type.getAnnotations()) {
      if (!annotation.getName().getIdentifier().equals("PolyglotClient")) continue;
      if (!annotation.isNormalAnnotationExpr()) {
        throw new IllegalArgumentException(
            file
                + ": Unsupported @PolyglotClient: explicitly select languages = SupportedLanguage.PYTHON.");
      }
      boolean python = false;
      for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
        String value = pair.getValue().toString().replaceAll("\\s", "");
        if (pair.getNameAsString().equals("languages")) {
          python =
              value.matches(
                  "\\{?(?:io\\.github\\.ih0rd\\.polyglot\\.)?SupportedLanguage\\.PYTHON\\}?");
        } else if (pair.getNameAsString().equals("convention")
            && !value.matches(
                "(?:io\\.github\\.ih0rd\\.polyglot\\.)?Convention\\.(DEFAULT|BY_INTERFACE_EXPORT)")) {
          throw new IllegalArgumentException(
              file
                  + ": Unsupported Python convention: "
                  + value
                  + ". Only DEFAULT/BY_INTERFACE_EXPORT are statically validated.");
        }
      }
      if (!python)
        throw new IllegalArgumentException(
            file + ": Unsupported language selection: explicitly select SupportedLanguage.PYTHON.");
    }
  }

  private static Map<String, Map<String, PythonPositionalSignature>> checkStaticSubset(
      String source, Path file) {
    Map<String, Map<String, PythonPositionalSignature>> classes = new HashMap<>();
    Map<String, Map<String, PythonPositionalSignature>> exports = new HashMap<>();
    Map<String, PythonPositionalSignature> methods = new HashMap<>();
    int classIndent = -1;
    int methodIndent = -1;
    String currentClass = null;
    String[] lines = source.split("\\R");
    for (int i = 0; i < lines.length; i++) {
      String raw = lines[i];
      String line = raw.strip();
      if (line.isEmpty() || line.startsWith("#")) continue;
      if (raw.contains("\t")
          || line.contains("\"\"\"")
          || line.contains("'''")
          || line.endsWith("\\")) {
        unsupported(file, i, "tabs, multiline strings or escaped line continuations");
      }
      int indent = raw.length() - raw.stripLeading().length();
      if (indent == 0) {
        classIndent = -1;
        methodIndent = -1;
        if (line.equals("import polyglot")) continue;
        if (CLASS.matcher(line).matches()) {
          currentClass = line.substring(6, line.indexOf(':')).trim();
          methods = new HashMap<>();
          if (classes.putIfAbsent(currentClass, methods) != null)
            unsupported(file, i, "duplicate class " + currentClass);
          classIndent = indent;
          continue;
        }
        var export = EXPORT.matcher(line);
        if (export.matches()) {
          if (!classes.containsKey(export.group(3)))
            unsupported(file, i, "export target is not a preceding local class");
          if (exports.putIfAbsent(export.group(2), Map.copyOf(classes.get(export.group(3))))
              != null) unsupported(file, i, "duplicate export " + export.group(2));
          continue;
        }
        unsupported(
            file,
            i,
            "top-level statement (only import polyglot, plain classes and literal class exports are supported)");
      }
      if (classIndent < 0) unsupported(file, i, "statement outside a plain class");
      if (line.contains("polyglot.export_value")) unsupported(file, i, "non-top-level export");
      if (methodIndent >= 0 && indent > methodIndent) {
        if (line.startsWith("def ") || line.startsWith("class "))
          unsupported(file, i, "nested declaration");
        continue;
      }
      methodIndent = -1;
      if (METHOD.matcher(line).matches()) {
        String method = line.substring(4, line.indexOf('(')).trim();
        if (method.startsWith("__")) unsupported(file, i, "special method " + method);
        if (methods.putIfAbsent(method, PythonPositionalSignature.parse(line, file, i + 1)) != null)
          unsupported(file, i, "duplicate method " + currentClass + "." + method);
        methodIndent = indent;
      } else if (!line.equals("pass")) {
        unsupported(
            file,
            i,
            "class-level statement, decorator, inheritance or unsupported method signature");
      }
    }
    return exports;
  }

  private static void unsupported(Path file, int zeroBasedLine, String reason) {
    throw new IllegalArgumentException(
        file
            + ":"
            + (zeroBasedLine + 1)
            + ": Unsupported static Python contract: "
            + reason
            + ". Use runtime validateBinding(...) for dynamic contracts.");
  }
}
