package io.github.ih0rd.codegen;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Internal arity metadata absent from the generation model; never evaluates default expressions.
 */
record PythonPositionalSignature(int required, int maximum, int line) {

  static PythonPositionalSignature parse(String declaration, Path file, int line) {
    List<Parameter> parameters = parameters(declaration, file, line);
    if (parameters.isEmpty()) {
      throw unsupported(file, line, "instance method must declare a receiver: " + declaration);
    }
    Set<String> names = new HashSet<>();
    boolean optionalSeen = false;
    int required = 0;
    for (int i = 0; i < parameters.size(); i++) {
      Parameter parameter = parameters.get(i);
      String text = parameter.text().strip();
      if (text.startsWith("*") || text.equals("/")) {
        throw unsupported(
            file, line, "variadic, keyword-only or positional-only parameters: " + declaration);
      }
      String head =
          parameter.optional() ? text.substring(0, parameter.equalsIndex()).strip() : text;
      int colon = head.indexOf(':');
      String name = (colon < 0 ? head : head.substring(0, colon)).strip();
      if (!name.matches("[A-Za-z_][A-Za-z_0-9]*")
          || !names.add(name)
          || (colon >= 0 && head.substring(colon + 1).isBlank())
          || (parameter.optional() && text.substring(parameter.equalsIndex() + 1).isBlank())) {
        throw unsupported(file, line, "cannot establish parameter declaration: " + text);
      }
      if (!parameter.optional() && optionalSeen) {
        throw unsupported(file, line, "required parameter follows a default: " + name);
      }
      optionalSeen |= parameter.optional();
      // Binding supplies the first parameter regardless of its name or annotation.
      if (i > 0 && !parameter.optional()) required++;
    }
    return new PythonPositionalSignature(required, parameters.size() - 1, line);
  }

  private record Parameter(String text, int equalsIndex) {
    boolean optional() {
      return equalsIndex >= 0;
    }
  }

  private static List<Parameter> parameters(String declaration, Path file, int line) {
    List<Parameter> result = new ArrayList<>();
    ArrayDeque<Character> closing = new ArrayDeque<>();
    closing.push(')');
    int start = declaration.indexOf('(') + 1;
    int equalsIndex = -1;
    char quote = 0;
    for (int i = start; i < declaration.length(); i++) {
      char c = declaration.charAt(i);
      if (quote != 0) {
        if (c == '\\') i++;
        else if (c == quote) quote = 0;
        continue;
      }
      if (c == '\'' || c == '"') {
        int prefixStart = i;
        while (prefixStart > start && Character.isLetter(declaration.charAt(prefixStart - 1)))
          prefixStart--;
        if (declaration
            .substring(prefixStart, i)
            .toLowerCase(java.util.Locale.ROOT)
            .contains("f")) {
          throw unsupported(file, line, "formatted string expressions in signatures");
        }
        quote = c;
      } else if (c == '#'
          || (declaration.startsWith("lambda", i)
              && (i == start || !Character.isJavaIdentifierPart(declaration.charAt(i - 1)))
              && (i + 6 == declaration.length()
                  || !Character.isJavaIdentifierPart(declaration.charAt(i + 6))))) {
        throw unsupported(file, line, "comments or lambda expressions in signatures");
      } else if (c == '(' || c == '[' || c == '{') {
        int previous = i - 1;
        while (previous >= start && Character.isWhitespace(declaration.charAt(previous)))
          previous--;
        if (c == '('
            && previous >= start
            && (Character.isJavaIdentifierPart(declaration.charAt(previous))
                || ")]\"'".indexOf(declaration.charAt(previous)) >= 0)) {
          throw unsupported(file, line, "call expressions in signatures");
        }
        closing.push(c == '(' ? ')' : c == '[' ? ']' : '}');
      } else if (c == ')' || c == ']' || c == '}') {
        if (closing.pop() != c) throw unsupported(file, line, "unbalanced signature delimiters");
        if (closing.isEmpty()) {
          if (declaration.substring(i + 1).contains("(")) {
            throw unsupported(
                file, line, "call or parenthesized expressions in return annotations");
          }
          String tail = declaration.substring(start, i).strip();
          if (!tail.isEmpty()) result.add(new Parameter(tail, equalsIndex));
          return result;
        }
      } else if (closing.size() == 1 && c == ',') {
        String text = declaration.substring(start, i).strip();
        if (text.isEmpty()) throw unsupported(file, line, "empty parameter");
        result.add(new Parameter(text, equalsIndex));
        start = i + 1;
        equalsIndex = -1;
      } else if (closing.size() == 1 && c == '=' && equalsIndex < 0) {
        equalsIndex = declaration.substring(start, i).stripLeading().length();
      }
    }
    throw unsupported(file, line, "unterminated signature");
  }

  private static IllegalArgumentException unsupported(Path file, int line, String reason) {
    return new IllegalArgumentException(
        file + ":" + line + ": Unsupported static Python signature: " + reason);
  }
}
