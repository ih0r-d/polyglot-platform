package io.github.ih0rd.examples.exceptions;

/** An analytics input violates a documented numerical or size constraint. */
public final class InvalidAnalyticsInputException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public InvalidAnalyticsInputException(String message) {
    super(message);
  }
}
