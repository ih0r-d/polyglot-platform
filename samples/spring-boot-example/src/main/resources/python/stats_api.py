# import numpy as np
from tabulate import tabulate
import polyglot


class StatsApi:
    def __init__(self):
        print("stats init")

    def randomNumbers(self, n: int = 10):
        # return np.random.randint(1, 100, size=n).tolist()
        return [1,2,3]

    def stats(self, n: int = 10):
        # arr = np.array(self.randomNumbers(n))
        return {
          "min": 1,
          "max": 3,
          "mean": 2,
          "median": 2
        #   "min": int(arr.min()),
          #           "max": int(arr.max()),
          #           "mean": float(arr.mean()),
          #           "median": float(np.median(arr))
        }

    def formatStats(self, n: int = 10) -> str:
        s = self.stats(n)
        data = [
          ["min", s["min"]],
          ["max", s["max"]],
          ["mean", s["mean"]],
          ["median", s["median"]]
        ]
        return tabulate(data, headers=["Metric", "Value"], tablefmt="grid")


polyglot.export_value("StatsApi", StatsApi)
