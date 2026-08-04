package io.github.ih0rd.codegen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.ih0rd.polyglot.model.ContractClass;
import io.github.ih0rd.polyglot.model.ContractMethod;
import io.github.ih0rd.polyglot.model.ContractParam;
import io.github.ih0rd.polyglot.model.types.PolyPrimitive;

class JavaContractValidatorTest {

  private final JavaContractValidator validator = new JavaContractValidator();

  @ParameterizedTest
  @ValueSource(strings = {"com.example-bad", "com.example bad", "com.1example", "com.class"})
  void validate_ShouldRejectInvalidPackageName(String basePackage) {
    ContractClass contract = contract("ValidApi", method("call", param("value")));

    JavaContractValidationException exception =
        assertThrows(
            JavaContractValidationException.class, () -> validator.validate(contract, basePackage));

    assertTrue(exception.getMessage().contains("package"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Bad-Api", "Bad Api", "1Api", "class"})
  void validate_ShouldRejectInvalidContractName(String contractName) {
    ContractClass contract = contract(contractName, method("call", param("value")));

    JavaContractValidationException exception =
        assertThrows(
            JavaContractValidationException.class, () -> validator.validate(contract, "com.demo"));

    assertTrue(exception.getMessage().contains("contract '" + contractName + "'"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"bad-method", "bad method", "1method"})
  void validate_ShouldRejectInvalidMethodName(String methodName) {
    ContractClass contract = contract("ValidApi", method(methodName, param("value")));

    JavaContractValidationException exception =
        assertThrows(
            JavaContractValidationException.class, () -> validator.validate(contract, "com.demo"));

    assertTrue(exception.getMessage().contains("method 'ValidApi." + methodName + "(...)'"));
  }

  @Test
  void validate_ShouldRejectJavaKeywordMethodName() {
    ContractClass contract = contract("ValidApi", method("class", param("value")));

    JavaContractValidationException exception =
        assertThrows(
            JavaContractValidationException.class, () -> validator.validate(contract, "com.demo"));

    assertTrue(exception.getMessage().contains("method 'ValidApi.class(...)'"));
    assertTrue(exception.getMessage().contains("keyword"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"bad-param", "bad param", "1param"})
  void validate_ShouldRejectInvalidParameterName(String parameterName) {
    ContractClass contract = contract("ValidApi", method("call", param(parameterName)));

    JavaContractValidationException exception =
        assertThrows(
            JavaContractValidationException.class, () -> validator.validate(contract, "com.demo"));

    assertTrue(
        exception
            .getMessage()
            .contains("parameter '" + parameterName + "' in method 'ValidApi.call(...)'"));
  }

  @Test
  void validate_ShouldRejectJavaKeywordParameterName() {
    ContractClass contract = contract("ValidApi", method("call", param("for")));

    JavaContractValidationException exception =
        assertThrows(
            JavaContractValidationException.class, () -> validator.validate(contract, "com.demo"));

    assertTrue(exception.getMessage().contains("parameter 'for' in method 'ValidApi.call(...)'"));
    assertTrue(exception.getMessage().contains("keyword"));
  }

  @Test
  void validate_ShouldAcceptUnicodeJavaIdentifiers() {
    ContractClass contract = contract("СервісApi", method("привіт", param("значення")));

    assertDoesNotThrow(() -> validator.validate(contract, "com.demo"));
  }

  @Test
  void validate_ShouldRejectDuplicateParameterNames() {
    ContractClass contract = contract("ValidApi", method("call", param("value"), param("value")));

    JavaContractValidationException exception =
        assertThrows(
            JavaContractValidationException.class, () -> validator.validate(contract, "com.demo"));

    assertTrue(exception.getMessage().contains("Duplicate Java parameter name 'value'"));
    assertTrue(exception.getMessage().contains("method 'ValidApi.call(...)'"));
  }

  private static ContractClass contract(String name, ContractMethod... methods) {
    return new ContractClass(name, List.of(methods));
  }

  private static ContractMethod method(String name, ContractParam... params) {
    return new ContractMethod(name, List.of(params), PolyPrimitive.STRING);
  }

  private static ContractParam param(String name) {
    return new ContractParam(name, PolyPrimitive.INT);
  }
}
