import numpy as np
import polyglot


def _series(samples):
    values = np.asarray(list(samples), dtype=np.float64)
    if values.ndim != 1 or not 1 <= values.size <= 100000 or not np.all(np.isfinite(values)) or np.any(np.abs(values) > 1e100):
        raise ValueError("samples must be a non-empty finite numeric series")
    return values


class AnalyticsApi:
    def describe(self, samples):
        values = _series(samples)
        return {
            "count": int(values.size),
            "mean": float(np.mean(values)),
            "median": float(np.median(values)),
            "standardDeviation": float(np.std(values, ddof=0)),
            "minimum": float(np.min(values)),
            "maximum": float(np.max(values)),
            "p05": float(np.percentile(values, 5, method="linear")),
            "p95": float(np.percentile(values, 95, method="linear")),
            "numpyVersion": np.__version__,
        }

    def rolling(self, samples, window):
        values = _series(samples)
        if window < 1 or window > values.size:
            raise ValueError("window must be between 1 and the sample count")
        means = np.convolve(values, np.ones(window) / window, mode="valid")
        return {"window": int(window), "firstIndex": int(window - 1), "means": means.tolist()}


polyglot.export_value("AnalyticsApi", AnalyticsApi)
