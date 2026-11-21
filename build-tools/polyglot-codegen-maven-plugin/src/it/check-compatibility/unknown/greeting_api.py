class GreetingApi:
    @staticmethod
    def hello(name: str) -> str:
        return name

polyglot.export_value('GreetingApi', GreetingApi)
