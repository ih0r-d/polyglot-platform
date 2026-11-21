class GreetingApi:
    def hello(self, name: str) -> str:
        return name

polyglot.export_value('GreetingApi', GreetingApi)
