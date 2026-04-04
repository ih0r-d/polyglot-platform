package io.github.ih0rd.codegen;

import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractMethod;
import io.github.ih0rd.polyglot.model.ContractModel;
import io.github.ih0rd.polyglot.model.ContractParam;
import io.github.ih0rd.polyglot.model.types.PolyList;
import io.github.ih0rd.polyglot.model.types.PolyMap;
import io.github.ih0rd.polyglot.model.types.PolyObject;
import io.github.ih0rd.polyglot.model.types.PolyType;
import io.github.ih0rd.polyglot.model.types.PolyUnion;
import io.github.ih0rd.polyglot.model.types.PolyUnknown;

/** Validation helpers for generated contract models. */
public final class ContractModelValidator {

  private ContractModelValidator() {}

  /**
   * Fails when the contract model contains unknown types.
   *
   * @param model generated model
   */
  public static void requireNoUnknownTypes(ContractModel model) {
    for (ContractClass contractClass : model.classes()) {
      for (ContractMethod method : contractClass.methods()) {
        if (containsUnknown(method.returnType())) {
          throw new IllegalStateException(
              "Unknown return type in contract "
                  + contractClass.name()
                  + "."
                  + method.name()
                  + "()");
        }
        for (ContractParam param : method.params()) {
          if (containsUnknown(param.type())) {
            throw new IllegalStateException(
                "Unknown parameter type in contract "
                    + contractClass.name()
                    + "."
                    + method.name()
                    + "(...) for parameter '"
                    + param.name()
                    + "'");
          }
        }
      }
    }
  }

  private static boolean containsUnknown(PolyType type) {
    if (type instanceof PolyUnknown) {
      return true;
    }
    if (type instanceof PolyList list) {
      return containsUnknown(list.elementType());
    }
    if (type instanceof PolyMap map) {
      return containsUnknown(map.keyType()) || containsUnknown(map.valueType());
    }
    if (type instanceof PolyObject object) {
      return object.fields().values().stream().anyMatch(ContractModelValidator::containsUnknown);
    }
    if (type instanceof PolyUnion union) {
      return union.variants().stream().anyMatch(ContractModelValidator::containsUnknown);
    }
    return false;
  }
}
