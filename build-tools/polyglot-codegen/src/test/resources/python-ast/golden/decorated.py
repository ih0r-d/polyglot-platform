def identity(function):
    return function
class Decorated:
    @identity
    def hello(self, value: str) -> str:
        return value
polyglot.export_value('Decorated', Decorated)
