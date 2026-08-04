package io.github.ih0rd.codegen;

import java.util.HashSet;
import java.util.Set;

import javax.lang.model.SourceVersion;

import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractMethod;
import io.github.ih0rd.polyglot.model.ContractParam;

/** Validates that a parsed contract can be rendered as valid Java source. */
public final class JavaContractValidator {

  /** Creates a Java contract validator. */
  public JavaContractValidator() {
    // Stateless validator.
  }

  /**
   * Validates the Java package and all generated Java identifiers for one contract.
   *
   * @param contract parsed contract
   * @param basePackage target Java package
   * @throws JavaContractValidationException when the contract cannot be represented in Java
   */
  public void validate(ContractClass contract, String basePackage) {
    validatePackageName(basePackage);
    if (contract == null) {
      throw new JavaContractValidationException("Contract must not be null");
    }

    validateTypeName(contract.name(), "contract '" + contract.name() + "'");

    for (ContractMethod method : contract.methods()) {
      validateMethod(contract, method);
    }
  }

  private void validatePackageName(String basePackage) {
    if (basePackage == null || basePackage.isBlank()) {
      throw new JavaContractValidationException(
          "Invalid Java package name: value must not be blank");
    }

    String trimmed = basePackage.trim();
    if (!trimmed.equals(basePackage)) {
      throw new JavaContractValidationException(
          "Invalid Java package name '" + basePackage + "': surrounding whitespace is not allowed");
    }

    String[] parts = basePackage.split("\\.", -1);
    for (String part : parts) {
      validateIdentifier(part, "package segment '" + part + "' in package '" + basePackage + "'");
    }
  }

  private void validateTypeName(String name, String context) {
    validateIdentifier(name, context);
  }

  private void validateMethod(ContractClass contract, ContractMethod method) {
    if (method == null) {
      throw new JavaContractValidationException(
          "Invalid Java method in contract '" + contract.name() + "': method must not be null");
    }

    String methodContext = "method '" + contract.name() + "." + method.name() + "(...)'";
    validateIdentifier(method.name(), methodContext);

    Set<String> seenParameters = new HashSet<>();
    for (ContractParam param : method.params()) {
      if (param == null) {
        throw new JavaContractValidationException(
            "Invalid Java parameter in method '"
                + contract.name()
                + "."
                + method.name()
                + "(...)': parameter must not be null");
      }
      String parameterContext =
          "parameter '"
              + param.name()
              + "' in method '"
              + contract.name()
              + "."
              + method.name()
              + "(...)'";
      validateIdentifier(param.name(), parameterContext);
      if (!seenParameters.add(param.name())) {
        throw new JavaContractValidationException(
            "Duplicate Java parameter name '"
                + param.name()
                + "' in method '"
                + contract.name()
                + "."
                + method.name()
                + "(...)'");
      }
    }
  }

  private void validateIdentifier(String value, String context) {
    if (value == null || value.isBlank()) {
      throw new JavaContractValidationException("Invalid Java identifier for " + context);
    }
    if (!SourceVersion.isIdentifier(value)) {
      throw new JavaContractValidationException(
          "Invalid Java identifier '" + value + "' for " + context);
    }
    if (SourceVersion.isKeyword(value)) {
      throw new JavaContractValidationException(
          "Invalid Java identifier '"
              + value
              + "' for "
              + context
              + ": Java keyword or reserved literal is not allowed");
    }
  }
}
