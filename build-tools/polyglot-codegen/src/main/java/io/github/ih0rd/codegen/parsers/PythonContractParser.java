package io.github.ih0rd.codegen.parsers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.ih0rd.codegen.types.PythonTypeMapper;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractMethod;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.ContractParam;
import io.github.ih0rd.polyglot.model.config.CodegenConfig;
import io.github.ih0rd.polyglot.model.parser.LanguageParser;
import io.github.ih0rd.polyglot.model.parser.ScriptDescriptor;
import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

public final class PythonContractParser implements LanguageParser {

  private static final String PYTHON_DECORATOR_INCLUDE = "@adapter_include";

  private static final String S = "\\s*";
  private static final String W = "\\w+";
  private static final String Q = "[\"']";

  private static final Pattern EXPORT_START =
      Pattern.compile("polyglot\\.export_value" + S + "\\(" + S + Q + "(" + W + ")" + Q + S + ",");

  private static final Pattern CLASS_DEF =
      Pattern.compile("^" + S + "class" + S + "(" + W + ")" + S + ":");

  private static final Pattern DEF_START = Pattern.compile("^" + S + "def" + S + "(" + W + ")");

  private static final Pattern PARAM_PATTERN =
      Pattern.compile("^(" + W + ")(?:" + S + ":" + S + "([^=]+))?(?:" + S + "=.*)?$");

  private final PythonTypeMapper mapper = new PythonTypeMapper();

  @Override
  public SupportedLanguage language() {
    return SupportedLanguage.PYTHON;
  }

  @Override
  public ContractModel parse(ScriptDescriptor script, CodegenConfig config) {
    String source = script.source().stripIndent();
    String[] lines = source.split("\\R");

    ExportInfo export = findExport(source);
    if (export == null) {
      throw new IllegalStateException("No polyglot.export_value found");
    }

    List<ContractMethod> methods;
    if (export.isClass) {
      methods = parseClassMethods(lines, export.targetName, config);
    } else {
      methods = parseDictMethods(lines, export.dictMapping, config);
    }

    return new ContractModel(List.of(new ContractClass(export.apiName, methods)));
  }

  private record ExportInfo(
      String apiName, String targetName, Map<String, String> dictMapping, boolean isClass) {}

  private ExportInfo findExport(String source) {
    Matcher m = EXPORT_START.matcher(source);
    if (!m.find()) {
      return null;
    }

    String apiName = m.group(1);
    int startIdx = m.end();

    String remainder = source.substring(startIdx).trim();
    String arg2 = extractExpression(remainder);

    if (arg2.startsWith("{")) {
      Map<String, String> mapping = parseExportDict(arg2);
      return new ExportInfo(apiName, null, mapping, false);
    } else {
      String className = arg2.split("\\W")[0];
      return new ExportInfo(apiName, className, null, true);
    }
  }

  private String extractExpression(String s) {
    if (s.startsWith("{")) {
      return extractBalancedBlock(s, 0, '{', '}');
    }
    int comma = s.indexOf(',');
    int paren = s.indexOf(')');
    int end = (comma != -1 && comma < paren) ? comma : paren;
    if (end == -1) {
      return s.trim();
    }
    return s.substring(0, end).trim();
  }

  private Map<String, String> parseExportDict(String dictExpr) {
    Map<String, String> map = new HashMap<>();
    String inside = stripOuter(dictExpr, '{', '}');
    List<String> entries = splitTopLevelComma(inside);

    for (String entry : entries) {
      int colon = indexOfTopLevel(entry, ':');
      if (colon > 0) {
        String key = entry.substring(0, colon).trim();
        String val = entry.substring(colon + 1).trim();
        key = stripQuotes(key);
        map.put(key, val);
      }
    }
    return map;
  }

  private List<ContractMethod> parseClassMethods(
      String[] lines, String className, CodegenConfig config) {
    List<ContractMethod> methods = new ArrayList<>();
    boolean insideClass = false;
    int classIndent = -1;
    boolean includeNext = false;

    for (int i = 0; i < lines.length; i++) {
      String rawLine = lines[i];
      String trimmed = rawLine.trim();

      if (!insideClass) {
        Matcher m = CLASS_DEF.matcher(rawLine);
        if (m.find() && m.group(1).equals(className)) {
          insideClass = true;
          classIndent = indentLevel(rawLine);
        }
        continue;
      }

      if (!trimmed.isBlank() && indentLevel(rawLine) <= classIndent) {
        break;
      }

      if (trimmed.equals(PYTHON_DECORATOR_INCLUDE)) {
        includeNext = true;
        continue;
      }

      Matcher m = DEF_START.matcher(rawLine);
      if (!m.find()) {
        includeNext = false;
        continue;
      }

      String methodName = m.group(1);
      if (shouldSkip(methodName, config, includeNext)) {
        includeNext = false;
        continue;
      }
      includeNext = false;

      MethodSignature sig = parseSignature(lines, i);
      PolyType returnType = resolveReturnType(sig, lines, i + 1, indentLevel(rawLine));

      methods.add(new ContractMethod(methodName, sig.params, returnType));
    }
    return methods;
  }

  private List<ContractMethod> parseDictMethods(
      String[] lines, Map<String, String> mapping, CodegenConfig config) {
    List<ContractMethod> methods = new ArrayList<>();
    boolean includeNext = false;

    Map<String, List<String>> reverseMap = new HashMap<>();
    mapping.forEach(
        (exportName, internalName) ->
            reverseMap.computeIfAbsent(internalName, interf -> new ArrayList<>()).add(exportName));

    for (int i = 0; i < lines.length; i++) {
      String rawLine = lines[i];
      String trimmed = rawLine.trim();
      if (trimmed.equals(PYTHON_DECORATOR_INCLUDE)) {
        includeNext = true;
        continue;
      }

      Matcher m = DEF_START.matcher(rawLine);
      boolean found = m.find();
      int indent = indentLevel(rawLine);
      if (found && indent == 0) {
        String internalName = m.group(1);
        if (reverseMap.containsKey(internalName)) {
          if (shouldSkip(internalName, config, includeNext)) {
            includeNext = false;
            continue;
          }

          MethodSignature sig = parseSignature(lines, i);
          PolyType returnType = resolveReturnType(sig, lines, i + 1, indentLevel(rawLine));

          for (String exportName : reverseMap.get(internalName)) {
            methods.add(new ContractMethod(exportName, sig.params, returnType));
          }
          includeNext = false;
        }
      }

      if (!trimmed.startsWith("def ") && !trimmed.startsWith("@")) {
        includeNext = false;
      }
    }
    return methods;
  }

  private boolean shouldSkip(String name, CodegenConfig config, boolean hasDecorator) {
    return name.startsWith("_") || (config.onlyIncludedMethods() && !hasDecorator);
  }

  private record MethodSignature(List<ContractParam> params, String returnAnnotation) {}

  private MethodSignature parseSignature(String[] lines, int startLine) {
    StringBuilder sb = new StringBuilder();
    int lineIdx = startLine;

    while (lineIdx < lines.length) {
      String line = lines[lineIdx].trim();
      int comment = line.indexOf('#');
      if (comment >= 0) {
        line = line.substring(0, comment).trim();
      }

      sb.append(line);

      if (indexOfTopLevel(line, ':') >= 0) {
        break;
      }

      lineIdx++;
    }

    String sig = sb.toString();
    int openParen = sig.indexOf('(');
    if (openParen < 0) {
      return new MethodSignature(List.of(), null);
    }

    int arrow = sig.lastIndexOf("->");
    int closeParen = -1;

    if (arrow > 0) {
      closeParen = sig.substring(0, arrow).lastIndexOf(')');
    } else {
      int colon = sig.lastIndexOf(':');
      if (colon > 0) {
        closeParen = sig.substring(0, colon).lastIndexOf(')');
      }
    }

    if (closeParen < 0) {
      return new MethodSignature(List.of(), null);
    }

    String paramsStr = sig.substring(openParen + 1, closeParen);
    String returnStr =
        (arrow > 0 && sig.lastIndexOf(':') > arrow)
            ? sig.substring(arrow + 2, sig.lastIndexOf(':')).trim()
            : null;

    return new MethodSignature(parseParams(paramsStr), returnStr);
  }

  private PolyType resolveReturnType(
      MethodSignature sig, String[] lines, int bodyStart, int indent) {
    if (sig.returnAnnotation != null && !sig.returnAnnotation.isBlank()) {
      return mapper.mapPrimitive(sig.returnAnnotation);
    }
    return inferReturnType(lines, bodyStart, indent);
  }

  private List<ContractParam> parseParams(String raw) {
    List<ContractParam> params = new ArrayList<>();
    List<String> parts = splitTopLevelComma(raw);

    for (String part : parts) {
      String trimmed = part.trim();
      if (trimmed.equals("self") || trimmed.isBlank()) {
        continue;
      }

      Matcher matcher = PARAM_PATTERN.matcher(trimmed);
      if (matcher.find()) {
        String name = matcher.group(1);
        String typeHint = matcher.group(2);
        PolyType type = (typeHint != null) ? mapper.mapPrimitive(typeHint) : new PolyUnknown();
        params.add(new ContractParam(name, type));
      }
    }
    return params;
  }

  private PolyType inferReturnType(String[] lines, int start, int methodIndent) {
    for (int i = start; i < lines.length; i++) {
      String raw = lines[i];
      String trimmed = raw.trim();

      // stop when leaving method block
      if (!trimmed.isBlank() && indentLevel(raw) <= methodIndent) {
        break;
      }

      // skip comments
      if (trimmed.startsWith("#")) {
        continue;
      }

      // optional: skip simple docstring lines
      if (trimmed.startsWith("\"\"\"") || trimmed.startsWith("'''")) {
        continue;
      }

      int returnIdx = trimmed.indexOf("return");
      if (returnIdx < 0) {
        continue;
      }

      // ensure it's a real keyword (not part of identifier)
      if (returnIdx > 0 && Character.isJavaIdentifierPart(trimmed.charAt(returnIdx - 1))) {
        continue;
      }

      String expr = trimmed.substring(returnIdx + "return".length()).trim();
      if (expr.isEmpty()) {
        return new PolyUnknown();
      }

      if ((expr.startsWith("[") && notBalanced(expr, '[', ']'))
          || (expr.startsWith("{") && notBalanced(expr, '{', '}'))
          || (expr.startsWith("(") && notBalanced(expr, '(', ')'))) {

        char open = expr.charAt(0);
        char close = (open == '[') ? ']' : (open == '{') ? '}' : ')';
        expr =
            expr
                + "\n"
                + collectUntilBalanced(
                    lines, i + 1, methodIndent, open, close, balanceDelta(expr, open, close));
      }

      return inferExprType(expr);
    }
    return new PolyUnknown();
  }

  private PolyType inferExprType(String expr) {
    String e = expr.trim();

    // List literal: [ ... ]
    if (e.startsWith("[")) {
      return inferListType(stripOuter(e, '[', ']'));
    }

    // Dict or Set literal: { ... }
    if (e.startsWith("{")) {
      String inside = stripOuter(e, '{', '}');

      // {} → empty dict
      if (inside.isBlank()) {
        return new PolyMap(PolyPrimitive.STRING, new PolyUnknown());
      }

      // if contains top-level ':' → dict
      if (indexOfTopLevel(inside, ':') >= 0) {
        return inferMapType(e);
      }

      // otherwise it's a set → treat as List
      return new PolyList(unifyTypes(splitTopLevelComma(inside)));
    }

    // list(...), set(...), tuple(...)
    if (e.startsWith("list(") || e.startsWith("set(") || e.startsWith("tuple(")) {
      String arg = extractBalancedBlock(e, e.indexOf('('), '(', ')');
      arg = stripOuter(arg, '(', ')');
      return inferExprType(arg);
    }

    // dict(...)
    if (e.startsWith("dict(")) {
      String args = stripOuter(extractBalancedBlock(e, e.indexOf('('), '(', ')'), '(', ')');

      if (args.trim().startsWith("{")) {
        return inferMapType(args);
      }

      return inferDictConstructor(args);
    }

    // primitive literals
    return detectLiteralType(e);
  }

  private PolyType inferListType(String inside) {
    if (inside.isBlank()) {
      return new PolyList(new PolyUnknown());
    }

    if (inside.contains(" for ") && inside.contains(" in ")) {
      int forIdx = inside.indexOf(" for ");
      String body = inside.substring(0, forIdx).trim();
      if (body.startsWith("{") && body.endsWith("}")) {
        PolyType t = inferExprType(body);
        return new PolyList(t);
      }
      return new PolyList(new PolyUnknown());
    }

    List<String> elements = splitTopLevelComma(inside);
    PolyType unified = unifyTypes(elements);
    return new PolyList(unified);
  }

  private PolyType inferMapType(String expr) {
    String inside = stripOuter(expr, '{', '}');
    if (inside.contains(" for ") && inside.contains(" in ") && inside.indexOf(':') > 0) {
      return new PolyMap(PolyPrimitive.STRING, new PolyUnknown());
    }

    return inferDictValueType(inside);
  }

  private PolyType inferDictConstructor(String args) {
    List<String> entries = splitTopLevelComma(args);
    PolyType acc = null;
    for (String entry : entries) {
      int eq = indexOfTopLevel(entry, '=');
      if (eq < 0) {
        continue;
      }
      PolyType t = inferExprType(entry.substring(eq + 1).trim());
      acc = (acc == null) ? t : unify(acc, t);
    }
    PolyType val = (acc != null) ? acc : new PolyUnknown();
    return new PolyMap(PolyPrimitive.STRING, val);
  }

  private PolyType inferDictValueType(String inside) {
    List<String> entries = splitTopLevelComma(inside);
    PolyType acc = null;
    for (String entry : entries) {
      int colon = indexOfTopLevel(entry, ':');
      if (colon < 0) {
        continue;
      }
      PolyType t = inferExprType(entry.substring(colon + 1).trim());
      acc = (acc == null) ? t : unify(acc, t);
    }
    PolyType val = (acc != null) ? acc : new PolyUnknown();
    return new PolyMap(PolyPrimitive.STRING, val);
  }

  private PolyType unifyTypes(List<String> expressions) {
    PolyType acc = null;
    for (String expr : expressions) {
      PolyType t = inferExprType(expr);
      acc = (acc == null) ? t : unify(acc, t);
    }
    return (acc != null) ? acc : new PolyUnknown();
  }

  private PolyType unify(PolyType a, PolyType b) {
    if (a instanceof PolyPrimitive pa && b instanceof PolyPrimitive pb && pa == pb) {
      return pa;
    }
    if (a instanceof PolyList(PolyType elementType) && b instanceof PolyList(PolyType type)) {
      return new PolyList(unify(elementType, type));
    }
    if (a instanceof PolyMap(PolyType type, PolyType aValueType)
        && b instanceof PolyMap(PolyType keyType, PolyType bValueType)) {
      return new PolyMap(unify(type, keyType), unify(aValueType, bValueType));
    }
    return new PolyUnknown();
  }

  private PolyType detectLiteralType(String v) {
    if (v.matches("^-?\\d+$")) {
      return PolyPrimitive.INT;
    }
    if (v.matches("^-?\\d+\\.\\d+$")) {
      return PolyPrimitive.FLOAT;
    }
    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
      return PolyPrimitive.STRING;
    }
    if (v.equals("True") || v.equals("False")) {
      return PolyPrimitive.BOOLEAN;
    }
    return new PolyUnknown();
  }

  private int indentLevel(String line) {
    int count = 0;
    for (char c : line.toCharArray()) {
      if (c == ' ') {
        count++;
      } else if (c == '\t') {
        count += 4;
      } else {
        break;
      }
    }
    return count;
  }

  private String stripQuotes(String s) {
    if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  private String stripOuter(String s, char open, char close) {
    String t = s.trim();
    if (t.length() >= 2 && t.charAt(0) == open && t.charAt(t.length() - 1) == close) {
      return t.substring(1, t.length() - 1).trim();
    }
    return t;
  }

  private String collectUntilBalanced(
      String[] lines, int start, int methodIndent, char open, char close, int initialDelta) {
    StringBuilder sb = new StringBuilder();
    int balance = initialDelta;
    for (int i = start; i < lines.length; i++) {
      String raw = lines[i];
      String trimmed = raw.trim();
      if (!trimmed.isBlank() && indentLevel(raw) <= methodIndent) {
        break;
      }
      sb.append(trimmed).append(" ");
      balance += balanceDelta(trimmed, open, close);
      if (balance == 0) {
        break;
      }
    }
    return sb.toString().trim();
  }

  private boolean notBalanced(String s, char open, char close) {
    return balanceDelta(s, open, close) != 0;
  }

  private int balanceDelta(String s, char open, char close) {
    int b = 0;
    for (char c : s.toCharArray()) {
      if (c == open) {
        b++;
      }
      if (c == close) {
        b--;
      }
    }
    return b;
  }

  private String extractBalancedBlock(String s, int startIdx, char open, char close) {
    int balance = 0;
    StringBuilder sb = new StringBuilder();
    for (int i = startIdx; i < s.length(); i++) {
      char c = s.charAt(i);
      sb.append(c);
      if (c == open) {
        balance++;
      }
      if (c == close) {
        balance--;
      }
      if (balance == 0) {
        break;
      }
    }
    return sb.toString();
  }

  private List<String> splitTopLevelComma(String s) {
    List<String> parts = new ArrayList<>();
    if (s == null || s.isBlank()) {
      return parts;
    }

    StringBuilder cur = new StringBuilder();

    int[] brackets = new int[3]; // square, curly, round
    boolean[] inQuote = new boolean[1];
    char[] quoteChar = new char[1];

    for (int i = 0; i < s.length(); i++) {
      updateScanState(s, i, brackets, inQuote, quoteChar);

      char c = s.charAt(i);
      if (c == ',' && !inQuote[0] && brackets[0] == 0 && brackets[1] == 0 && brackets[2] == 0) {

        parts.add(cur.toString());
        cur.setLength(0);
        continue;
      }

      cur.append(c);
    }

    if (!cur.isEmpty()) {
      parts.add(cur.toString());
    }
    return parts;
  }

  private int indexOfTopLevel(String s, char target) {

    int[] brackets = new int[3];
    boolean[] inQuote = new boolean[1];
    char[] quoteChar = new char[1];

    for (int i = 0; i < s.length(); i++) {
      updateScanState(s, i, brackets, inQuote, quoteChar);

      char c = s.charAt(i);
      if (c == target && !inQuote[0] && brackets[0] == 0 && brackets[1] == 0 && brackets[2] == 0) {
        return i;
      }
    }

    return -1;
  }

  private void updateScanState(
      String s, int i, int[] brackets, boolean[] inQuote, char[] quoteChar) {
    char c = s.charAt(i);

    if ((c == '\'' || c == '"') && (i == 0 || s.charAt(i - 1) != '\\')) {
      if (!inQuote[0]) {
        inQuote[0] = true;
        quoteChar[0] = c;
      } else if (c == quoteChar[0]) {
        inQuote[0] = false;
      }
    }

    if (!inQuote[0]) {
      switch (c) {
        case '[' -> brackets[0]++;
        case ']' -> brackets[0]--;
        case '{' -> brackets[1]++;
        case '}' -> brackets[1]--;
        case '(' -> brackets[2]++;
        case ')' -> brackets[2]--;
        default -> {
          // No state change for other characters.
        }
      }
    }
  }
}
