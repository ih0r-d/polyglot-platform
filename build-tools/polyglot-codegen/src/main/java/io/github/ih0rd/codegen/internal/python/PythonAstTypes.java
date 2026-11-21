package io.github.ih0rd.codegen.internal.python;

import java.util.List;

import io.github.ih0rd.codegen.internal.python.PythonAstSession.Node;
import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

/** Existing portable mappings applied to official AST nodes, never to manually tokenized text. */
public final class PythonAstTypes {
  private PythonAstTypes() {}

  public record Mapping(PolyType type, boolean reliable) {}

  public static Mapping annotation(PythonAstSession session, String source) {
    var parsed = session.expression(source);
    return parsed.error() == null ? annotation(parsed.root().node("body")) : unknown();
  }

  static Mapping annotation(Node node) {
    if (node.is("Name"))
      return switch (node.string("id")) {
        case "int" -> new Mapping(PolyPrimitive.INT, true);
        case "float" -> new Mapping(PolyPrimitive.FLOAT, true);
        case "str" -> new Mapping(PolyPrimitive.STRING, true);
        case "bool" -> new Mapping(PolyPrimitive.BOOLEAN, true);
        case "list", "tuple" -> new Mapping(new PolyList(new PolyUnknown()), false);
        case "dict" -> new Mapping(new PolyMap(PolyPrimitive.STRING, new PolyUnknown()), false);
        case "set", "Set" -> throw unsafeSet();
        default -> unknown();
      };
    if (node.is("Subscript") && node.node("value").is("Name")) {
      String base = node.node("value").string("id");
      Node slice = node.node("slice");
      List<Node> args = slice.is("Tuple") ? slice.nodes("elts") : List.of(slice);
      return switch (base.toLowerCase(java.util.Locale.ROOT)) {
        case "set" -> throw unsafeSet();
        case "list", "tuple" -> {
          List<Mapping> mapped = args.stream().map(PythonAstTypes::annotation).toList();
          yield new Mapping(
              new PolyList(unify(mapped.stream().map(Mapping::type).toList(), false)),
              (base.equals("list") || base.equals("List"))
                  && mapped.size() == 1
                  && mapped.getFirst().reliable());
        }
        case "dict" -> {
          if (args.size() < 2)
            yield new Mapping(new PolyMap(PolyPrimitive.STRING, new PolyUnknown()), false);
          Mapping key = annotation(args.get(0)), value = annotation(args.get(1));
          yield new Mapping(
              new PolyMap(key.type(), value.type()),
              (base.equals("dict") || base.equals("Dict"))
                  && args.size() == 2
                  && key.reliable()
                  && value.reliable());
        }
        case "optional" ->
            base.equals("Optional")
                ? new Mapping(
                    args.isEmpty() ? new PolyUnknown() : annotation(args.getFirst()).type(), false)
                : unknown();
        default -> unknown();
      };
    }
    return unknown();
  }

  static PolyType infer(Node node) {
    if (node.is("Constant"))
      return switch (node.string("constantKind")) {
        case "int" -> PolyPrimitive.INT;
        case "float" -> PolyPrimitive.FLOAT;
        case "bool" -> PolyPrimitive.BOOLEAN;
        case "str" -> PolyPrimitive.STRING;
        default -> new PolyUnknown();
      };
    if (node.is("UnaryOp") && node.node("op").is("UAdd", "USub"))
      return infer(node.node("operand"));
    if (node.is("List"))
      return new PolyList(unify(node.nodes("elts").stream().map(PythonAstTypes::infer).toList()));
    if (node.is("Set", "SetComp")) throw unsafeSet();
    if (node.is("Dict"))
      return new PolyMap(
          PolyPrimitive.STRING,
          unify(node.nodes("values").stream().map(PythonAstTypes::infer).toList()));
    if (node.is("ListComp"))
      return new PolyList(
          node.node("elt").is("Dict") ? infer(node.node("elt")) : new PolyUnknown());
    if (node.is("DictComp")) return new PolyMap(PolyPrimitive.STRING, new PolyUnknown());
    if (node.is("Call") && node.node("func").is("Name")) {
      String name = node.node("func").string("id");
      var args = node.nodes("args");
      if (name.equals("set")) throw unsafeSet();
      if ((name.equals("list") || name.equals("tuple")) && args.size() == 1) {
        Node argument = args.getFirst();
        return argument.is("List", "Tuple", "Set")
            ? new PolyList(
                unify(argument.nodes("elts").stream().map(PythonAstTypes::infer).toList()))
            : infer(argument);
      }
      if (name.equals("dict"))
        return args.size() == 1 && args.getFirst().is("Dict")
            ? infer(args.getFirst())
            : new PolyMap(
                PolyPrimitive.STRING,
                unify(node.nodes("keywords").stream().map(k -> infer(k.node("value"))).toList()));
    }
    return new PolyUnknown();
  }

  static boolean literal(Node node) {
    if (node.is("Constant"))
      return List.of("str", "int", "float", "bool", "NoneType")
          .contains(node.string("constantKind"));
    return node.is("UnaryOp")
        && node.node("op").is("UAdd", "USub")
        && node.node("operand").is("Constant")
        && List.of("int", "float").contains(node.node("operand").string("constantKind"));
  }

  private static PolyType unify(List<PolyType> types) {
    return unify(types, true);
  }

  private static PolyType unify(List<PolyType> types, boolean inferMaps) {
    if (types.isEmpty()) return new PolyUnknown();
    PolyType result = types.getFirst();
    for (PolyType next : types.subList(1, types.size())) {
      if (result instanceof PolyPrimitive && result.equals(next)) continue;
      if (result instanceof PolyList left && next instanceof PolyList right) {
        result = new PolyList(unify(List.of(left.elementType(), right.elementType()), inferMaps));
      } else if (inferMaps && result instanceof PolyMap left && next instanceof PolyMap right) {
        result =
            new PolyMap(
                unify(List.of(left.keyType(), right.keyType()), true),
                unify(List.of(left.valueType(), right.valueType()), true));
      } else result = new PolyUnknown();
    }
    return result;
  }

  private static Mapping unknown() {
    return new Mapping(new PolyUnknown(), false);
  }

  private static IllegalArgumentException unsafeSet() {
    return new IllegalArgumentException(
        "Python set cannot be converted to the generated Java List; return a list or tuple instead");
  }
}
