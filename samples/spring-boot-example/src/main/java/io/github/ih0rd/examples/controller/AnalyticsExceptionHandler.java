package io.github.ih0rd.examples.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.github.ih0rd.adapter.exceptions.InvocationException;
import io.github.ih0rd.examples.dto.ApiError;
import io.github.ih0rd.examples.exceptions.InvalidAnalyticsInputException;

/** Safe analytics-only error translation; legacy endpoints retain their contracts. */
@RestControllerAdvice(assignableTypes = AnalyticsController.class)
public class AnalyticsExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsExceptionHandler.class);
  private static final String INVALID_INPUT = "INVALID_INPUT";

  @ExceptionHandler(InvalidAnalyticsInputException.class)
  public ResponseEntity<ApiError> invalid(InvalidAnalyticsInputException error) {
    return badRequest(error.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> malformed() {
    return badRequest("Expected JSON object with a numeric samples array");
  }

  @ExceptionHandler(InvocationException.class)
  public ResponseEntity<ApiError> guestFailure() {
    LOGGER.warn("Python analytics invocation failed; returning a sanitized HTTP error");
    return ResponseEntity.internalServerError()
        .body(
            new ApiError(
                "PYTHON_EXECUTION_FAILED",
                "Python analytics failed; input and guest details are not exposed"));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiError> invalidParameter(MethodArgumentTypeMismatchException error) {
    return badRequest("Invalid numeric query parameter: " + error.getName());
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException error) {
    return badRequest("Missing query parameter: " + error.getParameterName());
  }

  private ResponseEntity<ApiError> badRequest(String message) {
    return ResponseEntity.badRequest().body(new ApiError(INVALID_INPUT, message));
  }
}
