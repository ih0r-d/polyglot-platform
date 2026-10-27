package io.github.ih0rd.examples.dto;

import java.util.List;

/** Input telemetry in time order; invalid or missing values are rejected by validation. */
public record SeriesRequest(List<Double> samples) {
  public SeriesRequest {
    samples = samples == null ? null : List.copyOf(samples);
  }
}
