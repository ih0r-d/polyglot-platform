package io.github.ih0rd.codegen.compatibility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import io.github.ih0rd.codegen.compatibility.CompatibilityDiagnostic.Category;
import io.github.ih0rd.codegen.compatibility.PythonContract.Function;
import io.github.ih0rd.codegen.compatibility.PythonContract.Kind;
import io.github.ih0rd.codegen.compatibility.PythonContract.Parameter;
import io.github.ih0rd.codegen.internal.python.PythonAstContracts;
import io.github.ih0rd.codegen.internal.python.PythonAstSession;
import io.github.ih0rd.codegen.internal.python.PythonAstTypes;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;
import io.github.ih0rd.polyglot.model.types.PolyType;

/** Static compatibility of Java positional bindings and supported mapped Java contract types. */
public final class PythonCompatibilityChecker {
  /** Creates a stateless checker. */
  public PythonCompatibilityChecker() {}

  /**
   * Compares an explicit baseline file/directory with a current directory, matching relative paths.
   * A baseline file selects only the file with the same basename in the current directory.
   *
   * @param baseline existing .py file or nonempty directory of .py files
   * @param currentDirectory existing directory containing current contracts
   * @return ordered findings; does not execute application Python or change files
   * @throws IOException if a source cannot be read
   * @throws IllegalArgumentException if the configured source roots are invalid
   */
  public CompatibilityReport compare(Path baseline, Path currentDirectory) throws IOException {
    try (var session = new PythonAstSession()) {
      return new Analysis(session).compare(baseline, currentDirectory);
    }
  }

  private static final class Analysis {
    private final PythonAstSession session;
    private final Map<String, PolyType> typeCache = new java.util.HashMap<>();
    private final Map<String, Boolean> literalCache = new java.util.HashMap<>();

    Analysis(PythonAstSession session) {
      this.session = session;
    }

    CompatibilityReport compare(Path baseline, Path currentDirectory) throws IOException {
      if (!Files.isDirectory(currentDirectory)) {
        throw new IllegalArgumentException(
            "currentDirectory is not a directory: " + currentDirectory);
      }
      Map<String, Path> oldFiles = sources(baseline);
      if (oldFiles.isEmpty())
        throw new IllegalArgumentException("Baseline contains no .py files: " + baseline);
      Map<String, Path> newFiles =
          Files.isRegularFile(baseline) ? new TreeMap<>() : sources(currentDirectory);
      if (Files.isRegularFile(baseline)) {
        Path name = Objects.requireNonNull(baseline.getFileName(), "Baseline file needs a name");
        Path selected = currentDirectory.resolve(name);
        if (Files.isRegularFile(selected)) newFiles.put(name.toString(), selected);
      }
      List<CompatibilityDiagnostic> findings = new ArrayList<>();
      Map<String, PythonContract> oldContracts = parse(oldFiles);
      Map<String, PythonContract> newContracts = parse(newFiles);
      oldContracts.values().forEach(c -> findings.addAll(c.diagnostics()));
      newContracts.values().forEach(c -> findings.addAll(c.diagnostics()));
      if (oldContracts.values().stream().allMatch(c -> c.complete() && c.functions().isEmpty())) {
        throw new IllegalArgumentException(
            "Baseline contains no Java-facing functions: " + baseline);
      }
      oldContracts.forEach(
          (module, oldContract) -> {
            String oldFile = oldFiles.get(module).toString();
            PythonContract newContract = newContracts.get(module);
            if (newContract == null) {
              finding(
                  findings,
                  oldFile,
                  1,
                  module,
                  CompatibilityOutcome.BREAKING,
                  Category.MODULE_REMOVED,
                  "Baseline Python module is absent from currentDirectory.");
            } else if (oldContract.complete() && newContract.complete()) {
              String currentFile = newFiles.get(module).toString();
              oldContract.functions().values().stream()
                  .sorted(java.util.Comparator.comparing(Function::name))
                  .forEach(
                      oldFunction -> {
                        Function current = newContract.functions().get(oldFunction.name());
                        if (current == null) {
                          finding(
                              findings,
                              oldFile,
                              oldFunction.line(),
                              oldFunction.name(),
                              CompatibilityOutcome.BREAKING,
                              Category.FUNCTION_REMOVED,
                              "Baseline callable is absent (a rename is a removal and an addition).");
                        } else compareFunction(oldFunction, current, currentFile, findings);
                      });
            }
          });
      newContracts.forEach(
          (module, contract) -> {
            if (contract.complete())
              contract
                  .functions()
                  .values()
                  .forEach(f -> inspect(f, newFiles.get(module).toString(), findings));
          });
      oldContracts.forEach(
          (module, contract) -> {
            if (contract.complete())
              contract
                  .functions()
                  .values()
                  .forEach(f -> inspect(f, oldFiles.get(module).toString(), findings));
          });
      return new CompatibilityReport(findings.stream().distinct().toList());
    }

    private Map<String, Path> sources(Path root) throws IOException {
      Map<String, Path> result = new TreeMap<>();
      if (Files.isRegularFile(root) && root.toString().endsWith(".py")) {
        result.put(
            Objects.requireNonNull(root.getFileName(), "Source file needs a name").toString(),
            root);
      } else if (Files.isDirectory(root)) {
        try (var files = Files.walk(root)) {
          files
              .filter(Files::isRegularFile)
              .filter(f -> f.toString().endsWith(".py"))
              .forEach(f -> result.put(root.relativize(f).toString().replace('\\', '/'), f));
        }
      } else
        throw new IllegalArgumentException("Baseline must be a .py file or directory: " + root);
      return result;
    }

    private Map<String, PythonContract> parse(Map<String, Path> files) throws IOException {
      Map<String, PythonContract> contracts = new TreeMap<>();
      for (var entry : files.entrySet()) {
        Path path = entry.getValue();
        contracts.put(
            entry.getKey(),
            PythonAstContracts.bindings(
                    session,
                    new ScriptDescriptor(
                        SupportedLanguage.PYTHON, Files.readString(path), path.toString()),
                    "compatibility")
                .contract());
      }
      return contracts;
    }

    private void compareFunction(
        Function old, Function current, String file, List<CompatibilityDiagnostic> findings) {
      if (old.requiredKeyword()) {
        finding(
            findings,
            file,
            current.line(),
            current.name(),
            CompatibilityOutcome.UNKNOWN,
            Category.REQUIRED_KEYWORD,
            "Baseline requires keyword arguments; Java bindings supply positional arguments only.");
        return;
      }
      if (current.requiredKeyword()) {
        finding(
            findings,
            file,
            current.line(),
            current.name(),
            CompatibilityOutcome.BREAKING,
            Category.REQUIRED_KEYWORD,
            "Current signature requires keyword-only arguments that Java cannot supply.");
      }
      if (current.required() > old.required() || current.maximum() < old.maximum()) {
        finding(
            findings,
            file,
            current.line(),
            current.name(),
            CompatibilityOutcome.BREAKING,
            Category.POSITIONAL_ARITY,
            "Java positional range narrowed from "
                + range(old)
                + " to "
                + range(current)
                + ". **kwargs does not accept positional arguments.");
      }
      List<Parameter> before = old.positional();
      List<Parameter> after = current.positional();
      for (int i = 0; i < before.size(); i++) {
        Parameter previous = before.get(i);
        int retainedIndex = -1;
        for (int j = 0; j < after.size(); j++)
          if (after.get(j).name().equals(previous.name())) retainedIndex = j;
        if (retainedIndex >= 0 && retainedIndex != i) {
          finding(
              findings,
              file,
              current.line(),
              current.name(),
              CompatibilityOutcome.BREAKING,
              Category.PARAMETER_BINDING,
              "Parameter '"
                  + previous.name()
                  + "' moved from Java argument "
                  + (i + 1)
                  + " to "
                  + (retainedIndex + 1)
                  + ".");
        }
        if (i < after.size()) {
          Parameter next = after.get(i);
          compareType(
              previous.annotation(),
              next.annotation(),
              "argument " + (i + 1),
              current,
              file,
              findings);
          if (previous.defaultValue() != null && next.defaultValue() != null) {
            compareDefault(
                previous.defaultValue(),
                next.defaultValue(),
                "argument " + (i + 1),
                current,
                file,
                findings);
          }
        } else if (current.maximum() == Integer.MAX_VALUE) {
          finding(
              findings,
              file,
              current.line(),
              current.name(),
              CompatibilityOutcome.UNKNOWN,
              Category.PARAMETER_BINDING,
              "Previously named argument "
                  + (i + 1)
                  + " is now captured by *args; value routing cannot be verified.");
        }
      }
      Parameter oldVariadic =
          old.parameters().stream()
              .filter(p -> p.kind() == Kind.VAR_POSITIONAL)
              .findFirst()
              .orElse(null);
      Parameter newVariadic =
          current.parameters().stream()
              .filter(p -> p.kind() == Kind.VAR_POSITIONAL)
              .findFirst()
              .orElse(null);
      if (oldVariadic != null) {
        for (int i = before.size(); i < after.size(); i++) {
          compareType(
              oldVariadic.annotation(),
              after.get(i).annotation(),
              "argument " + (i + 1),
              current,
              file,
              findings);
        }
        if (newVariadic != null)
          compareType(
              oldVariadic.annotation(), newVariadic.annotation(), "*args", current, file, findings);
      }
      // Java omits keyword-only parameters; changing their defaults can affect every existing
      // caller.
      old.parameters().stream()
          .filter(p -> p.kind() == Kind.KEYWORD_ONLY)
          .forEach(
              p ->
                  current.parameters().stream()
                      .filter(n -> n.name().equals(p.name()))
                      .filter(n -> p.defaultValue() != null && n.defaultValue() != null)
                      .forEach(
                          n ->
                              compareDefault(
                                  p.defaultValue(),
                                  n.defaultValue(),
                                  "keyword '" + p.name() + "'",
                                  current,
                                  file,
                                  findings)));
      compareType(old.returns(), current.returns(), "return", current, file, findings);
    }

    private void inspect(Function function, String file, List<CompatibilityDiagnostic> findings) {
      function
          .parameters()
          .forEach(
              p -> {
                inspectType(p.annotation(), function, file, findings);
                if (p.defaultValue() != null && !literal(p.defaultValue())) {
                  finding(
                      findings,
                      file,
                      function.line(),
                      function.name(),
                      CompatibilityOutcome.UNKNOWN,
                      Category.DEFAULT_VALUE,
                      "Default for '"
                          + p.name()
                          + "' is not a supported scalar literal; it is never evaluated.");
                }
              });
      inspectType(function.returns(), function, file, findings);
      if (function.requiredKeyword()) {
        finding(
            findings,
            file,
            function.line(),
            function.name(),
            CompatibilityOutcome.UNKNOWN,
            Category.REQUIRED_KEYWORD,
            "Required keyword-only parameters are not callable through Java positional binding.");
      }
    }

    private void inspectType(
        String annotation, Function function, String file, List<CompatibilityDiagnostic> findings) {
      if (!annotation.isEmpty() && reliableType(annotation) == null) {
        finding(
            findings,
            file,
            function.line(),
            function.name(),
            CompatibilityOutcome.UNKNOWN,
            Category.TYPE_ANNOTATION,
            "Annotation is outside the reliable Java mapping subset: " + annotation);
      }
    }

    private void compareType(
        String before,
        String after,
        String position,
        Function function,
        String file,
        List<CompatibilityDiagnostic> findings) {
      if (before.equals(after)) return;
      PolyType oldType = reliableType(before);
      PolyType newType = reliableType(after);
      boolean known = oldType != null && newType != null;
      if (known && oldType.equals(newType)) return;
      finding(
          findings,
          file,
          function.line(),
          function.name(),
          known ? CompatibilityOutcome.BREAKING : CompatibilityOutcome.UNKNOWN,
          Category.TYPE_ANNOTATION,
          position
              + " annotation changed; "
              + (known
                  ? "mapped Java contract types differ (invariant comparison)."
                  : "static Java type compatibility cannot be established."));
    }

    private PolyType reliableType(String annotation) {
      if (typeCache.containsKey(annotation)) return typeCache.get(annotation);
      PolyType type = null;
      try {
        var mapping = PythonAstTypes.annotation(session, annotation);
        if (mapping.reliable()) type = mapping.type();
      } catch (IllegalArgumentException unsupportedMapping) {
        // Unsafe mappings remain UNKNOWN; generation reports their actionable conversion error.
      }
      typeCache.put(annotation, type);
      return type;
    }

    private boolean literal(String value) {
      return literalCache.computeIfAbsent(value, session::literal);
    }

    private void compareDefault(
        String before,
        String after,
        String position,
        Function function,
        String file,
        List<CompatibilityDiagnostic> findings) {
      if (!before.equals(after))
        finding(
            findings,
            file,
            function.line(),
            function.name(),
            CompatibilityOutcome.UNKNOWN,
            Category.DEFAULT_VALUE,
            "Default changed for "
                + position
                + "; behavior of callers omitting it cannot be proven statically.");
    }

    private String range(Function function) {
      return function.required()
          + ".."
          + (function.maximum() == Integer.MAX_VALUE ? "*" : function.maximum());
    }

    private void finding(
        List<CompatibilityDiagnostic> findings,
        String file,
        int line,
        String symbol,
        CompatibilityOutcome outcome,
        Category category,
        String explanation) {
      findings.add(new CompatibilityDiagnostic(file, line, symbol, outcome, category, explanation));
    }
  }
}
