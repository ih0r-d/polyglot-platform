package io.github.ih0rd.examples.dto;

import java.util.List;

/** Valid-window means in input order, detached from the Python Context. */
public record RollingResult(int window, int firstIndex, List<Double> means) {
  public RollingResult {
    means = List.copyOf(means);
  }
}
