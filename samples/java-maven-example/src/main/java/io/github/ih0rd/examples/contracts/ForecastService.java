package io.github.ih0rd.examples.contracts;

import java.util.List;
import java.util.Map;

public interface ForecastService {
    /**
     * Fits a trend + seasonality model and returns forecast + model info as a map.
     *
     * @param y list of numeric values (historical data)
     * @param steps number of future points to predict
     * @param seasonPeriod assumed period of seasonality (e.g. 4 for quarterly)
     * @return a map containing:
     *         "forecast" -> List<Double>,
     *         "slope" -> Double,
     *         "intercept" -> Double,
     *         "season_amp" -> Double,
     *         "season_period" -> Integer
     */
    Map<String, Object> forecast(List<Double> y, int steps, int seasonPeriod);
}
