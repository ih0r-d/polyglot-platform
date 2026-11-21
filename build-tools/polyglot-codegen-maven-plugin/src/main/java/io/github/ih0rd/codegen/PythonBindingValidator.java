package io.github.ih0rd.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;

import io.github.ih0rd.codegen.compatibility.PythonContract;
import io.github.ih0rd.codegen.internal.python.PythonAstContracts;
import io.github.ih0rd.codegen.internal.python.PythonAstSession;
import io.github.ih0rd.polyglot.Convention;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.config.ScriptNames;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;

/** Source-only Java positional binding checks using codegen's existing Python metadata reader. */
final class PythonBindingValidator {
  private record ClientOptions(Convention convention, String script) {}

  private record BindingKey(Path script, Convention convention) {}

  private final Path pythonDirectory;
  private final PythonAstSession session;
  private final Map<BindingKey, PythonAstContracts.Binding> bindings = new HashMap<>();

  PythonBindingValidator(Path pythonDirectory, PythonAstSession session) {
    this.pythonDirectory = pythonDirectory;
    this.session = session;
  }

  static void validate(Path javaSource, Path pythonDirectory) throws IOException {
    try (var session = new PythonAstSession()) {
      new PythonBindingValidator(pythonDirectory, session).validate(javaSource);
    }
  }

  void validate(Path javaSource) throws IOException {
    var result =
        new JavaParser(
                new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21))
            .parse(javaSource);
    if (!result.isSuccessful() || result.getResult().isEmpty()) {
      throw new IllegalArgumentException(
          javaSource
              + ": Cannot parse Java contract: "
              + result.getProblems()
              + " [UNKNOWN: JAVA_SOURCE]");
    }
    var unit = result.getResult().orElseThrow();
    if (unit.getTypes().size() != 1
        || !(unit.getType(0) instanceof ClassOrInterfaceDeclaration type)
        || !type.isInterface()
        || !type.getExtendedTypes().isEmpty()) {
      throw new IllegalArgumentException(
          javaSource
              + ": Unsupported contract: select one top-level interface without inheritance. [UNKNOWN: JAVA_CONTRACT]");
    }
    ClientOptions options = clientOptions(type, javaSource);
    Convention convention = options.convention();
    String name = type.getNameAsString();
    String module = options.script();
    String location =
        javaSource
            + ":"
            + type.getBegin().orElseThrow().line
            + ": module '"
            + module
            + "', symbol '"
            + name
            + "': ";
    Path python = pythonDirectory.resolve(module + SupportedLanguage.PYTHON.ext());
    if (!Files.isRegularFile(python)) {
      throw new IllegalArgumentException(
          location
              + "Missing Python module '"
              + module
              + "': expected "
              + python
              + " [INCOMPATIBLE: MISSING_MODULE]");
    }
    Path realScript = python.toRealPath();
    if (!realScript.startsWith(pythonDirectory.toRealPath())) {
      throw new IllegalArgumentException(
          location + "Python script escapes inputDirectory: " + python + " [INVALID: SCRIPT]");
    }
    var bindingKey = new BindingKey(python, convention);
    var analysis = bindings.get(bindingKey);
    if (analysis == null) {
      var descriptor =
          new ScriptDescriptor(
              SupportedLanguage.PYTHON, Files.readString(realScript), python.toString());
      analysis = PythonAstContracts.bindings(session, descriptor, convention.name());
      bindings.put(bindingKey, analysis);
    }
    PythonContract binding = analysis.contract();
    if (!binding.complete()) {
      var diagnostic = binding.diagnostics().getFirst();
      throw new IllegalArgumentException(
          location
              + diagnostic.file()
              + ":"
              + diagnostic.line()
              + ": Unsupported static Python contract: "
              + diagnostic.explanation()
              + " [UNKNOWN: UNSUPPORTED_SYNTAX]. Use runtime validateBinding(...) for dynamic contracts.");
    }
    if (convention != Convention.BY_METHOD_NAME && !analysis.exports().contains(name)) {
      throw new IllegalArgumentException(
          location
              + "Missing export '"
              + name
              + "': No polyglot.export_value selecting this interface in "
              + python
              + ":1 [UNKNOWN: EXPORT_RESOLUTION]. Runtime language-binding fallback is outside this static check.");
    }
    for (var method : type.getMethods()) {
      if (method.isPrivate()) continue;
      String symbol = method.getNameAsString();
      String methodLocation =
          javaSource
              + ":"
              + method.getBegin().orElseThrow().line
              + ": module '"
              + module
              + "', symbol '"
              + name
              + "."
              + symbol
              + "': ";
      if (symbol.startsWith("_")) {
        throw new IllegalArgumentException(
            methodLocation
                + "Unsupported static contract: underscore-prefixed methods are excluded by the parser. "
                + "[UNKNOWN: JAVA_CONTRACT] Python source: "
                + python);
      }
      String key = convention == Convention.BY_METHOD_NAME ? symbol : name + "." + symbol;
      var function = binding.functions().get(key);
      if (function == null) {
        throw new IllegalArgumentException(
            methodLocation
                + "Missing Python method in "
                + python
                + ":1 [INCOMPATIBLE: MISSING_FUNCTION] ("
                + convention
                + (convention == Convention.BY_METHOD_NAME
                    ? "; no supported executable polyglot export or top-level language binding)."
                    : "; selected export has no such callable)."));
      }
      if (function.requiredKeyword()) {
        throw new IllegalArgumentException(
            methodLocation
                + "Java supplies positional arguments only; Python requires "
                + "keyword-only arguments, declared at "
                + python
                + ":"
                + function.line()
                + " [INCOMPATIBLE: REQUIRED_KEYWORD]");
      }
      PythonPositionalSignature signature = PythonPositionalSignature.from(function);
      int arguments = method.getParameters().size();
      if (arguments < signature.required() || arguments > signature.maximum()) {
        throw new IllegalArgumentException(
            methodLocation
                + "Java supplies "
                + arguments
                + " positional argument(s); Python accepts "
                + signature.range()
                + (convention == Convention.BY_METHOD_NAME
                    ? " (no implicit receiver)"
                    : " (bound receiver excluded for instance methods)")
                + ", declared at "
                + python
                + ":"
                + signature.line()
                + " [INCOMPATIBLE: POSITIONAL_ARITY]");
      }
    }
  }

  private static ClientOptions clientOptions(ClassOrInterfaceDeclaration type, Path file) {
    Convention convention = Convention.DEFAULT;
    String script = "";
    int scriptLine = type.getBegin().orElseThrow().line;
    for (AnnotationExpr annotation : type.getAnnotations()) {
      if (!annotation.getName().getIdentifier().equals("PolyglotClient")) continue;
      if (!annotation.isNormalAnnotationExpr()) {
        throw new IllegalArgumentException(
            file
                + ": Unsupported @PolyglotClient: explicitly select languages = SupportedLanguage.PYTHON. [UNKNOWN: LANGUAGE]");
      }
      String languages = "{}";
      for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
        if (pair.getNameAsString().equals("languages")) {
          languages = pair.getValue().toString().replaceAll("\\s", "");
        } else if (pair.getNameAsString().equals("convention")) {
          String value = pair.getValue().toString().replaceAll("\\s", "");
          if (!value.matches(
              "(?:io\\.github\\.ih0rd\\.polyglot\\.)?Convention\\.(DEFAULT|BY_INTERFACE_EXPORT|BY_METHOD_NAME)")) {
            throw new IllegalArgumentException(
                file
                    + ": Unsupported Python convention: "
                    + value
                    + ". Use an explicit Convention constant. [UNKNOWN: CONVENTION]");
          }
          convention = Convention.valueOf(value.substring(value.lastIndexOf('.') + 1));
        } else if (pair.getNameAsString().equals("script")) {
          scriptLine = pair.getBegin().orElseThrow().line;
          if (!AnnotatedContractDiscovery.isClientAnnotation(
                  type.findCompilationUnit().orElseThrow(), annotation, file)
              || !pair.getValue().isStringLiteralExpr()) {
            throw new IllegalArgumentException(
                file
                    + ":"
                    + scriptLine
                    + ": "
                    + "Use a string literal on the framework-neutral @PolyglotClient script attribute. [UNKNOWN: SCRIPT]");
          }
          script = pair.getValue().asStringLiteralExpr().asString();
        }
      }
      if (!(languages.matches(
              "\\{?(?:io\\.github\\.ih0rd\\.polyglot\\.)?SupportedLanguage\\.PYTHON\\}?")
          || (languages.equals("{}") && !script.isEmpty())))
        throw new IllegalArgumentException(
            file
                + ": Unsupported language selection: explicitly select SupportedLanguage.PYTHON. [UNKNOWN: LANGUAGE]");
    }
    try {
      return new ClientOptions(
          convention, ScriptNames.forPythonContract(type.getNameAsString(), script));
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException(
          file + ":" + scriptLine + ": " + invalid.getMessage() + " [INVALID: SCRIPT]", invalid);
    }
  }
}
