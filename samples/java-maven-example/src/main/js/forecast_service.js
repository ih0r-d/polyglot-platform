// forecast_service.js

/// Simple JavaScript forecast implementation for GraalJS.
/// Mirrors the ForecastService Java interface.
///
/// Java signature:
///   Map<String, Object> forecast(List<Double> data, int steps, int period);

function forecast(data, steps, period) {
  if (!Array.isArray(data) || data.length === 0) {
    return {
      forecast: [],
      avg: 0,
      trend: 0
    };
  }

  // Basic average
  let sum = 0;
  for (let i = 0; i < data.length; i++) {
    sum += data[i];
  }
  const avg = sum / data.length;

  // Very naive "trend" – difference between last and first / length
  const trend = (data[data.length - 1] - data[0]) / data.length;

  // Build simple linear forecast: avg + trend * k
  const result = [];
  for (let k = 1; k <= steps; k++) {
    result.push(avg + trend * k);
  }

  return {
    forecast: result,
    avg: avg,
    trend: trend,
    period: period
  };
}
