package io.github.ih0rd.codegen.internal.python;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

/** Owns one isolated AST interpreter per batch. Only detached Java nodes leave the monitor. */
public final class PythonAstSession implements AutoCloseable {
  private Context context;
  private Engine engine;
  private Value ast;
  private boolean closed;

  record SyntaxError(int line, int column, String message) {}

  record Parsed(Node root, SyntaxError error) {}

  /**
   * Detached view of the official AST fields, without a guest object or separate syntax grammar.
   */
  record Node(
      String kind, Map<String, Object> fields, int line, int column, String text, String source) {
    static final Node NONE = new Node("", Map.of(), 1, 1, "", "");

    boolean is(String... kinds) {
      return List.of(kinds).contains(kind);
    }

    Node node(String field) {
      return fields.get(field) instanceof Node n ? n : NONE;
    }

    String string(String field) {
      return fields.get(field) instanceof String s ? s : "";
    }

    List<Node> nodes(String field) {
      if (!(fields.get(field) instanceof List<?> list)) return List.of();
      return list.stream().map(item -> item instanceof Node n ? n : NONE).toList();
    }

    List<Node> children() {
      List<Node> children = new ArrayList<>();
      fields
          .values()
          .forEach(
              value -> {
                if (value instanceof Node n) children.add(n);
                else if (value instanceof List<?> list)
                  list.forEach(
                      item -> {
                        if (item instanceof Node n) children.add(n);
                      });
              });
      return children;
    }
  }

  private void initialize() {
    if (closed) throw new IllegalStateException("Python AST session is closed");
    if (context != null) return;
    try {
      engine =
          Engine.newBuilder("python")
              .useSystemProperties(false)
              .option("engine.WarnInterpreterOnly", "false")
              .build();
      context =
          Context.newBuilder("python")
              .engine(engine)
              .allowHostAccess(HostAccess.NONE)
              .allowHostClassLookup(name -> false)
              .allowIO(IOAccess.NONE)
              .allowCreateThread(false)
              .allowNativeAccess(false)
              .option("python.IsolateFlag", "true")
              .option("python.NoSiteFlag", "true")
              .option("python.IgnoreEnvironmentFlag", "true")
              .option("python.DontWriteBytecodeFlag", "true")
              .build();
      // The only evaluated Python source is this fixed standard-library import.
      context.eval("python", "import ast");
      ast = context.getBindings("python").getMember("ast");
    } catch (RuntimeException failure) {
      try {
        close();
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw new IllegalStateException(
          "Cannot initialize GraalPy AST analysis; check managed dependencies and the Java toolchain",
          failure);
    }
  }

  synchronized Parsed module(String source, String file) {
    return parse(source, sourceName(file), "exec");
  }

  static String sourceName(String file) {
    return file == null ? "<python>" : file;
  }

  synchronized Parsed expression(String source) {
    return parse(source, "<annotation>", "eval");
  }

  private Parsed parse(String source, String file, String mode) {
    initialize();
    try {
      Value tree = ast.getMember("parse").execute(source, file, mode);
      return new Parsed((Node) copy(tree, source, false), null);
    } catch (PolyglotException failure) {
      Value error = failure.getGuestObject();
      if (error != null && error.hasMember("lineno") && error.hasMember("msg")) {
        return new Parsed(
            Node.NONE,
            new SyntaxError(
                integer(error, "lineno", 1),
                integer(error, "offset", 1),
                error.getMember("msg").asString()));
      }
      throw new IllegalStateException("GraalPy AST analysis failed for " + file, failure);
    }
  }

  private Object copy(Value value, String source, boolean includeText) {
    if (value == null || value.isNull()) return null;
    if (value.hasMember("_fields")) {
      String kind = value.getMetaObject().getMetaSimpleName();
      Map<String, Object> fields = new LinkedHashMap<>();
      Value names = value.getMember("_fields");
      for (long i = 0; i < names.getArraySize(); i++) {
        String name = names.getArrayElement(i).asString();
        Value child = value.getMember(name);
        if (kind.equals("Constant") && name.equals("value")) {
          fields.put(
              "constantKind",
              child.isNull() ? "NoneType" : child.getMetaObject().getMetaSimpleName());
          if (child.isString()) fields.put("value", child.asString());
        } else {
          boolean text =
              Set.of("annotation", "returns", "defaults", "kw_defaults").contains(name)
                  || (kind.equals("Expression") && name.equals("body"));
          fields.put(name, copy(child, source, text));
        }
      }
      String text = "";
      String segment = "";
      if (includeText) {
        text = ast.getMember("unparse").execute(value).asString();
        Value original = ast.getMember("get_source_segment").execute(source, value);
        segment = original.isNull() ? text : original.asString();
      }
      return new Node(
          kind,
          Collections.unmodifiableMap(fields),
          integer(value, "lineno", 1),
          integer(value, "col_offset", 0) + 1,
          text,
          segment);
    }
    if (value.isString()) return value.asString();
    if (value.isBoolean()) return value.asBoolean();
    if (value.fitsInInt()) return value.asInt();
    if (value.hasArrayElements()) {
      List<Object> result = new ArrayList<>();
      for (long i = 0; i < value.getArraySize(); i++)
        result.add(copy(value.getArrayElement(i), source, includeText));
      return Collections.unmodifiableList(result);
    }
    throw new IllegalStateException("Unexpected field in official Python AST");
  }

  private static int integer(Value value, String field, int fallback) {
    Value member = value.getMember(field);
    return member == null || member.isNull() ? fallback : member.asInt();
  }

  public boolean literal(String source) {
    Parsed parsed = expression(source);
    return parsed.error() == null && PythonAstTypes.literal(parsed.root().node("body"));
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    RuntimeException failure = null;
    try {
      if (context != null) context.close();
    } catch (RuntimeException cleanup) {
      failure = cleanup;
    }
    try {
      if (engine != null) engine.close();
    } catch (RuntimeException cleanup) {
      if (failure == null) failure = cleanup;
      else failure.addSuppressed(cleanup);
    } finally {
      context = null;
      engine = null;
      ast = null;
    }
    if (failure != null) throw failure;
  }
}
