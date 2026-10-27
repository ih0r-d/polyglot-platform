package io.github.ih0rd.examples.validation;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ih0rd.examples.dto.SeriesRequest;
import io.github.ih0rd.examples.exceptions.InvalidAnalyticsInputException;

/** Numerical input rules, independent of HTTP error representation. */
@Component
public final class AnalyticsInputValidator {
  private static final int MAX_SAMPLES = 100_000;
  private static final double MAX_ABSOLUTE_VALUE = 1e100;

  public List<Double> samples(SeriesRequest request) {
    if (request == null
        || request.samples() == null
        || request.samples().isEmpty()
        || request.samples().size() > MAX_SAMPLES) {
      throw new InvalidAnalyticsInputException("samples must contain 1 to 100000 values");
    }
    for (Double value : request.samples()) {
      if (!Double.isFinite(value) || Math.abs(value) > MAX_ABSOLUTE_VALUE) {
        throw new InvalidAnalyticsInputException(
            "samples must be finite numbers with absolute value <= 1e100");
      }
    }
    return request.samples();
  }

  public void window(int window, int sampleCount) {
    if (window < 1 || window > sampleCount) {
      throw new InvalidAnalyticsInputException("window must be between 1 and sample count");
    }
  }

  public void threshold(double threshold) {
    if (!Double.isFinite(threshold) || threshold <= 0) {
      throw new InvalidAnalyticsInputException("threshold must be finite and positive");
    }
  }
}
