package io.github.ih0rd.codegen.compatibility;

import java.util.List;
import java.util.Map;

/** Internal parser metadata, separate from the released generation model. Not an extension SPI. */
public record PythonContract(
    Map<String, Function> functions, List<CompatibilityDiagnostic> diagnostics, boolean complete) {
  public PythonContract {
    functions = Map.copyOf(functions);
    diagnostics = List.copyOf(diagnostics);
  }

  /** Internal signature metadata. A null default denotes a required parameter. */
  public record Parameter(String name, Kind kind, String annotation, String defaultValue) {}

  /** Python parameter calling convention. */
  public enum Kind {
    POSITIONAL_ONLY,
    POSITIONAL_OR_KEYWORD,
    KEYWORD_ONLY,
    VAR_POSITIONAL,
    VAR_KEYWORD
  }

  /** Internal function metadata; bound class receivers have already been removed. */
  public record Function(String name, int line, List<Parameter> parameters, String returns) {
    public Function {
      parameters = List.copyOf(parameters);
    }

    public List<Parameter> positional() {
      return parameters.stream()
          .filter(p -> p.kind() == Kind.POSITIONAL_ONLY || p.kind() == Kind.POSITIONAL_OR_KEYWORD)
          .toList();
    }

    public int required() {
      return (int) positional().stream().filter(p -> p.defaultValue() == null).count();
    }

    public int maximum() {
      return parameters.stream().anyMatch(p -> p.kind() == Kind.VAR_POSITIONAL)
          ? Integer.MAX_VALUE
          : positional().size();
    }

    public boolean requiredKeyword() {
      return parameters.stream()
          .anyMatch(p -> p.kind() == Kind.KEYWORD_ONLY && p.defaultValue() == null);
    }
  }
}
