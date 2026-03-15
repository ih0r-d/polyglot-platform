package io.github.ih0rd.examples.contracts;

import java.util.List;
import java.util.Map;

public interface BrokenForecastService {
    Map<String, Object> forecast(List<Double> data, int steps, int period);
}

