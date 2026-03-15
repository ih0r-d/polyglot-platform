from faker import Faker
from tabulate import tabulate
import polyglot

class LibrariesApi:
    def __init__(self):
        self.fake = Faker()

    def genUsers(self, n: int = 5):
        return [
            {"name": self.fake.name(), "email": self.fake.email(), "country": self.fake.country()}
            for _ in range(n)
        ]

    def formatUsers(self, n: int = 5) -> str:
        users = self.genUsers(n)
        data = [[u["name"], u["email"], u["country"]] for u in users]
        return tabulate(data, headers=["Name", "Email", "Country"], tablefmt="grid")

    def fakeParagraphs(self, n: int = 3) -> str:
        return "\n\n".join(self.fake.paragraph() for _ in range(n))

polyglot.export_value("LibrariesApi", LibrariesApi)
