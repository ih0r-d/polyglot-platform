package io.github.ih0rd.adapter.context;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import io.github.ih0rd.polyglot.SupportedLanguage;

/** Captures only the supported binding names touched by one explicit contract reload. */
final class PythonReloadBindings {
  private final Value exported;
  private final Value globals;
  private final Value definesGlobal;
  private final Map<String, Value> previousExports;
  private final Map<String, Value> previousGlobals;

  PythonReloadBindings(Context context, Set<String> names) {
    exported = context.getPolyglotBindings();
    globals = context.getBindings(SupportedLanguage.PYTHON.id());
    definesGlobal =
        context.eval(
            SupportedLanguage.PYTHON.id(), "lambda name, namespace=globals(): name in namespace");
    previousExports = capture(exported, names);
    previousGlobals = new LinkedHashMap<>();
    for (String name : names) {
      previousGlobals.put(
          name, definesGlobal.execute(name).asBoolean() ? globals.getMember(name) : null);
    }
  }

  boolean contains(String name) {
    return exported.getMember(name) != null || definesGlobal.execute(name).asBoolean();
  }

  void clear() {
    previousExports.keySet().forEach(exported::removeMember);
    previousGlobals.keySet().forEach(globals::removeMember);
  }

  void restore() {
    restore(exported, previousExports);
    restore(globals, previousGlobals);
  }

  private static Map<String, Value> capture(Value bindings, Set<String> names) {
    Map<String, Value> values = new LinkedHashMap<>();
    names.forEach(name -> values.put(name, bindings.getMember(name)));
    return values;
  }

  private static void restore(Value bindings, Map<String, Value> previous) {
    previous.forEach(
        (name, value) -> {
          if (value == null) {
            bindings.removeMember(name);
          } else {
            bindings.putMember(name, value);
          }
        });
  }
}
