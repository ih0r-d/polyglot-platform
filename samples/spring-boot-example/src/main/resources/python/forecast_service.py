import numpy as np
import polyglot

class ForecastService:
    """
    Combined trend + seasonality forecaster using NumPy.
    Exposes a single method for clean interop with Java (returns a map).
    """

    def __init__(self):
        self.trend_coef = None
        self.season_amp = None
        self.season_period = None

    def forecast(self, y, steps, season_period=4):
        """
        Fits a trend + seasonal model and returns forecast + model info as a map.
        :param y: list or np.ndarray of numeric values
        :param steps: number of future points to predict
        :param season_period: assumed period of seasonality (e.g. 4 for quarterly)
        :return: dict {forecast, slope, intercept, season_amp, season_period}
        """
        print("PYTHON STARTED FORECAST_SERVICE")

        y = np.array(y, dtype=float)
        n = len(y)
        x = np.arange(n)

        # --- Trend estimation ---
        a, b = np.polyfit(x, y, 1)
        trend = a * x + b

        # --- Seasonality estimation ---
        detrended = y - trend
        self.season_amp = np.mean(np.abs(detrended))
        self.season_period = season_period

        # --- Forecast ---
        future_x = np.arange(n, n + steps)
        seasonal = self.season_amp * np.sin(2 * np.pi * future_x / season_period)
        forecast_trend = a * future_x + b
        forecast = forecast_trend + seasonal

        # Save internal state
        self.trend_coef = (a, b)

        # Return as map (dict -> Java Map)
        return {
            "forecast": forecast.tolist(),
            "slope": float(a),
            "intercept": float(b),
            "season_amp": float(self.season_amp),
            "season_period": int(self.season_period),
        }


# Export class for Java interop
polyglot.export_value('ForecastService', ForecastService)
