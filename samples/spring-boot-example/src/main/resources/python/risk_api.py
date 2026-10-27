import numpy as np


def assess(samples, threshold):
    values = np.asarray(list(samples), dtype=np.float64)
    if values.ndim != 1 or not 1 <= values.size <= 100000 or not np.all(np.isfinite(values)) or np.any(np.abs(values) > 1e100):
        raise ValueError("samples must be a non-empty finite numeric series")
    if not np.isfinite(threshold) or threshold <= 0:
        raise ValueError("threshold must be finite and positive")
    deviation = float(np.std(values, ddof=0))
    scores = np.zeros(values.size) if deviation == 0 else (values - np.mean(values)) / deviation
    indices = np.flatnonzero(np.abs(scores) > threshold)
    risk = 100.0 * int(indices.size) / int(values.size)
    return {
        "threshold": float(threshold),
        "zScores": scores.tolist(),
        "anomalyIndices": indices.tolist(),
        "anomalyCount": int(indices.size),
        "riskScore": risk,
        "qualityScore": 100.0 - risk,
    }


def fail():
    raise RuntimeError("Synthetic analytics failure")
