import random
import statistics
from tabulate import tabulate
import polyglot


class StatsApi:
    def __init__(self):
        self._random = random.Random()

    def randomNumbers(self, n: int = 10):
        count = max(1, n)
        return [self._random.randint(1, 100) for _ in range(count)]

    def stats(self, n: int = 10):
        values = self.randomNumbers(n)
        return {
          "min": min(values),
          "max": max(values),
          "mean": statistics.fmean(values),
          "median": statistics.median(values),
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
