class GreetingApi:
    def renamed(self, name: str) -> str:
        return name

polyglot.export_value('GreetingApi', GreetingApi)
