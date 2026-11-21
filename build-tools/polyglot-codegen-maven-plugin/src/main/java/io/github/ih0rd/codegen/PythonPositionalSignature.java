package io.github.ih0rd.codegen;

import io.github.ih0rd.codegen.compatibility.PythonContract.Function;

/** Java positional arity projected from codegen's shared Python signature metadata. */
record PythonPositionalSignature(int required, int maximum, int line) {
  static PythonPositionalSignature from(Function function) {
    return new PythonPositionalSignature(function.required(), function.maximum(), function.line());
  }

  String range() {
    return required + ".." + (maximum == Integer.MAX_VALUE ? "*" : maximum);
  }
}
