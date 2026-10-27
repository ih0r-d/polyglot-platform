package io.github.ih0rd.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ih0rd.examples.dto.SeriesRequest;
import io.github.ih0rd.examples.exceptions.InvalidAnalyticsInputException;
import io.github.ih0rd.examples.validation.AnalyticsInputValidator;

class AnalyticsInputValidatorTest {
  private final AnalyticsInputValidator validator = new AnalyticsInputValidator();

  @Test
  void acceptsFiniteValuesAndInclusiveSizeAndMagnitudeBounds() {
    SeriesRequest values = new SeriesRequest(List.of(-1e100, 0.0, 1e100));
    assertThat(validator.samples(values)).containsExactly(-1e100, 0.0, 1e100);
    assertThat(validator.samples(new SeriesRequest(Collections.nCopies(100_000, 1.0))))
        .hasSize(100_000);
    validator.window(1, 3);
    validator.window(3, 3);
    validator.threshold(Double.MIN_VALUE);
    validator.threshold(Double.MAX_VALUE);
  }

  @Test
  void rejectsMissingEmptyAndOversizedSeries() {
    assertThatThrownBy(() -> validator.samples(null))
        .isInstanceOf(InvalidAnalyticsInputException.class);
    assertThatThrownBy(() -> validator.samples(new SeriesRequest(null)))
        .isInstanceOf(InvalidAnalyticsInputException.class);
    assertThatThrownBy(() -> validator.samples(new SeriesRequest(List.of())))
        .isInstanceOf(InvalidAnalyticsInputException.class);
    assertThatThrownBy(
            () -> validator.samples(new SeriesRequest(Collections.nCopies(100_001, 1.0))))
        .isInstanceOf(InvalidAnalyticsInputException.class);
  }

  @Test
  void rejectsNonFiniteAndOutOfRangeReadings() {
    for (double value :
        new double[] {
          Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e101, -1e101
        }) {
      assertThatThrownBy(() -> validator.samples(new SeriesRequest(List.of(value))))
          .isInstanceOf(InvalidAnalyticsInputException.class);
    }
  }

  @Test
  void rejectsInvalidQueryConstraints() {
    for (int window : new int[] {-1, 0, 4}) {
      assertThatThrownBy(() -> validator.window(window, 3))
          .isInstanceOf(InvalidAnalyticsInputException.class);
    }
    for (double threshold :
        new double[] {-1, 0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      assertThatThrownBy(() -> validator.threshold(threshold))
          .isInstanceOf(InvalidAnalyticsInputException.class);
    }
  }
}
