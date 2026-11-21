class GreetingApi:
    def hello(self, name: str, prefix: str = 'Hello') -> str:
        return prefix + name

polyglot.export_value('GreetingApi', GreetingApi)
