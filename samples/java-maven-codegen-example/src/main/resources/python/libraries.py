import polyglot
from faker import Faker
from tabulate import tabulate


class Libraries:

  def __init__(self):
    self.fake = Faker()

  def formatUsers(self, n: int = 5) -> str:
    users = [
      {
        "name": self.fake.name(),
        "email": self.fake.email(),
        "country": self.fake.country()
      }
      for _ in range(n)
    ]

    headers = ["Name", "Email", "Country"]
    data = [[u["name"], u["email"], u["country"]]
            for u in users]
    return tabulate(data, headers=headers,
                    tablefmt="grid")


polyglot.export_value("Libraries", Libraries)
