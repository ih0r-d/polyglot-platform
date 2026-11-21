package io.github.ih0rd.codegen.internal.python;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import io.github.ih0rd.codegen.compatibility.CompatibilityDiagnostic;
import io.github.ih0rd.codegen.compatibility.CompatibilityOutcome;
import io.github.ih0rd.codegen.compatibility.PythonContract;
import io.github.ih0rd.codegen.compatibility.PythonContract.Function;
import io.github.ih0rd.codegen.compatibility.PythonContract.Kind;
import io.github.ih0rd.codegen.compatibility.PythonContract.Parameter;
import io.github.ih0rd.codegen.internal.python.PythonAstSession.Node;
import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractMethod;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.ContractParam;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

/** Contract semantics over the official AST. Generation and checks share the same declarations. */
public final class PythonAstContracts {
  private PythonAstContracts() {}

  public record Binding(PythonContract contract, Set<String> exports) {
    public Binding {
      exports = Set.copyOf(exports);
    }
  }

  public static Binding bindings(PythonAstSession session, ScriptDescriptor script, String mode) {
    return new Analysis(session, script, mode).bindings();
  }

  public static ContractModel generate(
      PythonAstSession session, ScriptDescriptor script, CodegenConfig config) {
    return new Analysis(session, script, "generate").generate(config);
  }

  private record Declaration(String kind, Node node) {}

  private record Signature(Function function, Node declaration, boolean bound) {}

  private record Export(
      String name,
      Map<String, Signature> members,
      Signature callable,
      Node node,
      boolean dictionary,
      boolean invalid) {}

  private static final class Analysis {
    private static final Set<String> TYPING =
        Set.of("Any", "List", "Dict", "Optional", "Union", "Tuple", "Set");
    private final ScriptDescriptor script;
    private final String mode;
    private final Map<String, Signature> functions = new LinkedHashMap<>();
    private final Map<String, Map<String, Signature>> classes = new LinkedHashMap<>();
    private final Map<String, Declaration> definitions = new LinkedHashMap<>();
    private final Map<String, String> imports = new HashMap<>();
    private final List<Export> exports = new ArrayList<>();
    private final List<CompatibilityDiagnostic> issues = new ArrayList<>();
    private boolean syntaxError;

    Analysis(PythonAstSession session, ScriptDescriptor script, String mode) {
      this.script = script;
      this.mode = mode;
      var parsed = session.module(script.source(), script.fileName());
      if (parsed.error() != null) {
        syntaxError = true;
        var error = parsed.error();
        issues.add(
            new CompatibilityDiagnostic(
                script.fileName(),
                error.line(),
                "<module>",
                CompatibilityOutcome.UNKNOWN,
                CompatibilityDiagnostic.Category.UNSUPPORTED_SYNTAX,
                "column " + error.column() + ": " + error.message()));
        return;
      }
      var statements = parsed.root().nodes("body");
      // Generation retains forward export support; binding checks use statement-order definitions.
      for (Node node : statements) {
        if (node.is("FunctionDef", "AsyncFunctionDef"))
          functions.put(node.string("name"), signature(node, false));
        else if (node.is("ClassDef")) {
          Map<String, Signature> methods = new LinkedHashMap<>();
          for (Node member : node.nodes("body"))
            if (member.is("FunctionDef", "AsyncFunctionDef")) {
              if (methods.containsKey(member.string("name")))
                issue(member, "duplicate function declaration");
              methods.put(member.string("name"), signature(member, true));
            }
          classes.put(node.string("name"), methods);
        }
      }
      for (Node node : statements) statement(node);
    }

    private Signature signature(Node node, boolean bound) {
      Node args = node.node("args");
      List<Parameter> parameters = new ArrayList<>();
      List<Node> positional = new ArrayList<>(args.nodes("posonlyargs"));
      positional.addAll(args.nodes("args"));
      var defaults = args.nodes("defaults");
      int offset = positional.size() - defaults.size();
      for (int i = 0; i < positional.size(); i++)
        parameters.add(
            parameter(
                positional.get(i),
                i < args.nodes("posonlyargs").size()
                    ? Kind.POSITIONAL_ONLY
                    : Kind.POSITIONAL_OR_KEYWORD,
                i < offset ? Node.NONE : defaults.get(i - offset)));
      if (!args.node("vararg").is(""))
        parameters.add(parameter(args.node("vararg"), Kind.VAR_POSITIONAL, Node.NONE));
      for (int i = 0; i < args.nodes("kwonlyargs").size(); i++)
        parameters.add(
            parameter(
                args.nodes("kwonlyargs").get(i),
                Kind.KEYWORD_ONLY,
                args.nodes("kw_defaults").get(i)));
      if (!args.node("kwarg").is(""))
        parameters.add(parameter(args.node("kwarg"), Kind.VAR_KEYWORD, Node.NONE));
      Set<String> names = new HashSet<>();
      if (parameters.stream().anyMatch(p -> !names.add(p.name()))) {
        issue(node, "invalid or duplicate parameter name");
        return null;
      }
      if (bound) {
        if (parameters.isEmpty()
            || !Set.of(Kind.POSITIONAL_ONLY, Kind.POSITIONAL_OR_KEYWORD, Kind.VAR_POSITIONAL)
                .contains(parameters.getFirst().kind())) {
          issue(node, "bound method needs a positional receiver");
          return null;
        }
        if (parameters.getFirst().kind() != Kind.VAR_POSITIONAL) parameters.removeFirst();
      }
      return new Signature(
          new Function(node.string("name"), node.line(), parameters, node.node("returns").text()),
          node,
          bound);
    }

    private static Parameter parameter(Node arg, Kind kind, Node defaultValue) {
      return new Parameter(
          arg.string("arg"),
          kind,
          arg.node("annotation").text(),
          defaultValue.is("") ? null : defaultValue.source());
    }

    private void statement(Node node) {
      if (node.is("FunctionDef", "AsyncFunctionDef", "ClassDef")) {
        String name = node.string("name");
        define(name, new Declaration(node.is("ClassDef") ? "class" : "function", node), node);
        if (node.is("ClassDef")) {
          if (!node.nodes("bases").isEmpty()
              || !node.nodes("keywords").isEmpty()
              || !node.nodes("decorator_list").isEmpty()
              || !node.nodes("type_params").isEmpty())
            issue(
                node,
                "class inheritance, decorators, metaclasses or type parameters need runtime validation");
          for (Node member : node.nodes("body")) {
            if (member.is("FunctionDef", "AsyncFunctionDef")) {
              if (member.string("name").startsWith("__"))
                issue(member, "special methods can alter class binding");
              signatureIssues(member);
            } else if (!member.is("Pass") && !docstring(member))
              issue(member, "dynamic class member declaration");
          }
        } else signatureIssues(node);
      } else if (node.is("Import", "ImportFrom")) importStatement(node);
      else if (node.is("Assign")
          && node.nodes("targets").size() == 1
          && node.nodes("targets").getFirst().is("Name")
          && node.node("value").is("Name")
          && definitions.containsKey(node.node("value").string("id"))
          && !definitions.get(node.node("value").string("id")).kind().equals("import")) {
        define(
            node.nodes("targets").getFirst().string("id"),
            definitions.get(node.node("value").string("id")),
            node);
      } else if (exportCall(node) != null) export(node, exportCall(node));
      else if (!node.is("Pass") && !docstring(node))
        issue(node, "unsupported dynamic module statement or rebinding");
    }

    private void importStatement(Node node) {
      String module = node.string("module");
      boolean safe = true;
      for (Node alias : node.nodes("names")) {
        String name = alias.string("name");
        String local = alias.string("asname");
        if (local.isEmpty()) local = name;
        String target = node.is("Import") ? name : module + "." + name;
        boolean known =
            node.is("Import")
                ? Set.of("polyglot", "typing").contains(name)
                : !node.fields().containsKey("level")
                    || Integer.valueOf(0).equals(node.fields().get("level"))
                        && ((module.equals("typing") && TYPING.contains(name))
                            || (module.equals("polyglot") && name.equals("export_value")));
        safe &= known;
        boolean sameImport =
            definitions.containsKey(local)
                && definitions.get(local).kind().equals("import")
                && target.equals(imports.get(local));
        if (!sameImport) define(local, new Declaration("import", node), node);
        imports.put(local, target);
      }
      if (!safe) issue(node, "dynamic import requires runtime resolution");
    }

    private Node exportCall(Node statement) {
      if (!statement.is("Expr") || !statement.node("value").is("Call")) return null;
      Node call = statement.node("value"), function = call.node("func");
      if (function.is("Attribute")
          && function.string("attr").equals("export_value")
          && function.node("value").is("Name")) {
        String owner = function.node("value").string("id");
        if (imports.getOrDefault(owner, owner).equals("polyglot")) return call;
      }
      return function.is("Name")
              && "polyglot.export_value".equals(imports.get(function.string("id")))
          ? call
          : null;
    }

    private void export(Node site, Node call) {
      if (call.nodes("args").size() != 2
          || !call.nodes("keywords").isEmpty()
          || !call.nodes("args").getFirst().is("Constant")
          || !call.nodes("args").getFirst().string("constantKind").equals("str")) {
        issue(site, "export must use a literal name and a static local target");
        exports.add(new Export("", Map.of(), null, site, false, true));
        return;
      }
      String name = call.nodes("args").getFirst().string("value");
      Node target = call.nodes("args").get(1);
      if (exports.stream().anyMatch(e -> e.name().equals(name)))
        issue(site, "Duplicate contract name '" + name + "' in polyglot.export_value declarations");
      Map<String, Signature> members = new LinkedHashMap<>();
      Signature callable = null;
      boolean invalid = false;
      if (target.is("Name")) {
        Declaration declaration = definitions.get(target.string("id"));
        if (declaration != null && declaration.kind().equals("function"))
          callable = functions.get(declaration.node().string("name"));
        else if (declaration != null && declaration.kind().equals("class"))
          members.putAll(classes.get(declaration.node().string("name")));
        else if (classes.containsKey(target.string("id"))) {
          members.putAll(classes.get(target.string("id")));
          issue(site, "export target must be a previously declared local class");
        } else invalid = true;
      } else if (target.is("Dict")) {
        for (int i = 0; i < target.nodes("keys").size(); i++) {
          Node key = target.nodes("keys").get(i), value = target.nodes("values").get(i);
          if (!key.is("Constant")
              || !key.string("constantKind").equals("str")
              || !value.is("Name")) {
            invalid = true;
            continue;
          }
          Declaration declaration = definitions.get(value.string("id"));
          String original =
              declaration != null && declaration.kind().equals("function")
                  ? declaration.node().string("name")
                  : value.string("id");
          if (declaration == null)
            issue(site, "dictionary target must be a previously declared function");
          Signature signature = functions.get(original);
          if (members.containsKey(key.string("value")))
            issue(site, "duplicate dictionary export entry");
          if (signature == null) invalid = true;
          else members.put(key.string("value"), signature);
        }
      } else invalid = true;
      if (invalid) issue(site, "export target is dynamic or unresolved");
      exports.add(new Export(name, members, callable, site, target.is("Dict"), invalid));
    }

    private void define(String name, Declaration declaration, Node site) {
      if (definitions.containsKey(name))
        issue(site, "declaration or import rebinds a contract name: " + name);
      definitions.put(name, declaration);
    }

    private void signatureIssues(Node node) {
      boolean dynamic =
          Stream.concat(node.node("args").children().stream(), Stream.of(node.node("returns")))
              .flatMap(Analysis::walk)
              .anyMatch(n -> n.is("Call", "Lambda", "NamedExpr", "JoinedStr"));
      if (node.is("AsyncFunctionDef")
          || generator(node)
          || !node.nodes("decorator_list").isEmpty()
          || dynamic
          || !node.nodes("type_params").isEmpty())
        issue(
            node,
            "async, generator, decorators, dynamic signature expressions or type parameters need runtime validation");
      if (!mode.equals("generate")
          && !mode.equals("compatibility")
          && body(node).anyMatch(n -> n.is("FunctionDef", "AsyncFunctionDef", "ClassDef")))
        issue(node, "nested declarations remain outside static binding validation");
    }

    private static Stream<Node> walk(Node node) {
      return Stream.concat(Stream.of(node), node.children().stream().flatMap(Analysis::walk));
    }

    private static Stream<Node> body(Node node) {
      return node.nodes("body").stream().flatMap(Analysis::inScope);
    }

    private static Stream<Node> inScope(Node node) {
      return Stream.concat(
          Stream.of(node),
          node.is("FunctionDef", "AsyncFunctionDef", "ClassDef", "Lambda")
              ? Stream.empty()
              : node.children().stream().flatMap(Analysis::inScope));
    }

    private static boolean generator(Node node) {
      return body(node).anyMatch(n -> n.is("Yield", "YieldFrom"));
    }

    private static boolean docstring(Node node) {
      return node.is("Expr")
          && node.node("value").is("Constant")
          && node.node("value").string("constantKind").equals("str");
    }

    private Binding bindings() {
      Map<String, Function> projected = new LinkedHashMap<>();
      if (mode.equals("BY_METHOD_NAME")) {
        definitions.forEach(
            (name, definition) -> {
              if (definition.kind().equals("function") && !name.startsWith("_"))
                put(projected, name, functions.get(definition.node().string("name")));
            });
        for (Export export : exports) {
          if (export.callable() != null) put(projected, export.name(), export.callable());
          else if (!export.invalid() && !export.dictionary())
            issue(
                export.node(),
                "class objects exported as callables need runtime constructor validation");
        }
      } else if (!exports.isEmpty()) {
        for (Export export : exports) {
          if (export.callable() != null)
            issue(
                export.node(),
                "standalone executable export is outside interface-export compatibility");
          export
              .members()
              .forEach(
                  (name, signature) -> {
                    if (!name.startsWith("_"))
                      put(projected, export.name() + "." + name, signature);
                  });
        }
      } else
        functions.forEach(
            (name, signature) -> {
              if (!name.startsWith("_")) put(projected, name, signature);
            });
      issues.sort(
          Comparator.comparingInt(CompatibilityDiagnostic::line)
              .thenComparing(CompatibilityDiagnostic::symbol)
              .thenComparing(CompatibilityDiagnostic::explanation));
      return new Binding(
          new PythonContract(projected, issues, issues.isEmpty()),
          new HashSet<>(exports.stream().map(Export::name).toList()));
    }

    private static void put(Map<String, Function> target, String name, Signature signature) {
      if (signature != null)
        target.put(
            name,
            new Function(
                name,
                signature.function().line(),
                signature.function().parameters(),
                signature.function().returns()));
    }

    private ContractModel generate(CodegenConfig config) {
      if (exports.isEmpty()) {
        if (syntaxError) throw invalid(issues.getFirst());
        throw new IllegalStateException("No polyglot.export_value found");
      }
      issues.stream()
          .filter(
              i ->
                  i.explanation().contains("rebinds")
                      || i.explanation().contains("duplicate function declaration"))
          .findFirst()
          .ifPresent(
              i -> {
                throw invalid(i);
              });
      List<ContractClass> result = new ArrayList<>();
      Set<String> names = new HashSet<>();
      for (Export export : exports) {
        if (!names.add(export.name()))
          throw new IllegalStateException(
              "Duplicate contract name '"
                  + export.name()
                  + "' in polyglot.export_value declarations");
        if (export.invalid() || export.callable() != null)
          throw invalid(export.node(), "dynamic or non-interface export cannot be generated");
        List<Map.Entry<String, Signature>> members = new ArrayList<>(export.members().entrySet());
        if (export.dictionary()) {
          Map<String, Signature> legacy = new HashMap<>();
          export.members().forEach(legacy::put);
          List<String> aliasOrder = new ArrayList<>(legacy.keySet());
          members.sort(
              Comparator.<Map.Entry<String, Signature>>comparingInt(
                      e -> e.getValue().function().line())
                  .thenComparingInt(e -> aliasOrder.indexOf(e.getKey())));
        }
        List<ContractMethod> methods = new ArrayList<>();
        for (var entry : members) {
          Signature signature = entry.getValue();
          if (signature == null) throw invalid(export.node(), "invalid function signature");
          Function function = signature.function();
          Node declaration = signature.declaration();
          if (function.name().startsWith("_")
              || (config.onlyIncludedMethods()
                  && declaration.nodes("decorator_list").stream()
                      .noneMatch(d -> d.is("Name") && d.string("id").equals("adapter_include"))))
            continue;
          if (declaration.is("AsyncFunctionDef") || generator(declaration))
            throw invalid(declaration, "async or generator callable needs runtime semantics");
          if (function.requiredKeyword())
            throw invalid(
                declaration,
                "required keyword-only parameters cannot be supplied by Java positional invocation");
          var args = declaration.node("args");
          List<Node> positional = new ArrayList<>(args.nodes("posonlyargs"));
          positional.addAll(args.nodes("args"));
          if (signature.bound() && !positional.isEmpty()) positional.removeFirst();
          try {
            List<ContractParam> parameters =
                positional.stream()
                    .map(
                        p ->
                            new ContractParam(
                                p.string("arg"),
                                PythonAstTypes.annotation(p.node("annotation")).type()))
                    .toList();
            PolyType returns =
                declaration.node("returns").is("")
                    ? inferReturn(declaration)
                    : PythonAstTypes.annotation(declaration.node("returns")).type();
            methods.add(new ContractMethod(entry.getKey(), parameters, returns));
          } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                script.fileName()
                    + ": line "
                    + declaration.line()
                    + ", column "
                    + declaration.column()
                    + ": "
                    + function.name()
                    + ": "
                    + failure.getMessage(),
                failure);
          }
        }
        result.add(new ContractClass(export.name(), methods));
      }
      return new ContractModel(result);
    }

    private static PolyType inferReturn(Node declaration) {
      List<Node> statements = declaration.nodes("body");
      // Keep the released Object projection for Sphinx-documented, unannotated returns.
      // A Python return annotation remains the explicit way to refine that generated API.
      if (!statements.isEmpty()
          && docstring(statements.getFirst())
          && statements.getFirst().node("value").string("value").contains(":return"))
        return new PolyUnknown();
      return body(declaration)
          .filter(n -> n.is("Return"))
          .findFirst()
          .map(n -> PythonAstTypes.infer(n.node("value")))
          .orElse(new PolyUnknown());
    }

    private void issue(Node node, String explanation) {
      String symbol = node.string("name");
      if (symbol.isEmpty()) symbol = "<module>";
      issues.add(
          new CompatibilityDiagnostic(
              script.fileName(),
              node.line(),
              symbol,
              CompatibilityOutcome.UNKNOWN,
              CompatibilityDiagnostic.Category.UNSUPPORTED_SYNTAX,
              "column " + node.column() + ": " + explanation));
    }

    private IllegalArgumentException invalid(Node node, String message) {
      return new IllegalArgumentException(
          script.fileName()
              + ": line "
              + node.line()
              + ", column "
              + node.column()
              + ": "
              + message);
    }

    private IllegalArgumentException invalid(CompatibilityDiagnostic issue) {
      return new IllegalArgumentException(issue.format());
    }
  }
}
