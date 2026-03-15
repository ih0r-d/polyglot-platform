import polyglot
from faker import Faker
from tabulate import tabulate

_fake = Faker()


def formatUsers(n: int = 5) -> str:
  users = [
    {
      "name": _fake.name(),
      "email": _fake.email(),
      "country": _fake.country()
    }
    for _ in range(n)
  ]

  headers = ["Name", "Email", "Country"]
  data = [[u["name"], u["email"], u["country"]]
          for u in users]
  return tabulate(data, headers=headers,
                  tablefmt="grid")


polyglot.export_value(
    "LibrariesModule",
    {
      "formatUsers": formatUsers
    }
)
